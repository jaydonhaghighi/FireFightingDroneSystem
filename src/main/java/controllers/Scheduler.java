package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;
import models.MetricsTracker;

import javax.swing.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static models.FireEvent.createFireEventFromString;

public class Scheduler {
    private final DatagramSocket sendSocket;
    private final DatagramSocket receiveSocket;
    private final int sendPort = 6000;
    private final int receivePort = 6001;
    private final InetAddress fireIncidentIP;
    private final PriorityBlockingQueue<FireEvent> eventQueue;
    private final DroneManager droneManager;
    private final ConcurrentMap<String, Integer> dronePorts;
    private final ConcurrentMap<Integer, Integer> fireEventAssignedDrones;
    private final ConcurrentMap<Integer, Integer> fireEventRequiredDrones;
    private final Set<Integer> fullyAssignedZones = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicBoolean isRunning;
    private final ReadWriteLock visualizationLock;
    private DroneVisualization visualization;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService workerExecutor;
    private final AtomicInteger messageReceiveCount = new AtomicInteger(0);
    private final AtomicInteger fireSentCount = new AtomicInteger(0);
    private final AtomicInteger droneAssignmentCount = new AtomicInteger(0);
    private final AtomicInteger fireExtinguishedCount = new AtomicInteger(0);
    
    protected void log(String message) {}
    protected void logVerbose(String message) {}
    protected void logError(String message, Throwable e) {}
    protected void logSystemState() {}
    protected void logDroneStatuses() {}
    protected void logActiveFiresDetail() {}

    static class FireEventComparator implements Comparator<FireEvent> {
        @Override
        public int compare(FireEvent e1, FireEvent e2) {
            int severityCompare = DroneManager.getSeverityWeight(e2.getSeverity()) - DroneManager.getSeverityWeight(e1.getSeverity());
            if (severityCompare != 0) return severityCompare;
            return e1.getTime().compareTo(e2.getTime());
        }
    }

    public Scheduler(InetAddress ip) throws SocketException {
        this.fireIncidentIP = ip;
        this.eventQueue = new PriorityBlockingQueue<>(20, new FireEventComparator());
        this.dronePorts = new ConcurrentHashMap<>();
        this.fireEventAssignedDrones = new ConcurrentHashMap<>();
        this.fireEventRequiredDrones = new ConcurrentHashMap<>();
        this.isRunning = new AtomicBoolean(true);
        this.visualizationLock = new ReentrantReadWriteLock();
        
        Location baseLocation = new Location(0, 0);
        this.droneManager = new DroneManager(baseLocation);
        
        try {
            this.sendSocket = new DatagramSocket(sendPort);
            this.receiveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            logError("Failed to create sockets", e);
            throw e;
        }
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.workerExecutor = Executors.newCachedThreadPool();
        
        registerDronePorts();
        initializeVisualization();
        
        scheduledExecutor.scheduleAtFixedRate(this::logSystemState, 10, 10, TimeUnit.SECONDS);
    }
    
    private void registerDronePorts() {
        for (int i = 1; i <= 10; i++) {
            String droneId = "drone" + i;
            int port = 7001 + (i * 100);
            dronePorts.put(droneId, port);
        }
    }
    
    private void initializeVisualization() {
        SwingUtilities.invokeLater(() -> {
            try {
                visualizationLock.writeLock().lock();
                visualization = new DroneVisualization(droneManager);
            } catch (Exception e) {
                logError("Failed to initialize visualization", e);
            } finally {
                visualizationLock.writeLock().unlock();
            }
        });
    }
    public FireEvent receive() {
        try {
            byte[] data = new byte[100];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            
            receiveSocket.receive(receivePacket);
            messageReceiveCount.incrementAndGet();
            
            String message = new String(data, 0, receivePacket.getLength());
            InetAddress sender = receivePacket.getAddress();
            int port = receivePacket.getPort();
            
            if (isDroneStatusUpdate(message)) {
                processDroneStatusUpdate(message);
                return null;
            }

            if (message.startsWith("ZONE_INFO_REQUEST:")) {
                handleZoneInfoRequest(message, port, sender);
                return null;
            }

            return createFireEventFromString(message);
        } catch (IOException e) {
            if (isRunning.get()) {
                logError("Error receiving packet", e);
            }
            return null;
        } catch (Exception e) {
            logError("Error parsing message", e);
            return null;
        }
    }
    
    private void handleZoneInfoRequest(String message, int port, InetAddress sender) {
        String[] parts = message.split(":");
        if (parts.length >= 2) {
            try {
                int zoneId = Integer.parseInt(parts[1]);
                Zone zone = droneManager.getZone(zoneId);
                if (zone != null) {
                    Location center = zone.getLocation();
                    String response = "ZONE_INFO:" + zoneId + ":" + center.getX() + ":" + center.getY();
                    send(response, port, sender);
                }
            } catch (NumberFormatException e) {
                log("Invalid zone ID in request: " + message);
            }
        }
    }

    private boolean isDroneStatusUpdate(String message) {
        try {
            return DroneManager.isDroneStatusMessage(message);
        } catch (Exception e) {
            logError("Error in isDroneStatusUpdate", e);
            return false;
        }
    }

    private static class TaskInfo {
        int zoneId = -1;
        String severity = null;
        boolean isFireOut = false;
        double currentCapacity = -1;
        int abandonedZoneId = -1;
        int newTaskZoneId = -1;
    }
    
    private void processDroneStatusUpdate(String message) {
        try {
            String[] parts = message.split(" ");
            String droneId = parts[0];
            String state = parts[1];
            
            int x = Integer.parseInt(parts[parts.length - 2]);
            int y = Integer.parseInt(parts[parts.length - 1]);
            Location location = new Location(x, y);
            
            TaskInfo taskInfo = extractTaskInfo(parts);
            
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                status = droneManager.registerDrone(droneId);
            }
            
            boolean fireWasExtinguished = false;
            if (taskInfo.isFireOut && taskInfo.zoneId > 0) {
                droneManager.updateZoneFireStatus(taskInfo.zoneId, false, "NONE");
                
                fireEventAssignedDrones.remove(taskInfo.zoneId);
                fireEventRequiredDrones.remove(taskInfo.zoneId);
                
                fireWasExtinguished = true;
                fireExtinguishedCount.incrementAndGet();
                
                MetricsTracker.getInstance().recordFireExtinguished(taskInfo.zoneId);
            }
            
            if (taskInfo.abandonedZoneId > 0) {
                int currentAssigned = fireEventAssignedDrones.getOrDefault(taskInfo.abandonedZoneId, 0);
                if (currentAssigned > 0) {
                    fireEventAssignedDrones.put(taskInfo.abandonedZoneId, currentAssigned - 1);
                    clearZoneFullyAssignedMark(taskInfo.abandonedZoneId);
                }
            }
            
            boolean stateChanged = !status.getState().equalsIgnoreCase(state);
            boolean locationChanged = !status.getCurrentLocation().equals(location);
            
            updateDroneStatus(status, droneId, state, location, taskInfo);
            
            if (fireWasExtinguished || stateChanged || locationChanged) {
                updateVisualization();
            }
            
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state))) {
                final DroneStatus finalStatus = status;
                
                workerExecutor.submit(() -> {
                    try {
                        checkForPendingFireEvents(finalStatus);
                    } catch (Exception e) {
                        logError("Error in worker thread for " + droneId, e);
                    }
                });
            }
        } catch (Exception e) {
            logError("Error processing drone status", e);
        }
    }
    
    private void updateVisualization() {
        try {
            visualizationLock.readLock().lock();
            try {
                if (visualization != null) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            visualization.updateVisualization();
                        } catch (Exception e) {
                            logError("Error updating visualization on EDT", e);
                        }
                    });
                }
            } finally {
                visualizationLock.readLock().unlock();
            }
        } catch (Exception e) {
            logError("Error in updateVisualization", e);
        }
    }
    
    private TaskInfo extractTaskInfo(String[] parts) {
        TaskInfo info = new TaskInfo();
        
        for (String part : parts) {
            if (part.startsWith("TASK:")) {
                String[] taskParts = part.split(":");
                if (taskParts.length >= 3) {
                    info.zoneId = Integer.parseInt(taskParts[1]);
                    info.severity = taskParts[2];
                }
            } else if (part.startsWith("FIRE_OUT:")) {
                String[] fireOutParts = part.split(":");
                if (fireOutParts.length >= 2) {
                    info.zoneId = Integer.parseInt(fireOutParts[1]);
                    info.isFireOut = true;
                }
            } else if (part.startsWith("CAPACITY:")) {
                String[] capacityParts = part.split(":");
                if (capacityParts.length >= 2) {
                    try {
                        info.currentCapacity = Double.parseDouble(capacityParts[1]);
                    } catch (NumberFormatException e) {
                        // Ignore parse errors
                    }
                }
            } else if (part.startsWith("ABANDONED:")) {
                String[] abandonedParts = part.split(":");
                if (abandonedParts.length >= 2) {
                    info.abandonedZoneId = Integer.parseInt(abandonedParts[1]);
                }
            } else if (part.startsWith("NEW_TASK:")) {
                String[] newTaskParts = part.split(":");
                if (newTaskParts.length >= 2) {
                    info.newTaskZoneId = Integer.parseInt(newTaskParts[1]);
                }
            }
        }
        
        return info;
    }
    
    private void updateDroneStatus(DroneStatus status, String droneId, String state, 
                             Location location, TaskInfo taskInfo) {
        try {
            FireEvent currentTask = status.getCurrentTask();
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state)) && 
                currentTask != null) {
                int zoneId = currentTask.getZoneID();
                
                Integer oldCount = fireEventAssignedDrones.get(zoneId);
                if (oldCount != null) {
                    Integer newCount = Math.max(0, oldCount - 1);
                    fireEventAssignedDrones.put(zoneId, newCount);
                    
                    int requiredDrones = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                    if (newCount < requiredDrones) {
                        clearZoneFullyAssignedMark(zoneId);
                    }
                }
                
                currentTask = null;
            }
            
            droneManager.updateDroneStatus(droneId, state, location, currentTask);
            
            if (taskInfo.currentCapacity >= 0) {
                status.getSpecifications().setCurrentCapacity(taskInfo.currentCapacity);
            }
        } catch (Exception e) {
            logError("Error updating drone status for " + droneId, e);
        }
    }

    private void checkForPendingFireEvents(DroneStatus drone) {
        try {
            String droneId = drone.getDroneId();
            cleanupExtinguishedFires();
            
            Integer highestPriorityZoneId = null;
            String highestPrioritySeverity = null;
            
            Map<Integer, Integer> requiredSnapshot = new HashMap<>(fireEventRequiredDrones);
            
            for (Map.Entry<Integer, Integer> entry : requiredSnapshot.entrySet()) {
                int zoneId = entry.getKey();
                int required = entry.getValue();
                int assigned = fireEventAssignedDrones.getOrDefault(zoneId, 0);
                
                if (assigned < required) {
                    Zone zone = droneManager.getZone(zoneId);
                    if (zone != null && zone.hasFire()) {
                        if (isZoneFullyAssigned(zoneId)) {
                            continue;
                        }
                        
                        int actualRequired = getDronesNeededForSeverity(zone.getSeverity());
                        int actualAssignedCount = countDronesForZone(zoneId);
                        
                        if (actualAssignedCount >= actualRequired) {
                            markZoneAsFullyAssigned(zoneId);
                            continue;
                        }
                        
                        String severity = zone.getSeverity();
                        int currentWeight = highestPrioritySeverity != null ? 
                            DroneManager.getSeverityWeight(highestPrioritySeverity) : -1;
                        int newWeight = DroneManager.getSeverityWeight(severity);
                        
                        if (highestPriorityZoneId == null || newWeight > currentWeight) {
                            highestPriorityZoneId = zoneId;
                            highestPrioritySeverity = severity;
                        }
                    }
                }
            }
            
            if (highestPriorityZoneId != null) {
                final int zoneId = highestPriorityZoneId;
                final String severity = highestPrioritySeverity;
                
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                Integer oldCount = fireEventAssignedDrones.get(zoneId);
                Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                fireEventAssignedDrones.put(zoneId, newCount);
                
                droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                
                boolean sent = sendToDrone(event, droneId);
                
                if (sent) {
                    droneAssignmentCount.incrementAndGet();
                } else {
                    fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                        count != null && count > 0 ? count - 1 : null);
                }
                
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error checking for pending fire events", e);
        }
    }
    
    private int countDronesForZone(int zoneId) {
        return droneManager.countDronesForZone(zoneId);
    }
    
    private void cleanupExtinguishedFires() {
        try {
            Set<Integer> zoneIds = new HashSet<>(fireEventRequiredDrones.keySet());
            
            if (zoneIds.isEmpty()) {
                return;
            }
            
            for (Integer zoneId : zoneIds) {
                Zone zone = droneManager.getZone(zoneId);
                if (zone == null || !zone.hasFire()) {
                    fireEventRequiredDrones.remove(zoneId);
                    fireEventAssignedDrones.remove(zoneId);
                    clearZoneFullyAssignedMark(zoneId);
                    removeFromEventQueue(zoneId);
                }
            }
        } catch (Exception e) {
            logError("Error cleaning up extinguished fires", e);
        }
    }
    
    private int removeFromEventQueue(int zoneId) {
        try {
            List<FireEvent> eventsToKeep = new ArrayList<>();
            int removedCount = 0;
            
            FireEvent event;
            while ((event = eventQueue.poll()) != null) {
                if (event.getZoneID() != zoneId) {
                    eventsToKeep.add(event);
                } else {
                    removedCount++;
                }
            }
            
            if (!eventsToKeep.isEmpty()) {
                eventQueue.addAll(eventsToKeep);
            }
            
            return removedCount;
        } catch (Exception e) {
            logError("Error removing events from queue", e);
            return 0;
        }
    }
    
    private FireEvent createFireEventForZone(int zoneId, String severity) {
        return droneManager.createFireEventForZone(zoneId, severity);
    }

    public boolean send(FireEvent fire, int port) {
        try {
            String message = fire.toString();
            byte[] msg = message.getBytes();
            
            DatagramPacket sendPacket = new DatagramPacket(msg, msg.length, 
                                                          InetAddress.getLocalHost(), port);
            sendSocket.send(sendPacket);
            fireSentCount.incrementAndGet();
            return true;
        } catch (Exception e) {
            logError("Error sending fire event to port " + port, e);
            return false;
        }
    }
    
    public boolean send(String message, int port, InetAddress ip) {
        try {
            byte[] msg = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(msg, msg.length, ip, port);
            sendSocket.send(sendPacket);
            return true;
        } catch (Exception e) {
            logError("Error sending message to " + ip + ":" + port, e);
            return false;
        }
    }

    public boolean sendToDrone(FireEvent fire, String droneId) {
        try {
            fire.assignDrone(droneId);
            
            Integer port = dronePorts.get(droneId);
            if (port == null) {
                return false;
            }
            
            boolean result = send(fire, port);
            
            if (result) {
                MetricsTracker.getInstance().recordDroneResponse(fire.getZoneID());
            }
            
            return result;
        } catch (Exception e) {
            logError("Error in sendToDrone for " + droneId, e);
            return false;
        }
    }

    private int getDronesNeededForSeverity(String severity) {
        try {
            return droneManager.getDronesNeededForSeverity(severity);
        } catch (Exception e) {
            logError("Error calculating drones needed for severity", e);
            return 1;
        }
    }
    
    private void markZoneAsFullyAssigned(int zoneId) {
        fullyAssignedZones.add(zoneId);
    }
    
    private boolean isZoneFullyAssigned(int zoneId) {
        return fullyAssignedZones.contains(zoneId);
    }
    
    private void clearZoneFullyAssignedMark(int zoneId) {
        fullyAssignedZones.remove(zoneId);
    }

    private void processNextFireEvent() {
        try {
            FireEvent event = eventQueue.poll();
            if (event == null) {
                checkActiveFiresForDroneAssignment();
                return;
            }
            
            int zoneId = event.getZoneID();
            String severity = event.getSeverity();
            
            Zone zone = droneManager.getZone(zoneId);
            
            if (zone == null) {
                Location zoneLocation = new Location(
                    ((zoneId-1) % 3) * 350 + 175,
                    ((zoneId-1) / 3) * 300 + 150);
                
                zone = droneManager.createZone(zoneId, zoneLocation);
            }
            
            boolean updateZoneStatus = true;
            if (zone.hasFire()) {
                int currentSeverityWeight = DroneManager.getSeverityWeight(zone.getSeverity());
                int newSeverityWeight = DroneManager.getSeverityWeight(severity);
                
                if (currentSeverityWeight >= newSeverityWeight) {
                    updateZoneStatus = false;
                }
            }
            
            if (updateZoneStatus) {
                droneManager.updateZoneFireStatus(zoneId, true, severity);
                
                int dronesNeeded = getDronesNeededForSeverity(severity);
                int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                
                if (currentRequired < dronesNeeded) {
                    fireEventRequiredDrones.put(zoneId, dronesNeeded);
                }
                
                fireEventAssignedDrones.putIfAbsent(zoneId, 0);
            }
            
            updateVisualization();
            
            if (updateZoneStatus) {
                dispatchDronesToFire(event, fireEventRequiredDrones.get(zoneId));
            }
            
            checkActiveFiresForDroneAssignment();
        } catch (Exception e) {
            logError("Error processing fire event", e);
        }
    }
    
    private void checkActiveFiresForDroneAssignment() {
        try {
            Map<Integer, Zone> zones = droneManager.getAllZones();
            List<Map.Entry<Integer, Zone>> activeFireZones = zones.entrySet().stream()
                .filter(entry -> entry.getValue().hasFire())
                .sorted((e1, e2) -> {
                    int severityCompare = DroneManager.getSeverityWeight(e2.getValue().getSeverity()) - 
                                         DroneManager.getSeverityWeight(e1.getValue().getSeverity());
                    if (severityCompare != 0) return severityCompare;
                    
                    int zone1 = e1.getKey();
                    int zone2 = e2.getKey();
                    int required1 = fireEventRequiredDrones.getOrDefault(zone1, 0);
                    int required2 = fireEventRequiredDrones.getOrDefault(zone2, 0);
                    int assigned1 = fireEventAssignedDrones.getOrDefault(zone1, 0);
                    int assigned2 = fireEventAssignedDrones.getOrDefault(zone2, 0);
                    
                    double ratio1 = required1 == 0 ? 1.0 : (double)assigned1 / required1;
                    double ratio2 = required2 == 0 ? 1.0 : (double)assigned2 / required2;
                    
                    return Double.compare(ratio1, ratio2);
                })
                .collect(Collectors.toList());
            
            if (activeFireZones.isEmpty()) {
                return;
            }
            
            Collection<DroneStatus> allDrones = droneManager.getAllDrones();
            List<DroneStatus> availableDrones = allDrones.stream()
                .filter(DroneStatus::isAvailable)
                .collect(Collectors.toList());
            
            for (Map.Entry<Integer, Zone> entry : activeFireZones) {
                int zoneId = entry.getKey();
                Zone zone = entry.getValue();
                String severity = zone.getSeverity();
                
                int actualDronesNeeded = getDronesNeededForSeverity(severity);
                int requiredDrones = Math.min(
                    fireEventRequiredDrones.getOrDefault(zoneId, 0),
                    actualDronesNeeded
                );
                
                if (requiredDrones != fireEventRequiredDrones.getOrDefault(zoneId, 0)) {
                    fireEventRequiredDrones.put(zoneId, requiredDrones);
                }
                
                int assignedDrones = countDronesForZone(zoneId);
                
                if (assignedDrones != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                    fireEventAssignedDrones.put(zoneId, assignedDrones);
                }
                
                int neededDrones = requiredDrones - assignedDrones;
                
                if (isZoneFullyAssigned(zoneId) || assignedDrones >= requiredDrones) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                if (neededDrones <= 0) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                int dispatched = 0;
                Set<String> assignedDroneIds = new HashSet<>();
                
                if (!availableDrones.isEmpty()) {
                    for (int i = 0; i < Math.min(neededDrones, availableDrones.size()); i++) {
                        DroneStatus drone = availableDrones.get(i);
                        String droneId = drone.getDroneId();
                        
                        Set<String> currentAssigned = new HashSet<>(assignedDroneIds);
                        DroneStatus redirectableStatus = findAvailableDrone(event, currentAssigned);
                        
                        FireEvent actualEvent = event;
                        int targetZoneId = zoneId;
                        
                        if (redirectableStatus != null && redirectableStatus.getCurrentTask() != null) {
                            FireEvent redirectedTask = redirectableStatus.getCurrentTask();
                            if (redirectedTask.getZoneID() != zoneId) {
                                actualEvent = redirectedTask;
                                targetZoneId = redirectedTask.getZoneID();
                            }
                        }
                        
                        droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), actualEvent);
                        
                        fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                            (count == null) ? 1 : count + 1);
                            
                        boolean sent = sendToDrone(actualEvent, droneId);
                        if (sent) {
                            dispatched++;
                            assignedDroneIds.add(droneId);
                            droneAssignmentCount.incrementAndGet();
                        } else {
                            droneManager.updateDroneStatus(droneId, "Idle", drone.getCurrentLocation(), null);
                            fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                        }
                    }
                    
                    availableDrones.removeIf(drone -> assignedDroneIds.contains(drone.getDroneId()));
                    neededDrones -= dispatched;
                }
                
                if (neededDrones > 0) {
                    List<DroneStatus> enRouteDrones = allDrones.stream()
                        .filter(drone -> 
                            (drone.getState().equalsIgnoreCase("ENROUTE") || 
                             drone.getState().equalsIgnoreCase("EnRoute")) && 
                            drone.getCurrentTask() != null)
                        .filter(drone -> {
                            FireEvent currentTask = drone.getCurrentTask();
                            return DroneManager.getSeverityWeight(severity) > 
                                   DroneManager.getSeverityWeight(currentTask.getSeverity()) &&
                                   currentTask.getZoneID() != zoneId &&
                                   !assignedDroneIds.contains(drone.getDroneId());
                        })
                        .sorted(Comparator.comparingInt(drone -> 
                            drone.distanceTo(droneManager.getLocationForZone(zoneId))))
                        .collect(Collectors.toList());
                    
                    for (int i = 0; i < Math.min(neededDrones, enRouteDrones.size()); i++) {
                        DroneStatus drone = enRouteDrones.get(i);
                        String droneId = drone.getDroneId();
                        
                        FireEvent currentTask = drone.getCurrentTask();
                        int oldZoneId = currentTask.getZoneID();
                        
                        fireEventAssignedDrones.compute(oldZoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                            
                        clearZoneFullyAssignedMark(oldZoneId);
                        
                        droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                        
                        fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                            (count == null) ? 1 : count + 1);
                            
                        boolean sent = sendToDrone(event, droneId);
                        if (sent) {
                            dispatched++;
                            assignedDroneIds.add(droneId);
                            droneAssignmentCount.incrementAndGet();
                        } else {
                            droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), currentTask);
                            fireEventAssignedDrones.compute(oldZoneId, (id, count) -> 
                                (count == null) ? 1 : count + 1);
                            fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                                
                            int currentOldZoneAssigned = fireEventAssignedDrones.getOrDefault(oldZoneId, 0);
                            int requiredOldZone = fireEventRequiredDrones.getOrDefault(oldZoneId, 0);
                            if (currentOldZoneAssigned >= requiredOldZone) {
                                markZoneAsFullyAssigned(oldZoneId);
                            }
                        }
                    }
                }
                
                if (dispatched > 0) {
                    updateVisualization();
                }
            }
        } catch (Exception e) {
            logError("Error checking active fires for drone assignment", e);
        }
    }
    
    private void dispatchDronesToFire(FireEvent event, int requestedDrones) {
        try {
            int zoneId = event.getZoneID();
            Set<String> assignedDroneIds = new HashSet<>();
            
            int currentlyAssigned = countDronesForZone(zoneId);
            for (DroneStatus currentDrone : droneManager.getAllDrones()) {
                FireEvent currentTask = currentDrone.getCurrentTask();
                if (currentTask != null && currentTask.getZoneID() == zoneId && 
                    !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                    !currentDrone.getState().equalsIgnoreCase("Idle")) {
                    assignedDroneIds.add(currentDrone.getDroneId());
                }
            }
            
            if (currentlyAssigned != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                fireEventAssignedDrones.put(zoneId, currentlyAssigned);
            }
            
            if (isZoneFullyAssigned(zoneId)) {
                return;
            }
            
            int actualDronesNeeded = requestedDrones;
            
            Zone zone = droneManager.getZone(zoneId);
            if (zone != null && zone.hasFire()) {
                actualDronesNeeded = Math.min(requestedDrones, 
                                            getDronesNeededForSeverity(zone.getSeverity()));
            }
            
            int remainingNeeded = actualDronesNeeded - currentlyAssigned;
            
            if (currentlyAssigned >= actualDronesNeeded) {
                markZoneAsFullyAssigned(zoneId);
                return;
            }
            
            int successfulDispatches = 0;
            for (int i = 0; i < remainingNeeded; i++) {
                DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
                
                if (drone != null) {
                    String droneId = drone.getDroneId();
                    assignedDroneIds.add(droneId);
                    
                    FireEvent droneTask = drone.getCurrentTask();
                    int targetZoneId = droneTask != null ? droneTask.getZoneID() : zoneId;
                    
                    Integer oldCount = fireEventAssignedDrones.getOrDefault(targetZoneId, 0);
                    Integer newCount = oldCount + 1;
                    fireEventAssignedDrones.put(targetZoneId, newCount);
                    
                    FireEvent actualEvent = (droneTask != null) ? droneTask : event;
                    
                    droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), actualEvent);
                    
                    boolean sent = sendToDrone(actualEvent, droneId);
                    if (sent) {
                        successfulDispatches++;
                        droneAssignmentCount.incrementAndGet();
                    } else {
                        fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                    }
                } else {
                    break;
                }
            }
            
            if (successfulDispatches > 0) {
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error dispatching drones to fire", e);
        }
    }
    
    private DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds) {
        try {
            return droneManager.findAvailableDrone(event, assignedDroneIds, true);
        } catch (Exception e) {
            logError("Error finding available drone", e);
            return null;
        }
    }

    private void receiveMessages() {
        while (isRunning.get()) {
            FireEvent event = receive();
            
            if (event != null) {
                final FireEvent finalEvent = event;
                
                workerExecutor.submit(() -> {
                    try {
                        int zoneId = finalEvent.getZoneID();
                        String severity = finalEvent.getSeverity();
                        
                        Zone zone = droneManager.getZone(zoneId);
                        if (zone == null) {
                            Location zoneLocation = new Location(
                                ((zoneId-1) % 3) * 350 + 175,
                                ((zoneId-1) / 3) * 300 + 150);
                            zone = droneManager.createZone(zoneId, zoneLocation);
                        }
                        
                        boolean updateZoneStatus = true;
                        if (zone.hasFire()) {
                            int currentSeverityWeight = DroneManager.getSeverityWeight(zone.getSeverity());
                            int newSeverityWeight = DroneManager.getSeverityWeight(severity);
                            
                            if (currentSeverityWeight >= newSeverityWeight) {
                                updateZoneStatus = false;
                            }
                        }
                        
                        if (updateZoneStatus) {
                            droneManager.updateZoneFireStatus(zoneId, true, severity);
                        }
                        
                        eventQueue.add(finalEvent);
                        
                        MetricsTracker.getInstance().recordFireDetected(zoneId);
                        
                        int dronesNeeded = getDronesNeededForSeverity(severity);
                        int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                        
                        if (currentRequired < dronesNeeded) {
                            fireEventRequiredDrones.put(zoneId, dronesNeeded);
                        }
                        
                        fireEventAssignedDrones.putIfAbsent(zoneId, 0);
                        
                        send(finalEvent, 5001);
                        
                        updateVisualization();
                    } catch (Exception e) {
                        logError("Error processing incoming fire event", e);
                    }
                });
            }
            
            Thread.yield();
        }
    }

    private void processEvents() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExtinguishedFires();
            } catch (Exception e) {
                logError("Error in periodic cleanup", e);
            }
        }, 5, 15, TimeUnit.SECONDS);
        
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                if (eventQueue.isEmpty()) {
                    checkActiveFiresForDroneAssignment();
                }
            } catch (Exception e) {
                logError("Error in periodic drone assignment", e);
            }
        }, 3, 3, TimeUnit.SECONDS);
        
        while (isRunning.get()) {
            if (!eventQueue.isEmpty()) {
                processNextFireEvent();
            }

            Thread.yield();
        }
    }
    
    public void shutdown() {
        try {
            isRunning.set(false);
            
            scheduledExecutor.shutdownNow();
            workerExecutor.shutdownNow();
            
            try {
                scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS);
                workerExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.close();
            }
            
            if (receiveSocket != null && !receiveSocket.isClosed()) {
                receiveSocket.close();
            }
        } catch (Exception e) {
            logError("Error during shutdown", e);
        }
    }

    public static void main(String[] args) {
        Scheduler scheduler = null;
        
        try {
            InetAddress ip = InetAddress.getLocalHost();
            scheduler = new Scheduler(ip);
            
            Thread receiveThread = new Thread(scheduler::receiveMessages);
            Thread processThread = new Thread(scheduler::processEvents);
            
            receiveThread.setName("Scheduler-Receive");
            processThread.setName("Scheduler-Process");
            
            receiveThread.start();
            processThread.start();
            
            final Scheduler finalScheduler = scheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(finalScheduler::shutdown));
            
            receiveThread.join();
            processThread.join();
            
        } catch (Exception e) {
            // Handle silently
        } finally {
            if (scheduler != null) {
                scheduler.shutdown();
            }
        }
    }
}