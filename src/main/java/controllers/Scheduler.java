package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import javax.swing.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;

import static models.FireEvent.createFireEventFromString;

/**
 * The Scheduler class manages the flow of fire incident events and drone responses.
 * It processes incoming fire events, assigns them to drones, and forwards drone responses
 * to the FireIncidentSubsystem.
 */
public class Scheduler {
    // Network components
    private final DatagramSocket sendSocket;
    private final DatagramSocket receiveSocket;
    private final int sendPort = 6000;
    private final int receivePort = 6001;
    private final InetAddress fireIncidentIP;
    
    // Thread-safe event queue based on severity priority
    private final PriorityBlockingQueue<FireEvent> eventQueue;
    
    // Drone management with thread-safety
    private final DroneManager droneManager;
    private final ConcurrentMap<String, Integer> dronePorts;
    
    // Fire events tracking with thread-safety
    private final ConcurrentMap<Integer, Integer> fireEventAssignedDrones;
    private final ConcurrentMap<Integer, Integer> fireEventRequiredDrones;
    private final Set<Integer> fullyAssignedZones = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Thread control
    private final AtomicBoolean isRunning;
    private final ReadWriteLock visualizationLock;
    
    // Visualization component
    private DroneVisualization visualization;
    
    // Executor services for concurrent operations
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService workerExecutor;
    
    // Basic statistics tracking
    private final AtomicInteger messageReceiveCount = new AtomicInteger(0);
    private final AtomicInteger fireSentCount = new AtomicInteger(0);
    private final AtomicInteger droneAssignmentCount = new AtomicInteger(0);
    private final AtomicInteger fireExtinguishedCount = new AtomicInteger(0);
    
    /**
     * Logging methods (all disabled by default)
     */
    protected void log(String message) {}
    protected void logVerbose(String message) {}
    protected void logError(String message, Throwable e) {}
    protected void logSystemState() {}
    protected void logDroneStatuses() {}
    protected void logActiveFiresDetail() {}
    
    // No longer needed - removed timing overhead wrappers

    /**
     * Custom comparator for FireEvent to prioritize based on severity
     */
    static class FireEventComparator implements Comparator<FireEvent> {
        @Override
        public int compare(FireEvent e1, FireEvent e2) {
            // Compare severity first (High > Moderate > Low)
            int severityCompare = getSeverityWeight(e2.getSeverity()) - getSeverityWeight(e1.getSeverity());
            if (severityCompare != 0) return severityCompare;
            
            // If severity is the same, compare by time (older events first)
            return e1.getTime().compareTo(e2.getTime());
        }
    }
    
    /**
     * Helper method to get numeric weight for severity levels
     */
    private static int getSeverityWeight(String severity) {
        switch (severity.toLowerCase()) {
            case "high": return 100;
            case "moderate": return 50;
            case "low": return 10;
            default: return 0;
        }
    }

    /**
     * Constructs a new Scheduler with drone management capability
     *
     * @param ip The IP address of the fire incident system
     * @throws SocketException if socket creation fails
     */
    public Scheduler(InetAddress ip) throws SocketException {
        log("Initializing Scheduler");
        this.fireIncidentIP = ip;
        this.eventQueue = new PriorityBlockingQueue<>(20, new FireEventComparator());
        this.dronePorts = new ConcurrentHashMap<>();
        this.fireEventAssignedDrones = new ConcurrentHashMap<>();
        this.fireEventRequiredDrones = new ConcurrentHashMap<>();
        this.isRunning = new AtomicBoolean(true);
        this.visualizationLock = new ReentrantReadWriteLock();
        
        // Initialize base resources
        Location baseLocation = new Location(0, 0);
        this.droneManager = new DroneManager(baseLocation);
        log("DroneManager initialized with base at " + baseLocation);
        
        // Initialize network resources
        try {
            log("Creating network sockets (send=" + sendPort + ", receive=" + receivePort + ")");
            this.sendSocket = new DatagramSocket(sendPort);
            this.receiveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            logError("Failed to create sockets", e);
            throw e;
        }
        
        // Initialize thread pools
        log("Initializing thread pools");
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.workerExecutor = Executors.newCachedThreadPool();
        
        // Register drone ports for communication
        registerDronePorts();
        
        // Initialize the visualization component
        initializeVisualization();
        
        // Schedule periodic logging
        scheduledExecutor.scheduleAtFixedRate(this::logSystemState, 10, 10, TimeUnit.SECONDS);
        
        log("Scheduler initialization complete");
    }
    
    /**
     * Registers ports for all drones to enable communication
     */
    private void registerDronePorts() {
        log("Registering communication ports for drones");
        for (int i = 1; i <= 10; i++) {
            String droneId = "drone" + i;
            int port = 7001 + (i * 100); // Same calculation as DroneSubsystem uses
            dronePorts.put(droneId, port);
            logVerbose("Registered " + droneId + " on port " + port);
        }
        log("Registered ports for 10 drones");
    }
    
    /**
     * Initializes the visualization component on the Event Dispatch Thread
     */
    private void initializeVisualization() {
        log("Initializing visualization component on EDT");
        SwingUtilities.invokeLater(() -> {
            try {
                log("Acquiring visualization write lock");
                visualizationLock.writeLock().lock();
                log("Creating DroneVisualization");
                visualization = new DroneVisualization(droneManager);
                log("Visualization component initialized");
            } catch (Exception e) {
                logError("Failed to initialize visualization", e);
            } finally {
                visualizationLock.writeLock().unlock();
                log("Released visualization write lock");
            }
        });
    }

    /**
     * Receives a message and processes it based on content
     * @return FireEvent if it's a fire event, null otherwise
     */
    public FireEvent receive() {
        try {
            byte[] data = new byte[100];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            
            receiveSocket.receive(receivePacket);
            messageReceiveCount.incrementAndGet();
            
            String message = new String(data, 0, receivePacket.getLength());
            InetAddress sender = receivePacket.getAddress();
            int port = receivePacket.getPort();
            
            // Check if this is a drone status update
            if (isDroneStatusUpdate(message)) {
                processDroneStatusUpdate(message);
                return null;
            }

            // Check if this is a zone info request from DroneSubsystem
            if (message.startsWith("ZONE_INFO_REQUEST:")) {
                handleZoneInfoRequest(message, port, sender);
                return null;
            }

            // Try to parse as a FireEvent
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
     * Handles a zone info request from a drone
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
     * Checks if a message is a drone status update
     * @param message The message to check
     * @return true if it's a status update, false otherwise
     */
    private boolean isDroneStatusUpdate(String message) {
        try {
            String[] parts = message.split(" ");
            
            // Must have at least: droneId state x y
            if (parts.length < 4) {
                return false;
            }
            
            // First part must be a drone ID
            String droneId = parts[0];
            if (!droneId.startsWith("drone")) {
                return false;
            }
            
            // Last two elements should be coordinates
            try {
                Integer.parseInt(parts[parts.length - 2]);
                Integer.parseInt(parts[parts.length - 1]);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        } catch (Exception e) {
            logError("Error in isDroneStatusUpdate", e);
            return false;
        }
    }

    /**
     * Helper class to store task info extracted from status messages
     */
    private static class TaskInfo {
        int zoneId = -1;
        String severity = null;
        boolean isFireOut = false;
    }
    
    /**
     * Processes a drone status update
     * @param message The status update message
     */
    private void processDroneStatusUpdate(String message) {
        try {
            log("Processing drone status update: " + message);
            String[] parts = message.split(" ");
            String droneId = parts[0];
            String state = parts[1];
            
            // Extract location (last two elements are x and y coordinates)
            int x = Integer.parseInt(parts[parts.length - 2]);
            int y = Integer.parseInt(parts[parts.length - 1]);
            Location location = new Location(x, y);
            
            log(droneId + " status update: State=" + state + ", Location=(" + x + "," + y + ")");
            
            // Parse task and fire status information
            TaskInfo taskInfo = extractTaskInfo(parts);
            
            // Process drone update
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                // Register new drone
                log("Registering new drone: " + droneId);
                status = droneManager.registerDrone(droneId);
            }
            
            // Process fire extinguished status if indicated
            boolean fireWasExtinguished = false;
            if (taskInfo.isFireOut && taskInfo.zoneId > 0) {
                log("Fire-out notification for Zone " + taskInfo.zoneId + " from " + droneId);
                droneManager.updateZoneFireStatus(taskInfo.zoneId, false, "NONE");
                
                Integer previousAssigned = fireEventAssignedDrones.remove(taskInfo.zoneId);
                Integer previousRequired = fireEventRequiredDrones.remove(taskInfo.zoneId);
                log("Removed tracking for Zone " + taskInfo.zoneId + 
                    " (previously " + previousAssigned + "/" + previousRequired + " drones)");
                
                fireWasExtinguished = true;
                fireExtinguishedCount.incrementAndGet();
            }
            
            // Check if state or location changed
            boolean stateChanged = !status.getState().equalsIgnoreCase(state);
            boolean locationChanged = !status.getCurrentLocation().equals(location);
            
            // Update drone status
            if (stateChanged) {
                log(droneId + " state changed: " + status.getState() + " -> " + state);
            }
            
            updateDroneStatus(status, droneId, state, location, taskInfo);
            
            // Update visualization if needed
            if (fireWasExtinguished || stateChanged || locationChanged) {
                log("Updating visualization due to changes");
                updateVisualization();
            }
            
            // If drone becomes idle, check if there are active fires to assign it to
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state))) {
                // Make a final copy for the lambda to use
                final DroneStatus finalStatus = status;
                log("Drone " + droneId + " is idle, submitting task to check for pending fires");
                
                // Use worker thread to process assignment to avoid blocking receive thread
                workerExecutor.submit(() -> {
                    try {
                        log("Worker thread started for " + droneId + " to find pending fire assignments");
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
     * Updates visualization in a thread-safe manner
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
     * Extracts task and fire status information from a status message
     */
    private TaskInfo extractTaskInfo(String[] parts) {
        TaskInfo info = new TaskInfo();
        
        // Look for TASK: and FIRE_OUT: tags in the message
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
            }
        }
        
        return info;
    }
    
    /**
     * Updates an existing drone with new status information
     */
    private void updateDroneStatus(DroneStatus status, String droneId, String state, 
                                Location location, TaskInfo taskInfo) {
        try {
            // Handle transition to idle state
            FireEvent currentTask = status.getCurrentTask();
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state)) && 
                currentTask != null) {
                // If drone was previously assigned to a fire, update tracking
                int zoneId = currentTask.getZoneID();
                log(droneId + " is now idle, was assigned to Zone " + zoneId);
                
                // Atomically decrement the assigned count
                Integer oldCount = fireEventAssignedDrones.get(zoneId);
                if (oldCount != null) {
                    Integer newCount = Math.max(0, oldCount - 1);
                    fireEventAssignedDrones.put(zoneId, newCount);
                    log("Updated drone count for Zone " + zoneId + ": " + 
                       oldCount + " -> " + newCount);
                    
                    // Clear the fully assigned mark if we're now below the threshold
                    int requiredDrones = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                    if (newCount < requiredDrones) {
                        clearZoneFullyAssignedMark(zoneId);
                        log("Zone " + zoneId + " is no longer fully assigned");
                    }
                }
                
                currentTask = null; // Clear task when idle
            }
            
            // Update drone status
            droneManager.updateDroneStatus(droneId, state, location, currentTask);
        } catch (Exception e) {
            logError("Error updating drone status for " + droneId, e);
        }
    }

    /**
     * Check if there are pending fire events that need drones and assign this idle drone if needed
     */
    private void checkForPendingFireEvents(DroneStatus drone) {
        try {
            String droneId = drone.getDroneId();
            log("Checking for pending fire events for " + droneId);
            
            // First update all our maps to remove any extinguished fires
            cleanupExtinguishedFires();
            
            // Find the highest priority fire that needs more drones
            Integer highestPriorityZoneId = null;
            String highestPrioritySeverity = null;
            
            // Get a snapshot to avoid ConcurrentModificationException
            Map<Integer, Integer> requiredSnapshot = new HashMap<>(fireEventRequiredDrones);
            
            // Look through all fires that need more drones
            for (Map.Entry<Integer, Integer> entry : requiredSnapshot.entrySet()) {
                int zoneId = entry.getKey();
                int required = entry.getValue();
                int assigned = fireEventAssignedDrones.getOrDefault(zoneId, 0);
                
                // If this zone needs more drones according to our tracking
                if (assigned < required) {
                    Zone zone = droneManager.getZone(zoneId);
                    if (zone != null && zone.hasFire()) {
                        // Check if zone is marked as fully assigned
                        if (isZoneFullyAssigned(zoneId)) {
                            continue;
                        }
                        
                        // Double-check the actual required number
                        int actualRequired = getDronesNeededForSeverity(zone.getSeverity());
                        
                        // Count drones currently assigned to this zone
                        int actualAssignedCount = countDronesForZone(zoneId);
                        
                        // Skip if we already have enough drones based on actual counts
                        if (actualAssignedCount >= actualRequired) {
                            markZoneAsFullyAssigned(zoneId);
                            continue;
                        }
                        
                        String severity = zone.getSeverity();
                        int currentWeight = highestPrioritySeverity != null ? 
                            getSeverityWeight(highestPrioritySeverity) : -1;
                        int newWeight = getSeverityWeight(severity);
                        
                        // If this is our first candidate or has higher severity
                        if (highestPriorityZoneId == null || newWeight > currentWeight) {
                            highestPriorityZoneId = zoneId;
                            highestPrioritySeverity = severity;
                        }
                    }
                }
            }
            
            // If we found a fire that needs drones, dispatch this drone to it
            if (highestPriorityZoneId != null) {
                final int zoneId = highestPriorityZoneId;
                final String severity = highestPrioritySeverity;
                
                // Create a FireEvent for this zone
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                // Update tracking atomically
                Integer oldCount = fireEventAssignedDrones.get(zoneId);
                Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                fireEventAssignedDrones.put(zoneId, newCount);
                
                // Update drone status
                droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                
                // Send event to drone
                boolean sent = sendToDrone(event, droneId);
                
                if (sent) {
                    droneAssignmentCount.incrementAndGet();
                } else {
                    // Revert the count increment since assignment failed
                    fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                        count != null && count > 0 ? count - 1 : null);
                }
                
                // Update visualization
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error checking for pending fire events", e);
        }
    }
    
    /**
     * Counts the number of drones assigned to a specific zone
     */
    private int countDronesForZone(int zoneId) {
        int count = 0;
        for (DroneStatus drone : droneManager.getAllDrones()) {
            FireEvent task = drone.getCurrentTask();
            if (task != null && task.getZoneID() == zoneId &&
                !drone.getState().equalsIgnoreCase("IDLE") &&
                !drone.getState().equalsIgnoreCase("Idle")) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Removes any tracking for fires that are no longer active
     */
    private void cleanupExtinguishedFires() {
        try {
            // Get a snapshot of current zone IDs to avoid concurrent modification
            Set<Integer> zoneIds = new HashSet<>(fireEventRequiredDrones.keySet());
            
            if (zoneIds.isEmpty()) {
                return;
            }
            
            // Check each zone and remove if fire is extinguished
            for (Integer zoneId : zoneIds) {
                Zone zone = droneManager.getZone(zoneId);
                if (zone == null || !zone.hasFire()) {
                    // Remove from tracking maps
                    fireEventRequiredDrones.remove(zoneId);
                    fireEventAssignedDrones.remove(zoneId);
                    
                    // Remove from fully assigned zones set
                    clearZoneFullyAssignedMark(zoneId);
                    
                    // Also remove from event queue if present
                    removeFromEventQueue(zoneId);
                }
            }
        } catch (Exception e) {
            logError("Error cleaning up extinguished fires", e);
        }
    }
    
    /**
     * Removes events for a specific zone from the event queue
     * @return The number of events removed
     */
    private int removeFromEventQueue(int zoneId) {
        try {
            // Create a temporary list to hold events we want to keep
            List<FireEvent> eventsToKeep = new ArrayList<>();
            int removedCount = 0;
            
            // Drain the queue
            FireEvent event;
            while ((event = eventQueue.poll()) != null) {
                if (event.getZoneID() != zoneId) {
                    eventsToKeep.add(event);
                } else {
                    removedCount++;
                }
            }
            
            // Add back events we want to keep
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
     * Creates a FireEvent object for a specified zone
     */
    private FireEvent createFireEventForZone(int zoneId, String severity) {
        // Generate a timestamp
        String timestamp = String.format("%d", System.currentTimeMillis());
        
        // Create a new fire event
        return new FireEvent(timestamp, zoneId, "FIRE", severity, "NONE");
    }

    /**
     * Sends a UDP packet to the designated IP and port
     * @param fire the fire event to send
     * @param port the port to send to
     * @return true if sending was successful, false otherwise
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
     * Sends a string message to the designated IP and port
     * @param message the message to send
     * @param port the port to send to
     * @param ip the IP address to send to
     * @return true if sending was successful, false otherwise
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
     * Sends a fire event to a specific drone
     *
     * @param fire The fire event to send
     * @param droneId The ID of the drone to send to
     * @return true if send was successful, false otherwise
     */
    public boolean sendToDrone(FireEvent fire, String droneId) {
        try {
            // Assign this drone to the fire event
            fire.assignDrone(droneId);
            
            // Get the port for this drone
            Integer port = dronePorts.get(droneId);
            if (port == null) {
                log("Error: No port registration found for " + droneId);
                return false;
            }
            
            log("Sending fire event to " + droneId + " for Zone " + fire.getZoneID());
            return send(fire, port);
        } catch (Exception e) {
            logError("Error in sendToDrone for " + droneId, e);
            return false;
        }
    }

    /**
     * Determines the number of drones needed based on fire severity
     *
     * @param severity the fire severity
     * @return the number of drones to dispatch
     */
    private int getDronesNeededForSeverity(String severity) {
        try {
            switch (severity.toLowerCase()) {
                case "high":
                    return 3; // 30L total capacity needed - 3 drones with 10L each
                case "moderate":
                    return 2; // 20L total capacity needed - 2 drones with 10L each
                case "low":
                default:
                    return 1; // 10L total capacity needed - 1 drone with 10L
            }
        } catch (Exception e) {
            logError("Error calculating drones needed for severity", e);
            // Default to 1 drone in case of error
            return 1;
        }
    }
    
    /**
     * Marks a zone as fully assigned - it has enough drones to handle the fire
     * 
     * @param zoneId the zone ID
     */
    private void markZoneAsFullyAssigned(int zoneId) {
        fullyAssignedZones.add(zoneId);
        log("Zone " + zoneId + " is now marked as fully assigned");
    }
    
    /**
     * Checks if a zone is already fully assigned
     * 
     * @param zoneId the zone ID
     * @return true if the zone is fully assigned
     */
    private boolean isZoneFullyAssigned(int zoneId) {
        return fullyAssignedZones.contains(zoneId);
    }
    
    /**
     * Removes fully assigned mark for a zone
     * 
     * @param zoneId the zone ID
     */
    private void clearZoneFullyAssignedMark(int zoneId) {
        fullyAssignedZones.remove(zoneId);
    }

    /**
     * Processes the next fire event in the queue and assigns drones
     */
    private void processNextFireEvent() {
        try {
            // Get the next event from the queue
            FireEvent event = eventQueue.poll();
            if (event == null) {
                // If the queue is empty, scan for active fires that need drones
                checkActiveFiresForDroneAssignment();
                return;
            }
            
            // Extract event details
            int zoneId = event.getZoneID();
            String severity = event.getSeverity();
            
            Zone zone = droneManager.getZone(zoneId);
            
            // Create zone if it doesn't exist yet
            if (zone == null) {
                Location zoneLocation = new Location(
                    ((zoneId-1) % 3) * 700 + 350,  // 700m wide zones, centered at x+350
                    ((zoneId-1) / 3) * 600 + 300); // 600m tall zones, centered at y+300
                
                zone = droneManager.createZone(zoneId, zoneLocation);
            }
            
            // Check if zone already has fire of same or higher severity
            boolean updateZoneStatus = true;
            if (zone.hasFire()) {
                int currentSeverityWeight = getSeverityWeight(zone.getSeverity());
                int newSeverityWeight = getSeverityWeight(severity);
                
                if (currentSeverityWeight >= newSeverityWeight) {
                    updateZoneStatus = false;
                }
            }
            
            // Update zone fire status if needed
            if (updateZoneStatus) {
                droneManager.updateZoneFireStatus(zoneId, true, severity);
                
                // Determine drones needed and track requirements
                int dronesNeeded = getDronesNeededForSeverity(severity);
                int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                
                if (currentRequired < dronesNeeded) {
                    // Update required drones if new severity needs more
                    fireEventRequiredDrones.put(zoneId, dronesNeeded);
                }
                
                fireEventAssignedDrones.putIfAbsent(zoneId, 0);
            }
            
            // Update visualization with fire before dispatching drones
            updateVisualization();
            
            // Find and dispatch available drones only if we need more
            if (updateZoneStatus) {
                dispatchDronesToFire(event, fireEventRequiredDrones.get(zoneId));
            }
            
            // After processing this event, check other active fires that might need drones
            checkActiveFiresForDroneAssignment();
        } catch (Exception e) {
            logError("Error processing fire event", e);
        }
    }
    
    /**
     * Checks all active fires and assigns available drones to them based on priority
     */
    private void checkActiveFiresForDroneAssignment() {
        try {
            // Get all zones with active fires
            Map<Integer, Zone> zones = droneManager.getAllZones();
            List<Map.Entry<Integer, Zone>> activeFireZones = zones.entrySet().stream()
                .filter(entry -> entry.getValue().hasFire())
                .sorted((e1, e2) -> {
                    // Sort by severity (high to low)
                    int severityCompare = getSeverityWeight(e2.getValue().getSeverity()) - 
                                         getSeverityWeight(e1.getValue().getSeverity());
                    if (severityCompare != 0) return severityCompare;
                    
                    // If same severity, sort by which needs more drones proportionally
                    int zone1 = e1.getKey();
                    int zone2 = e2.getKey();
                    int required1 = fireEventRequiredDrones.getOrDefault(zone1, 0);
                    int required2 = fireEventRequiredDrones.getOrDefault(zone2, 0);
                    int assigned1 = fireEventAssignedDrones.getOrDefault(zone1, 0);
                    int assigned2 = fireEventAssignedDrones.getOrDefault(zone2, 0);
                    
                    // If required is 0, treat as fully assigned
                    double ratio1 = required1 == 0 ? 1.0 : (double)assigned1 / required1;
                    double ratio2 = required2 == 0 ? 1.0 : (double)assigned2 / required2;
                    
                    return Double.compare(ratio1, ratio2); // Lower ratio first (needs more drones)
                })
                .collect(Collectors.toList());
            
            if (activeFireZones.isEmpty()) {
                return;
            }
            
            // Get available drones
            Collection<DroneStatus> allDrones = droneManager.getAllDrones();
            List<DroneStatus> availableDrones = allDrones.stream()
                .filter(DroneStatus::isAvailable)
                .collect(Collectors.toList());
            
            if (availableDrones.isEmpty()) {
                return;
            }
            
            // Process each fire zone in priority order
            for (Map.Entry<Integer, Zone> entry : activeFireZones) {
                int zoneId = entry.getKey();
                Zone zone = entry.getValue();
                String severity = zone.getSeverity();
                
                // Get the actual number of drones needed based on current severity
                int actualDronesNeeded = getDronesNeededForSeverity(severity);
                
                // Use the stored required value, but make sure it's not more than we actually need
                int requiredDrones = Math.min(
                    fireEventRequiredDrones.getOrDefault(zoneId, 0),
                    actualDronesNeeded
                );
                
                // Update required drones if our calculation is different
                if (requiredDrones != fireEventRequiredDrones.getOrDefault(zoneId, 0)) {
                    fireEventRequiredDrones.put(zoneId, requiredDrones);
                }
                
                // Count drones currently assigned to this zone
                int assignedDrones = countDronesForZone(zoneId);
                
                // Update our tracking to match actual count if different
                if (assignedDrones != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                    fireEventAssignedDrones.put(zoneId, assignedDrones);
                }
                
                int neededDrones = requiredDrones - assignedDrones;
                
                // Check if this zone is already fully assigned or has enough drones
                if (isZoneFullyAssigned(zoneId) || assignedDrones >= requiredDrones) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                if (neededDrones <= 0) {
                    markZoneAsFullyAssigned(zoneId);
                    continue;
                }
                
                if (availableDrones.isEmpty()) {
                    break;
                }
                
                // Create event for the zone
                FireEvent event = createFireEventForZone(zoneId, severity);
                
                // Dispatch drones to this fire
                int dispatched = 0;
                Set<String> assignedDroneIds = new HashSet<>();
                
                for (int i = 0; i < Math.min(neededDrones, availableDrones.size()); i++) {
                    DroneStatus drone = availableDrones.get(i);
                    String droneId = drone.getDroneId();
                    
                    // Assign drone to fire
                    droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                    
                    // Update assigned count
                    fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                        (count == null) ? 1 : count + 1);
                        
                    // Send assignment to drone
                    boolean sent = sendToDrone(event, droneId);
                    if (sent) {
                        dispatched++;
                        assignedDroneIds.add(droneId);
                        droneAssignmentCount.incrementAndGet();
                    } else {
                        // Revert assignment if send fails
                        droneManager.updateDroneStatus(droneId, "Idle", drone.getCurrentLocation(), null);
                        fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                    }
                }
                
                // Remove assigned drones from available list
                availableDrones.removeIf(drone -> assignedDroneIds.contains(drone.getDroneId()));
                
                // Update visualization after drone assignments
                if (dispatched > 0) {
                    updateVisualization();
                }
            }
        } catch (Exception e) {
            logError("Error checking active fires for drone assignment", e);
        }
    }
    
    /**
     * Dispatches the required number of drones to a fire event
     */
    private void dispatchDronesToFire(FireEvent event, int requestedDrones) {
        try {
            int zoneId = event.getZoneID();
            Set<String> assignedDroneIds = new HashSet<>();
            
            // Count drones already assigned to this zone
            int currentlyAssigned = countDronesForZone(zoneId);
            for (DroneStatus currentDrone : droneManager.getAllDrones()) {
                FireEvent currentTask = currentDrone.getCurrentTask();
                if (currentTask != null && currentTask.getZoneID() == zoneId && 
                    !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                    !currentDrone.getState().equalsIgnoreCase("Idle")) {
                    assignedDroneIds.add(currentDrone.getDroneId());
                }
            }
            
            // Update our tracking to match actual count if different
            if (currentlyAssigned != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                fireEventAssignedDrones.put(zoneId, currentlyAssigned);
            }
            
            // Check if zone is already fully assigned
            if (isZoneFullyAssigned(zoneId)) {
                return;
            }
            
            // Determine the actual number of drones needed
            int actualDronesNeeded = requestedDrones;
            
            // Get the zone to check its current severity
            Zone zone = droneManager.getZone(zoneId);
            if (zone != null && zone.hasFire()) {
                // Use the minimum of requested and actual drones needed
                actualDronesNeeded = Math.min(requestedDrones, 
                                              getDronesNeededForSeverity(zone.getSeverity()));
            }
            
            int remainingNeeded = actualDronesNeeded - currentlyAssigned;
            
            // Mark as fully assigned if we already have enough drones
            if (currentlyAssigned >= actualDronesNeeded) {
                markZoneAsFullyAssigned(zoneId);
                return;
            }
            
            // Find and dispatch drones
            int successfulDispatches = 0;
            for (int i = 0; i < remainingNeeded; i++) {
                DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
                
                if (drone != null) {
                    // Process this drone
                    String droneId = drone.getDroneId();
                    assignedDroneIds.add(droneId);
                    
                    // Update tracking count
                    Integer oldCount = fireEventAssignedDrones.get(zoneId);
                    Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                    fireEventAssignedDrones.put(zoneId, newCount);
                    
                    // Update drone status and send event
                    droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                    
                    boolean sent = sendToDrone(event, droneId);
                    if (sent) {
                        successfulDispatches++;
                        droneAssignmentCount.incrementAndGet();
                    } else {
                        // Revert tracking count if send failed
                        fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                    }
                    
                    Thread.yield();
                } else {
                    break;
                }
            }
            
            // Update visualization after dispatching
            if (successfulDispatches > 0) {
                updateVisualization();
            }
        } catch (Exception e) {
            logError("Error dispatching drones to fire", e);
        }
    }
    
    /**
     * Finds an available drone for a fire event, avoiding duplicates
     */
    private DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds) {
        try {
            // Use DroneManager to select best drone
            DroneStatus selectedDrone = droneManager.selectBestDroneForEvent(event);
            
            if (selectedDrone == null) {
                return null;
            }
            
            String droneId = selectedDrone.getDroneId();
            
            // Check if drone is already in the assigned set
            if (assignedDroneIds.contains(droneId)) {
                return null;
            }
            
            // Check if drone is still available (could have changed state concurrently)
            if (!selectedDrone.isAvailable()) {
                return null;
            }
            
            return selectedDrone;
        } catch (Exception e) {
            logError("Error finding available drone", e);
            return null;
        }
    }

    /**
     * Thread function for receiving messages
     */
    private void receiveMessages() {
        // Main message processing loop
        while (isRunning.get()) {
            // Attempt to receive a message
            FireEvent event = receive();
            
            // If we received a fire event, process it
            if (event != null) {
                // Process the fire event in a worker thread to avoid blocking receive thread
                final FireEvent finalEvent = event;
                
                workerExecutor.submit(() -> {
                    try {
                        // Update zone fire status
                        int zoneId = finalEvent.getZoneID();
                        String severity = finalEvent.getSeverity();
                        
                        // Create or update zone
                        Zone zone = droneManager.getZone(zoneId);
                        if (zone == null) {
                            Location zoneLocation = new Location(
                                ((zoneId-1) % 3) * 700 + 350,
                                ((zoneId-1) / 3) * 600 + 300);
                            zone = droneManager.createZone(zoneId, zoneLocation);
                        }
                        
                        // Check if zone already has a higher severity fire
                        boolean updateZoneStatus = true;
                        if (zone.hasFire()) {
                            int currentSeverityWeight = getSeverityWeight(zone.getSeverity());
                            int newSeverityWeight = getSeverityWeight(severity);
                            
                            if (currentSeverityWeight >= newSeverityWeight) {
                                updateZoneStatus = false;
                            }
                        }
                        
                        // Update zone fire status
                        if (updateZoneStatus) {
                            droneManager.updateZoneFireStatus(zoneId, true, severity);
                        }
                        
                        // Add to priority queue 
                        eventQueue.add(finalEvent);
                        
                        // Track required drones based on severity
                        int dronesNeeded = getDronesNeededForSeverity(severity);
                        int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                        
                        // Only update if new requirement is higher
                        if (currentRequired < dronesNeeded) {
                            fireEventRequiredDrones.put(zoneId, dronesNeeded);
                        }
                        
                        // Initialize assigned count if not present
                        fireEventAssignedDrones.putIfAbsent(zoneId, 0);
                        
                        // Send acknowledgement to FireIncidentSubsystem
                        send(finalEvent, 5001);
                        
                        // Update visualization
                        updateVisualization();
                    } catch (Exception e) {
                        logError("Error processing incoming fire event", e);
                    }
                });
            }
            
            // Small pause to prevent tight loop
            Thread.yield();
        }
    }

    /**
     * Thread function for processing events and assigning to drones
     */
    private void processEvents() {
        // Run periodic cleanup
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExtinguishedFires();
            } catch (Exception e) {
                logError("Error in periodic cleanup", e);
            }
        }, 5, 15, TimeUnit.SECONDS);
        
        // Run periodic drone assignment check to ensure fires get drones
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                if (eventQueue.isEmpty()) {
                    checkActiveFiresForDroneAssignment();
                }
            } catch (Exception e) {
                logError("Error in periodic drone assignment", e);
            }
        }, 3, 3, TimeUnit.SECONDS);
        
        // Main processing loop
        while (isRunning.get()) {
            if (!eventQueue.isEmpty()) {
                // Process fire events and assign drones
                processNextFireEvent();
            }

            // Brief pause between processing cycles
            Thread.yield();
        }
    }
    
    /**
     * Shuts down the scheduler and releases resources
     */
    public void shutdown() {
        try {
            isRunning.set(false);
            
            // Shutdown thread pools
            scheduledExecutor.shutdownNow();
            workerExecutor.shutdownNow();
            
            try {
                // Wait briefly for tasks to complete
                scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS);
                workerExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Close sockets
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
     * Main entry point for the scheduler
     */
    public static void main(String[] args) {
        Scheduler scheduler = null;
        
        try {
            // Initialize scheduler with all logging disabled
            InetAddress ip = InetAddress.getLocalHost();
            scheduler = new Scheduler(ip);
            
            // Start processing threads
            Thread receiveThread = new Thread(scheduler::receiveMessages);
            Thread processThread = new Thread(scheduler::processEvents);
            
            receiveThread.setName("Scheduler-Receive");
            processThread.setName("Scheduler-Process");
            
            receiveThread.start();
            processThread.start();
            
            // Register shutdown hook for clean termination
            final Scheduler finalScheduler = scheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(finalScheduler::shutdown));
            
            // Wait for threads to complete (they normally won't)
            receiveThread.join();
            processThread.join();
            
        } catch (Exception e) {
            // Handle silently
        } finally {
            // Ensure resources are released
            if (scheduler != null) {
                scheduler.shutdown();
            }
        }
    }
}