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

/**
 * The Scheduler class is responsible for managing the coordination between fire events and drones.
 * It receives fire events, assigns drones to handle them based on priority and availability,
 * and maintains the overall state of the fire-fighting system.
 * 
 * The scheduler operates using a priority queue for fire events, with higher severity fires
 * being processed first. It communicates with drones via UDP sockets and maintains a
 * visualization of the system state.
 */
public class Scheduler {
    // Network communication sockets
    private final DatagramSocket sendSocket;
    private final DatagramSocket receiveSocket;
    private int sendPort = 6000;
    private int receivePort = 6001;
    private final InetAddress fireIncidentIP;
    
    // Event and drone management
    private final PriorityBlockingQueue<FireEvent> eventQueue;
    private final DroneManager droneManager;
    private final ConcurrentMap<String, Integer> dronePorts;
    private final ConcurrentMap<Integer, Integer> fireEventAssignedDrones;
    private final ConcurrentMap<Integer, Integer> fireEventRequiredDrones;
    private final Set<Integer> fullyAssignedZones = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // System state management
    private final AtomicBoolean isRunning;
    private final ReadWriteLock visualizationLock;
    private DroneVisualization visualization;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService workerExecutor;
    
    // Metrics tracking
    private final AtomicInteger messageReceiveCount = new AtomicInteger(0);
    private final AtomicInteger fireSentCount = new AtomicInteger(0);
    private final AtomicInteger droneAssignmentCount = new AtomicInteger(0);
    private final AtomicInteger fireExtinguishedCount = new AtomicInteger(0);
    
    /**
     * Logs a standard message.
     * @param message The message to log
     */
    protected void log(String message) {}
    
    /**
     * Logs a verbose message with additional details.
     * @param message The verbose message to log
     */
    protected void logVerbose(String message) {}
    
    /**
     * Logs an error message with the associated exception.
     * @param message The error message
     * @param e The exception that caused the error
     */
    protected void logError(String message, Throwable e) {}
    
    /**
     * Logs the current system state.
     */
    protected void logSystemState() {}
    
    /**
     * Logs the status of all drones.
     */
    protected void logDroneStatuses() {}
    
    /**
     * Logs detailed information about active fires.
     */
    protected void logActiveFiresDetail() {}

    /**
     * Comparator for FireEvent objects that prioritizes events based on severity and time.
     * Higher severity events are processed first, and for events of equal severity,
     * earlier events are processed first.
     */
    static class FireEventComparator implements Comparator<FireEvent> {
        @Override
        public int compare(FireEvent e1, FireEvent e2) {
            // Compare by severity first (higher severity = higher priority)
            int severityCompare = DroneManager.getSeverityWeight(e2.getSeverity()) - DroneManager.getSeverityWeight(e1.getSeverity());
            if (severityCompare != 0) return severityCompare;
            // For equal severity, compare by time (earlier = higher priority)
            return e1.getTime().compareTo(e2.getTime());
        }
    }

    /**
     * Constructs a new Scheduler with the specified IP address.
     * Initializes all necessary components including sockets, queues, and thread pools.
     * 
     * @param ip The IP address for the scheduler
     * @throws SocketException if there is an error creating the sockets
     */
    public Scheduler(InetAddress ip) throws SocketException {
        this.fireIncidentIP = ip;
        this.eventQueue = new PriorityBlockingQueue<>(20, new FireEventComparator());
        this.dronePorts = new ConcurrentHashMap<>();
        this.fireEventAssignedDrones = new ConcurrentHashMap<>();
        this.fireEventRequiredDrones = new ConcurrentHashMap<>();
        this.isRunning = new AtomicBoolean(true);
        this.visualizationLock = new ReentrantReadWriteLock();
        
        // Initialize the drone manager with a base location
        Location baseLocation = new Location(0, 0);
        this.droneManager = new DroneManager(baseLocation);
        
        try {
            // Create UDP sockets for communication
            this.sendSocket = new DatagramSocket(sendPort);
            this.receiveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            logError("Failed to create sockets", e);
            throw e;
        }
        
        // Initialize thread pools for scheduled and worker tasks
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.workerExecutor = Executors.newCachedThreadPool();
        
        // Set up initial system state
        registerDronePorts();
        initializeVisualization();
        
        // Schedule periodic system state logging
        scheduledExecutor.scheduleAtFixedRate(this::logSystemState, 10, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Registers ports for all drones in the system.
     * Assigns a unique port to each drone for communication.
     */
    private void registerDronePorts() {
        for (int i = 1; i <= 10; i++) {
            String droneId = "drone" + i;
            int port = 7001 + (i * 100);
            dronePorts.put(droneId, port);
        }
    }
    
    /**
     * Initializes the visualization component for the system.
     * This is done on the Event Dispatch Thread to ensure thread safety.
     */
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
    
    /**
     * Receives and processes incoming messages from drones and fire detection systems.
     * 
     * @return A FireEvent if a valid fire event was received, null otherwise
     */
    public FireEvent receive() {
        try {
            // Prepare to receive a UDP packet
            byte[] data = new byte[100];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            
            // Wait for a packet to arrive
            receiveSocket.receive(receivePacket);
            messageReceiveCount.incrementAndGet();
            
            // Extract message content and sender information
            String message = new String(data, 0, receivePacket.getLength());
            InetAddress sender = receivePacket.getAddress();
            int port = receivePacket.getPort();
            
            // Process different types of messages
            if (isDroneStatusUpdate(message)) {
                processDroneStatusUpdate(message);
                return null;
            }

            if (message.startsWith("ZONE_INFO_REQUEST:")) {
                handleZoneInfoRequest(message, port, sender);
                return null;
            }

            // If not a special message, try to parse as a fire event
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
    
    /**
     * Handles requests for zone information from drones.
     * 
     * @param message The request message
     * @param port The port to send the response to
     * @param sender The sender's IP address
     */
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

    /**
     * Checks if a message is a drone status update.
     * 
     * @param message The message to check
     * @return true if the message is a drone status update, false otherwise
     */
    private boolean isDroneStatusUpdate(String message) {
        try {
            return DroneManager.isDroneStatusMessage(message);
        } catch (Exception e) {
            logError("Error in isDroneStatusUpdate", e);
            return false;
        }
    }

    /**
     * Internal class to hold task information extracted from drone status messages.
     */
    private static class TaskInfo {
        int zoneId = -1;
        String severity = null;
        boolean isFireOut = false;
        double currentCapacity = -1;
        int abandonedZoneId = -1;
        int newTaskZoneId = -1;
    }
    
    /**
     * Processes a drone status update message.
     * Updates the drone's status, location, and task information.
     * 
     * @param message The status update message
     */
    private void processDroneStatusUpdate(String message) {
        try {
            // Parse the message components
            String[] parts = message.split(" ");
            String droneId = parts[0];
            String state = parts[1];
            
            // Extract location information
            int x = Integer.parseInt(parts[parts.length - 2]);
            int y = Integer.parseInt(parts[parts.length - 1]);
            Location location = new Location(x, y);
            
            // Extract task information
            TaskInfo taskInfo = extractTaskInfo(parts);
            
            // Get or register the drone
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                status = droneManager.registerDrone(droneId);
            }
            
            // Handle fire extinguished notification
            boolean fireWasExtinguished = false;
            if (taskInfo.isFireOut && taskInfo.zoneId > 0) {
                droneManager.updateZoneFireStatus(taskInfo.zoneId, false, "NONE");
                
                fireEventAssignedDrones.remove(taskInfo.zoneId);
                fireEventRequiredDrones.remove(taskInfo.zoneId);
                
                fireWasExtinguished = true;
                fireExtinguishedCount.incrementAndGet();
                
                MetricsTracker.getInstance().recordFireExtinguished(taskInfo.zoneId);
            }
            
            // Handle abandoned zone notification
            if (taskInfo.abandonedZoneId > 0) {
                int currentAssigned = fireEventAssignedDrones.getOrDefault(taskInfo.abandonedZoneId, 0);
                if (currentAssigned > 0) {
                    fireEventAssignedDrones.put(taskInfo.abandonedZoneId, currentAssigned - 1);
                    clearZoneFullyAssignedMark(taskInfo.abandonedZoneId);
                }
            }
            
            // Check if status or location has changed
            boolean stateChanged = !status.getState().equalsIgnoreCase(state);
            boolean locationChanged = !status.getCurrentLocation().equals(location);
            
            // Update the drone's status
            updateDroneStatus(status, droneId, state, location, taskInfo);
            
            // Update visualization if necessary
            if (fireWasExtinguished || stateChanged || locationChanged) {
                updateVisualization();
            }
            
            // If drone is idle, check for pending fire events
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
    
    /**
     * Updates the visualization of the system state.
     * This is done on the Event Dispatch Thread to ensure thread safety.
     */
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
    
    /**
     * Extracts task information from a drone status message.
     * 
     * @param parts The parts of the message
     * @return A TaskInfo object containing the extracted information
     */
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
    
    /**
     * Updates the status of a drone with new information.
     * 
     * @param status The current drone status
     * @param droneId The ID of the drone
     * @param state The new state of the drone
     * @param location The new location of the drone
     * @param taskInfo Information about the drone's current task
     */
    private void updateDroneStatus(DroneStatus status, String droneId, String state, 
                             Location location, TaskInfo taskInfo) {
        try {
            // Handle transition to idle state
            FireEvent currentTask = status.getCurrentTask();
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state)) && 
                currentTask != null) {
                int zoneId = currentTask.getZoneID();
                
                // Update assigned drones count for the zone
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
            
            // Update the drone's status in the drone manager
            droneManager.updateDroneStatus(droneId, state, location, currentTask);
            
            // Update the drone's capacity if provided
            if (taskInfo.currentCapacity >= 0) {
                status.getSpecifications().setCurrentCapacity(taskInfo.currentCapacity);
            }
        } catch (Exception e) {
            logError("Error updating drone status for " + droneId, e);
        }
    }

    /**
     * Checks for pending fire events and assigns them to an idle drone.
     * This method is called when a drone becomes idle.
     * 
     * @param drone The idle drone to check for assignments
     */
    private void checkForPendingFireEvents(DroneStatus drone) {
        try {
            String droneId = drone.getDroneId();
            cleanupExtinguishedFires();
            
            // Find the highest priority zone that needs more drones
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
                        // Skip zones that are already fully assigned
                        if (isZoneFullyAssigned(zoneId)) {
                            continue;
                        }
                        
                        // Check if the zone actually needs more drones
                        int actualRequired = getDronesNeededForSeverity(zone.getSeverity());
                        int actualAssignedCount = countDronesForZone(zoneId);
                        
                        if (actualAssignedCount >= actualRequired) {
                            markZoneAsFullyAssigned(zoneId);
                            continue;
                        }
                        
                        // Compare severity to find highest priority
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
            
            // If a high priority zone was found, assign the drone to it
            if (highestPriorityZoneId != null) {
                final int zoneId = highestPriorityZoneId;
                final String severity = highestPrioritySeverity;
                
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                // Update assigned drones count
                Integer oldCount = fireEventAssignedDrones.get(zoneId);
                Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                fireEventAssignedDrones.put(zoneId, newCount);
                
                // Update drone status and send assignment
                droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                
                boolean sent = sendToDrone(event, droneId);
                
                if (sent) {
                    droneAssignmentCount.incrementAndGet();
                } else {
                    // If sending failed, revert the assignment
                    fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                        count != null && count > 0 ? count - 1 : null);
                }
                
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error checking for pending fire events", e);
        }
    }
    
    /**
     * Counts the number of drones currently assigned to a zone.
     * 
     * @param zoneId The ID of the zone to count drones for
     * @return The number of drones assigned to the zone
     */
    private int countDronesForZone(int zoneId) {
        return droneManager.countDronesForZone(zoneId);
    }
    
    /**
     * Cleans up information about extinguished fires.
     * Removes zones with no active fires from tracking maps.
     */
    private void cleanupExtinguishedFires() {
        try {
            Set<Integer> zoneIds = new HashSet<>(fireEventRequiredDrones.keySet());
            
            if (zoneIds.isEmpty()) {
                return;
            }
            
            for (Integer zoneId : zoneIds) {
                Zone zone = droneManager.getZone(zoneId);
                if (zone == null || !zone.hasFire()) {
                    // Remove zone from tracking maps
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
    
    /**
     * Removes all events for a specific zone from the event queue.
     * 
     * @param zoneId The ID of the zone whose events should be removed
     * @return The number of events removed
     */
    private int removeFromEventQueue(int zoneId) {
        try {
            List<FireEvent> eventsToKeep = new ArrayList<>();
            int removedCount = 0;
            
            // Process all events in the queue
            FireEvent event;
            while ((event = eventQueue.poll()) != null) {
                if (event.getZoneID() != zoneId) {
                    eventsToKeep.add(event);
                } else {
                    removedCount++;
                }
            }
            
            // Put back events that weren't removed
            if (!eventsToKeep.isEmpty()) {
                eventQueue.addAll(eventsToKeep);
            }
            
            return removedCount;
        } catch (Exception e) {
            logError("Error removing events from queue", e);
            return 0;
        }
    }
    
    /**
     * Creates a new fire event for a specific zone and severity.
     * 
     * @param zoneId The ID of the zone
     * @param severity The severity of the fire
     * @return A new FireEvent object
     */
    private FireEvent createFireEventForZone(int zoneId, String severity) {
        return droneManager.createFireEventForZone(zoneId, severity);
    }

    /**
     * Sends a fire event to a specific port.
     * 
     * @param fire The fire event to send
     * @param port The port to send to
     * @return true if the send was successful, false otherwise
     */
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
    
    /**
     * Sends a string message to a specific IP address and port.
     * 
     * @param message The message to send
     * @param port The port to send to
     * @param ip The IP address to send to
     * @return true if the send was successful, false otherwise
     */
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

    /**
     * Sends a fire event to a specific drone.
     * 
     * @param fire The fire event to send
     * @param droneId The ID of the drone to send to
     * @return true if the send was successful, false otherwise
     */
    public boolean sendToDrone(FireEvent fire, String droneId) {
        try {
            // Assign the drone to the fire event
            fire.assignDrone(droneId);
            
            // Get the port for the drone
            Integer port = dronePorts.get(droneId);
            if (port == null) {
                return false;
            }
            
            // Send the fire event to the drone
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

    /**
     * Gets the number of drones needed for a fire of a specific severity.
     * 
     * @param severity The severity of the fire
     * @return The number of drones needed
     */
    private int getDronesNeededForSeverity(String severity) {
        try {
            return droneManager.getDronesNeededForSeverity(severity);
        } catch (Exception e) {
            logError("Error calculating drones needed for severity", e);
            return 1;
        }
    }
    
    /**
     * Marks a zone as fully assigned with drones.
     * 
     * @param zoneId The ID of the zone to mark
     */
    private void markZoneAsFullyAssigned(int zoneId) {
        fullyAssignedZones.add(zoneId);
    }
    
    /**
     * Checks if a zone is fully assigned with drones.
     * 
     * @param zoneId The ID of the zone to check
     * @return true if the zone is fully assigned, false otherwise
     */
    private boolean isZoneFullyAssigned(int zoneId) {
        return fullyAssignedZones.contains(zoneId);
    }
    
    /**
     * Removes the fully assigned mark from a zone.
     * 
     * @param zoneId The ID of the zone to unmark
     */
    private void clearZoneFullyAssignedMark(int zoneId) {
        fullyAssignedZones.remove(zoneId);
    }

    /**
     * Processes the next fire event from the queue.
     * If the queue is empty, checks for active fires that need drone assignments.
     */
    private void processNextFireEvent() {
        try {
            // Get the next event from the queue
            FireEvent event = eventQueue.poll();
            if (event == null) {
                checkActiveFiresForDroneAssignment();
                return;
            }
            
            int zoneId = event.getZoneID();
            String severity = event.getSeverity();
            
            // Get or create the zone for this event
            Zone zone = droneManager.getZone(zoneId);
            
            if (zone == null) {
                // Calculate zone location based on zone ID
                Location zoneLocation = new Location(
                    ((zoneId-1) % 3) * 350 + 175,
                    ((zoneId-1) / 3) * 300 + 150);
                
                zone = droneManager.createZone(zoneId, zoneLocation);
            }
            
            // Determine if we need to update the zone's fire status
            boolean updateZoneStatus = true;
            if (zone.hasFire()) {
                int currentSeverityWeight = DroneManager.getSeverityWeight(zone.getSeverity());
                int newSeverityWeight = DroneManager.getSeverityWeight(severity);
                
                // Only update if the new severity is higher than the current one
                if (currentSeverityWeight >= newSeverityWeight) {
                    updateZoneStatus = false;
                }
            }
            
            // Update zone status if needed
            if (updateZoneStatus) {
                droneManager.updateZoneFireStatus(zoneId, true, severity);
                
                // Calculate and update required drones
                int dronesNeeded = getDronesNeededForSeverity(severity);
                int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                
                if (currentRequired < dronesNeeded) {
                    fireEventRequiredDrones.put(zoneId, dronesNeeded);
                }
                
                fireEventAssignedDrones.putIfAbsent(zoneId, 0);
            }
            
            // Update visualization
            updateVisualization();
            
            // Dispatch drones if zone status was updated
            if (updateZoneStatus) {
                dispatchDronesToFire(event, fireEventRequiredDrones.get(zoneId));
            }
            
            // Check for other active fires that might need assignments
            checkActiveFiresForDroneAssignment();
        } catch (Exception e) {
            logError("Error processing fire event", e);
        }
    }
    
    /**
     * Checks all active fires and assigns drones as needed.
     * Prioritizes fires based on severity and assignment ratio.
     */
    private void checkActiveFiresForDroneAssignment() {
        try {
            // Get all zones with active fires, sorted by priority
            Map<Integer, Zone> zones = droneManager.getAllZones();
            List<Map.Entry<Integer, Zone>> activeFireZones = zones.entrySet().stream()
                .filter(entry -> entry.getValue().hasFire())
                .sorted((e1, e2) -> {
                    // First sort by severity (higher severity = higher priority)
                    int severityCompare = DroneManager.getSeverityWeight(e2.getValue().getSeverity()) - 
                                         DroneManager.getSeverityWeight(e1.getValue().getSeverity());
                    if (severityCompare != 0) return severityCompare;
                    
                    // Then sort by assignment ratio (lower ratio = higher priority)
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
            
            // Get all available drones
            Collection<DroneStatus> allDrones = droneManager.getAllDrones();
            List<DroneStatus> availableDrones = allDrones.stream()
                .filter(DroneStatus::isAvailable)
                .collect(Collectors.toList());
            
            // Process each active fire zone
            for (Map.Entry<Integer, Zone> entry : activeFireZones) {
                int zoneId = entry.getKey();
                Zone zone = entry.getValue();
                String severity = zone.getSeverity();
                
                // Calculate required and assigned drones
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
                
                // Skip if zone is already fully assigned
                if (isZoneFullyAssigned(zoneId) || assignedDrones >= requiredDrones) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                if (neededDrones <= 0) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                // Create a fire event for this zone
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                int dispatched = 0;
                Set<String> assignedDroneIds = new HashSet<>();
                
                // First, try to assign available drones
                if (!availableDrones.isEmpty()) {
                    for (int i = 0; i < Math.min(neededDrones, availableDrones.size()); i++) {
                        DroneStatus drone = availableDrones.get(i);
                        String droneId = drone.getDroneId();
                        
                        Set<String> currentAssigned = new HashSet<>(assignedDroneIds);
                        DroneStatus redirectableStatus = findAvailableDrone(event, currentAssigned);
                        
                        FireEvent actualEvent = event;
                        int targetZoneId = zoneId;
                        
                        // Check if we should redirect to a different zone
                        if (redirectableStatus != null && redirectableStatus.getCurrentTask() != null) {
                            FireEvent redirectedTask = redirectableStatus.getCurrentTask();
                            if (redirectedTask.getZoneID() != zoneId) {
                                actualEvent = redirectedTask;
                                targetZoneId = redirectedTask.getZoneID();
                            }
                        }
                        
                        // Update drone status and send assignment
                        droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), actualEvent);
                        
                        fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                            (count == null) ? 1 : count + 1);
                            
                        boolean sent = sendToDrone(actualEvent, droneId);
                        if (sent) {
                            dispatched++;
                            assignedDroneIds.add(droneId);
                            droneAssignmentCount.incrementAndGet();
                        } else {
                            // If sending failed, revert the assignment
                            droneManager.updateDroneStatus(droneId, "Idle", drone.getCurrentLocation(), null);
                            fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                        }
                    }
                    
                    // Remove assigned drones from available list
                    availableDrones.removeIf(drone -> assignedDroneIds.contains(drone.getDroneId()));
                    neededDrones -= dispatched;
                }
                
                // If we still need more drones, try to redirect en-route drones
                if (neededDrones > 0) {
                    // Find en-route drones that could be redirected
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
                    
                    // Try to redirect each en-route drone
                    for (int i = 0; i < Math.min(neededDrones, enRouteDrones.size()); i++) {
                        DroneStatus drone = enRouteDrones.get(i);
                        String droneId = drone.getDroneId();
                        
                        FireEvent currentTask = drone.getCurrentTask();
                        int oldZoneId = currentTask.getZoneID();
                        
                        // Update counts for the old zone
                        fireEventAssignedDrones.compute(oldZoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                            
                        clearZoneFullyAssignedMark(oldZoneId);
                        
                        // Update drone status and send new assignment
                        droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                        
                        fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                            (count == null) ? 1 : count + 1);
                            
                        boolean sent = sendToDrone(event, droneId);
                        if (sent) {
                            dispatched++;
                            assignedDroneIds.add(droneId);
                            droneAssignmentCount.incrementAndGet();
                        } else {
                            // If sending failed, revert the assignment
                            droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), currentTask);
                            fireEventAssignedDrones.compute(oldZoneId, (id, count) -> 
                                (count == null) ? 1 : count + 1);
                            fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                                
                            // Check if the old zone is now fully assigned
                            int currentOldZoneAssigned = fireEventAssignedDrones.getOrDefault(oldZoneId, 0);
                            int requiredOldZone = fireEventRequiredDrones.getOrDefault(oldZoneId, 0);
                            if (currentOldZoneAssigned >= requiredOldZone) {
                                markZoneAsFullyAssigned(oldZoneId);
                            }
                        }
                    }
                }
                
                // Update visualization if any drones were dispatched
                if (dispatched > 0) {
                    updateVisualization();
                }
            }
        } catch (Exception e) {
            logError("Error checking active fires for drone assignment", e);
        }
    }
    
    /**
     * Dispatches drones to a fire event.
     * 
     * @param event The fire event to dispatch drones to
     * @param requestedDrones The number of drones requested for this fire
     */
    private void dispatchDronesToFire(FireEvent event, int requestedDrones) {
        try {
            int zoneId = event.getZoneID();
            Set<String> assignedDroneIds = new HashSet<>();
            
            // Count currently assigned drones
            int currentlyAssigned = countDronesForZone(zoneId);
            for (DroneStatus currentDrone : droneManager.getAllDrones()) {
                FireEvent currentTask = currentDrone.getCurrentTask();
                if (currentTask != null && currentTask.getZoneID() == zoneId && 
                    !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                    !currentDrone.getState().equalsIgnoreCase("Idle")) {
                    assignedDroneIds.add(currentDrone.getDroneId());
                }
            }
            
            // Update assigned drones count if needed
            if (currentlyAssigned != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                fireEventAssignedDrones.put(zoneId, currentlyAssigned);
            }
            
            // Skip if zone is already fully assigned
            if (isZoneFullyAssigned(zoneId)) {
                return;
            }
            
            // Calculate actual drones needed
            int actualDronesNeeded = requestedDrones;
            
            Zone zone = droneManager.getZone(zoneId);
            if (zone != null && zone.hasFire()) {
                actualDronesNeeded = Math.min(requestedDrones, 
                                            getDronesNeededForSeverity(zone.getSeverity()));
            }
            
            int remainingNeeded = actualDronesNeeded - currentlyAssigned;
            
            // Skip if we already have enough drones
            if (currentlyAssigned >= actualDronesNeeded) {
                markZoneAsFullyAssigned(zoneId);
                return;
            }
            
            // Try to dispatch drones
            int successfulDispatches = 0;
            for (int i = 0; i < remainingNeeded; i++) {
                DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
                
                if (drone != null) {
                    String droneId = drone.getDroneId();
                    assignedDroneIds.add(droneId);
                    
                    FireEvent droneTask = drone.getCurrentTask();
                    int targetZoneId = droneTask != null ? droneTask.getZoneID() : zoneId;
                    
                    // Update assigned drones count
                    Integer oldCount = fireEventAssignedDrones.getOrDefault(targetZoneId, 0);
                    Integer newCount = oldCount + 1;
                    fireEventAssignedDrones.put(targetZoneId, newCount);
                    
                    FireEvent actualEvent = (droneTask != null) ? droneTask : event;
                    
                    // Update drone status and send assignment
                    droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), actualEvent);
                    
                    boolean sent = sendToDrone(actualEvent, droneId);
                    if (sent) {
                        successfulDispatches++;
                        droneAssignmentCount.incrementAndGet();
                    } else {
                        // If sending failed, revert the assignment
                        fireEventAssignedDrones.compute(targetZoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                    }
                } else {
                    break;
                }
            }
            
            // Update visualization if any drones were dispatched
            if (successfulDispatches > 0) {
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error dispatching drones to fire", e);
        }
    }
    
    /**
     * Finds an available drone for a fire event.
     * 
     * @param event The fire event to find a drone for
     * @param assignedDroneIds The set of drone IDs that are already assigned
     * @return An available DroneStatus, or null if none are available
     */
    private DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds) {
        try {
            return droneManager.findAvailableDrone(event, assignedDroneIds, true);
        } catch (Exception e) {
            logError("Error finding available drone", e);
            return null;
        }
    }

    /**
     * Receives messages from the network and processes them.
     * This method runs in a separate thread and handles incoming messages
     * from drones and other components of the system.
     */
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
    
    /**
     * Processes periodic events in the system.
     * This method runs in a separate thread and handles regular system maintenance tasks.
     */
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
    
    /**
     * Shuts down the scheduler and cleans up resources.
     * This method should be called when the system is shutting down.
     */
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
    
    /**
     * Main method to start the Scheduler.
     * 
     * @param args Command line arguments (not used)
     */
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
