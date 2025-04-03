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
    
    // Logging and statistics
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final AtomicInteger messageReceiveCount = new AtomicInteger(0);
    private final AtomicInteger fireSentCount = new AtomicInteger(0);
    private final AtomicInteger droneAssignmentCount = new AtomicInteger(0);
    private final AtomicInteger fireExtinguishedCount = new AtomicInteger(0);
    private final ConcurrentMap<String, Long> methodExecutionTimes = new ConcurrentHashMap<>();
    private final boolean verboseLogging = false; // Set to false to disable detailed logs
    
    /**
     * Logs a message with the current timestamp and thread information
     */
    protected void log(String message) {
        // Logging disabled
    }
    
    /**
     * Logs a message if verbose logging is enabled
     */
    protected void logVerbose(String message) {
        // Verbose logging disabled
    }
    
    /**
     * Logs error messages
     */
    protected void logError(String message, Throwable e) {
        // Error logging disabled
    }
    
    /**
     * Utility method to time method execution
     */
    private <T> T timeExecution(String methodName, Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } finally {
            // Timing disabled
        }
    }
    
    /**
     * Utility method to time void method execution
     */
    private void timeExecution(String methodName, Runnable runnable) {
        try {
            runnable.run();
        } finally {
            // Timing disabled
        }
    }
    
    /**
     * Logs the current state of the system for diagnostics
     */
    protected void logSystemState() {
        // System state logging disabled
    }
    
    /**
     * Logs detailed status of each drone
     */
    protected void logDroneStatuses() {
        // Drone status logging disabled
    }
    
    /**
     * Logs details about active fires
     */
    protected void logActiveFiresDetail() {
        // Fire details logging disabled
    }

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
            return timeExecution("receive", () -> {
                logVerbose("Waiting for incoming packet...");
                byte[] data = new byte[100];
                DatagramPacket receivePacket = new DatagramPacket(data, data.length);
    
                try {
                    receiveSocket.receive(receivePacket);
                    messageReceiveCount.incrementAndGet();
                    
                    int len = receivePacket.getLength();
                    String message = new String(data, 0, len);
                    InetAddress sender = receivePacket.getAddress();
                    int port = receivePacket.getPort();
                    
                    log("Received message from " + sender + ":" + port + ", length=" + len);
                    logVerbose("Message content: " + message);
                    
                    // Check if this is a drone status update
                    if (isDroneStatusUpdate(message)) {
                        log("Message identified as drone status update");
                        processDroneStatusUpdate(message);
                        return null;
                    }
    
                    // Try to parse as a FireEvent
                    log("Message identified as fire event, parsing...");
                    FireEvent event = createFireEventFromString(message);
                    if (event != null) {
                        log("Parsed fire event: Zone=" + event.getZoneID() + 
                            ", Severity=" + event.getSeverity() + 
                            ", Time=" + event.getTime());
                    }
                    return event;
                } catch (IOException e) {
                    if (isRunning.get()) {
                        logError("Error receiving packet", e);
                    }
                    return null;
                } catch (Exception e) {
                    logError("Error parsing message", e);
                    return null;
                }
            });
        } catch (Exception e) {
            logError("Critical error in receive method", e);
            return null;
        }
    }

    /**
     * Checks if a message is a drone status update
     * @param message The message to check
     * @return true if it's a status update, false otherwise
     */
    private boolean isDroneStatusUpdate(String message) {
        try {
            return timeExecution("isDroneStatusUpdate", () -> {
                logVerbose("Checking if message is a drone status update: " + message);
                String[] parts = message.split(" ");
                
                // Must have at least: droneId state x y
                if (parts.length < 4) {
                    logVerbose("Rejected: insufficient parts in message (required 4+, found " + parts.length + ")");
                    return false;
                }
                
                String droneId = parts[0];
                if (!droneId.startsWith("drone")) {
                    logVerbose("Rejected: first part doesn't start with 'drone'");
                    return false;
                }
                
                // Last two elements should be coordinates
                try {
                    int x = Integer.parseInt(parts[parts.length - 2]);
                    int y = Integer.parseInt(parts[parts.length - 1]);
                    logVerbose("Validated as drone status update with coords: (" + x + "," + y + ")");
                    return true;
                } catch (NumberFormatException e) {
                    logVerbose("Rejected: last two parts are not valid integers");
                    return false;
                }
            });
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
            timeExecution("processDroneStatusUpdate", () -> {
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
            });
        } catch (Exception e) {
            logError("Error processing drone status", e);
        }
    }
    
    /**
     * Updates visualization in a thread-safe manner
     */
    private void updateVisualization() {
        try {
            timeExecution("updateVisualization", () -> {
                logVerbose("Acquiring visualization read lock");
                visualizationLock.readLock().lock();
                try {
                    if (visualization != null) {
                        logVerbose("Submitting visualization update to Swing EDT");
                        SwingUtilities.invokeLater(() -> {
                            try {
                                visualization.updateVisualization();
                                logVerbose("Visualization updated on EDT");
                            } catch (Exception e) {
                                logError("Error updating visualization on EDT", e);
                            }
                        });
                    } else {
                        logVerbose("Visualization component not yet initialized, update skipped");
                    }
                } finally {
                    visualizationLock.readLock().unlock();
                    logVerbose("Released visualization read lock");
                }
            });
        } catch (Exception e) {
            logError("Error in updateVisualization", e);
        }
    }
    
    /**
     * Extracts task and fire status information from a status message
     */
    private TaskInfo extractTaskInfo(String[] parts) {
        try {
            return timeExecution("extractTaskInfo", () -> {
                TaskInfo info = new TaskInfo();
                
                // Look for TASK: and FIRE_OUT: tags in the message
                for (String part : parts) {
                    if (part.startsWith("TASK:")) {
                        String[] taskParts = part.split(":");
                        if (taskParts.length >= 3) {
                            info.zoneId = Integer.parseInt(taskParts[1]);
                            info.severity = taskParts[2];
                            logVerbose("Extracted task info: Zone=" + info.zoneId + ", Severity=" + info.severity);
                        }
                    } else if (part.startsWith("FIRE_OUT:")) {
                        String[] fireOutParts = part.split(":");
                        if (fireOutParts.length >= 2) {
                            info.zoneId = Integer.parseInt(fireOutParts[1]);
                            info.isFireOut = true;
                            logVerbose("Extracted fire-out info: Zone=" + info.zoneId);
                        }
                    }
                }
                
                return info;
            });
        } catch (Exception e) {
            logError("Error extracting task info", e);
            return new TaskInfo();
        }
    }
    
    /**
     * Updates an existing drone with new status information
     */
    private void updateDroneStatus(DroneStatus status, String droneId, String state, 
                                Location location, TaskInfo taskInfo) {
        try {
            timeExecution("updateDroneStatus", () -> {
                logVerbose("Updating status for " + droneId + ": state=" + state + 
                          ", location=(" + location.getX() + "," + location.getY() + ")");
                
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
                logVerbose("DroneStatus updated successfully for " + droneId);
            });
        } catch (Exception e) {
            logError("Error updating drone status for " + droneId, e);
        }
    }

    /**
     * Check if there are pending fire events that need drones and assign this idle drone if needed
     */
    private void checkForPendingFireEvents(DroneStatus drone) {
        try {
            timeExecution("checkForPendingFireEvents", () -> {
                String droneId = drone.getDroneId();
                log("Checking for pending fire events for " + droneId);
                
                // First update all our maps to remove any extinguished fires
                cleanupExtinguishedFires();
                
                // Find the highest priority fire that needs more drones
                Integer highestPriorityZoneId = null;
                String highestPrioritySeverity = null;
                
                // Get a snapshot to avoid ConcurrentModificationException
                Map<Integer, Integer> requiredSnapshot = new HashMap<>(fireEventRequiredDrones);
                
                log("Checking " + requiredSnapshot.size() + " active fires for assignment");
                
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
                                log("Zone " + zoneId + " is marked as fully assigned, skipping");
                                continue;
                            }
                            
                            // Double-check the actual required number - it might have changed
                            int actualRequired = getDronesNeededForSeverity(zone.getSeverity());
                            
                            // Do an actual count of drones currently assigned to this zone
                            // This is more accurate than our tracking which might be out of sync
                            int actualAssignedCount = 0;
                            for (DroneStatus currentDrone : droneManager.getAllDrones()) {
                                FireEvent currentTask = currentDrone.getCurrentTask();
                                if (currentTask != null && currentTask.getZoneID() == zoneId &&
                                    !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                                    !currentDrone.getState().equalsIgnoreCase("Idle")) {
                                    actualAssignedCount++;
                                }
                            }
                            
                            // Skip if we already have enough drones based on actual counts
                            if (actualAssignedCount >= actualRequired) {
                                log("Zone " + zoneId + " already has enough drones assigned (actual count: " + 
                                    actualAssignedCount + "/" + actualRequired + "), skipping");
                                markZoneAsFullyAssigned(zoneId);
                                continue;
                            }
                            String severity = zone.getSeverity();
                            int currentWeight = highestPrioritySeverity != null ? 
                                getSeverityWeight(highestPrioritySeverity) : -1;
                            int newWeight = getSeverityWeight(severity);
                            
                            logVerbose("Zone " + zoneId + ": " + assigned + "/" + required + 
                                      " drones, severity=" + severity + " (weight=" + newWeight + ")");
                            
                            // If this is our first candidate or has higher severity
                            if (highestPriorityZoneId == null || newWeight > currentWeight) {
                                highestPriorityZoneId = zoneId;
                                highestPrioritySeverity = severity;
                                logVerbose("New highest priority candidate: Zone " + zoneId);
                            }
                        }
                    }
                }
                
                // If we found a fire that needs drones, dispatch this drone to it
                if (highestPriorityZoneId != null) {
                    final int zoneId = highestPriorityZoneId;
                    final String severity = highestPrioritySeverity;
                    
                    log("Selected Zone " + zoneId + " (" + severity + 
                        ") as assignment for " + droneId);
                    
                    // Create a FireEvent for this zone
                    FireEvent event = createFireEventForZone(zoneId, severity);
                    
                    // Update tracking atomically
                    Integer oldCount = fireEventAssignedDrones.get(zoneId);
                    Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                    fireEventAssignedDrones.put(zoneId, newCount);
                    
                    log("Updated drone count for Zone " + zoneId + ": " + 
                        (oldCount != null ? oldCount : 0) + " -> " + newCount);
                    
                    // Update drone status
                    droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                    
                    // Send event to drone
                    boolean sent = sendToDrone(event, droneId);
                    
                    if (sent) {
                        log("Successfully dispatched " + droneId + " to Zone " + zoneId);
                        droneAssignmentCount.incrementAndGet();
                    } else {
                        log("Failed to send assignment to " + droneId + " for Zone " + zoneId);
                        // Revert the count increment since assignment failed
                        fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                            count != null && count > 0 ? count - 1 : null);
                    }
                    
                    // Update visualization
                    updateVisualization();
                } else {
                    log("No suitable fire assignment found for " + droneId);
                }
            });
        } catch (Exception e) {
            logError("Error checking for pending fire events", e);
        }
    }
    
    /**
     * Removes any tracking for fires that are no longer active
     */
    private void cleanupExtinguishedFires() {
        try {
            timeExecution("cleanupExtinguishedFires", () -> {
                logVerbose("Cleaning up tracking for extinguished fires");
                
                // Get a snapshot of current zone IDs to avoid concurrent modification
                Set<Integer> zoneIds = new HashSet<>(fireEventRequiredDrones.keySet());
                
                if (zoneIds.isEmpty()) {
                    logVerbose("No fires to check for cleanup");
                    return;
                }
                
                int removedCount = 0;
                
                // Check each zone and remove if fire is extinguished
                for (Integer zoneId : zoneIds) {
                    Zone zone = droneManager.getZone(zoneId);
                    if (zone == null || !zone.hasFire()) {
                        log("Found extinguished fire in Zone " + zoneId + ", cleaning up tracking");
                        
                        // Remove from tracking maps
                        Integer requiredDrones = fireEventRequiredDrones.remove(zoneId);
                        Integer assignedDrones = fireEventAssignedDrones.remove(zoneId);
                        
                        // Remove from fully assigned zones set
                        clearZoneFullyAssignedMark(zoneId);
                        
                        log("Removed Zone " + zoneId + " from tracking (was " + 
                            assignedDrones + "/" + requiredDrones + " drones)");
                        
                        // Also remove from event queue if present
                        int eventsRemoved = removeFromEventQueue(zoneId);
                        if (eventsRemoved > 0) {
                            log("Removed " + eventsRemoved + " queued events for Zone " + zoneId);
                        }
                        
                        removedCount++;
                    }
                }
                
                if (removedCount > 0) {
                    log("Cleanup complete - removed " + removedCount + " extinguished fires");
                } else {
                    logVerbose("No extinguished fires found during cleanup");
                }
            });
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
            return timeExecution("removeFromEventQueue", () -> {
                logVerbose("Removing events for Zone " + zoneId + " from event queue");
                
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
                        logVerbose("Removing queued event: " + event);
                    }
                }
                
                // Add back events we want to keep
                if (!eventsToKeep.isEmpty()) {
                    logVerbose("Re-adding " + eventsToKeep.size() + " events back to queue");
                    eventQueue.addAll(eventsToKeep);
                }
                
                return removedCount;
            });
        } catch (Exception e) {
            logError("Error removing events from queue", e);
            return 0;
        }
    }
    
    /**
     * Creates a FireEvent object for a specified zone
     */
    private FireEvent createFireEventForZone(int zoneId, String severity) {
        try {
            return timeExecution("createFireEventForZone", () -> {
                // Generate a timestamp
                String timestamp = String.format("%d", System.currentTimeMillis());
                
                log("Creating new FireEvent for Zone " + zoneId + 
                    ", Severity=" + severity + ", Time=" + timestamp);
                
                // Create a new fire event
                FireEvent event = new FireEvent(timestamp, zoneId, "FIRE", severity, "NONE");
                return event;
            });
        } catch (Exception e) {
            logError("Error creating fire event", e);
            // Fallback to basic creation without timing
            String timestamp = String.format("%d", System.currentTimeMillis());
            return new FireEvent(timestamp, zoneId, "FIRE", severity, "NONE");
        }
    }

    /**
     * Sends a UDP packet to the designated IP and port
     * @param fire the fire event to send
     * @param port the port to send to
     * @return true if sending was successful, false otherwise
     */
    public boolean send(FireEvent fire, int port) {
        try {
            return timeExecution("send", () -> {
                String message = fire.toString();
                byte[] msg = message.getBytes();
                log("Sending message to port " + port + ": " + message);
                
                try {
                    DatagramPacket sendPacket = new DatagramPacket(msg, msg.length, 
                                                                  InetAddress.getLocalHost(), port);
                    sendSocket.send(sendPacket);
                    fireSentCount.incrementAndGet();
                    logVerbose("Message sent successfully, " + msg.length + " bytes");
                    return true;
                } catch (IOException e) {
                    logError("Error sending packet to port " + port, e);
                    return false;
                }
            });
        } catch (Exception e) {
            logError("Critical error in send method", e);
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
            return timeExecution("sendToDrone", () -> {
                log("Preparing to send fire event to " + droneId);
                
                // Assign this drone to the fire event
                fire.assignDrone(droneId);
                
                // Get the port for this drone
                Integer port = dronePorts.get(droneId);
                if (port == null) {
                    log("Error: No port registration found for " + droneId);
                    return false;
                }
                
                log("Sending fire event to " + droneId + " on port " + port + 
                   " for Zone " + fire.getZoneID());
                
                boolean success = send(fire, port);
                
                if (success) {
                    log("Successfully sent fire event to " + droneId);
                } else {
                    log("Failed to send fire event to " + droneId);
                }
                
                return success;
            });
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
            return timeExecution("getDronesNeededForSeverity", () -> {
                int dronesNeeded;
                
                switch (severity.toLowerCase()) {
                    case "high":
                        dronesNeeded = 3; // 30L total capacity needed - 3 drones with 10L each
                        break;
                    case "moderate":
                        dronesNeeded = 2; // 20L total capacity needed - 2 drones with 10L each
                        break;
                    case "low":
                    default:
                        dronesNeeded = 1; // 10L total capacity needed - 1 drone with 10L
                        break;
                }
                
                logVerbose("Severity " + severity + " requires " + dronesNeeded + " drones");
                return dronesNeeded;
            });
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
            timeExecution("processNextFireEvent", () -> {
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
                log("Processing fire event: Zone=" + zoneId + ", Severity=" + severity + 
                    ", Time=" + event.getTime());
                
                Zone zone = droneManager.getZone(zoneId);
                
                // Create zone if it doesn't exist yet
                if (zone == null) {
                    Location zoneLocation = new Location(
                        ((zoneId-1) % 3) * 700 + 350,  // 700m wide zones, centered at x+350
                        ((zoneId-1) / 3) * 600 + 300); // 600m tall zones, centered at y+300
                    
                    zone = droneManager.createZone(zoneId, zoneLocation);
                    log("Created new Zone " + zoneId + " at location (" + 
                        zoneLocation.getX() + "," + zoneLocation.getY() + ")");
                } else {
                    log("Found existing Zone " + zoneId + " at location (" + 
                        zone.getLocation().getX() + "," + zone.getLocation().getY() + ")");
                }
                
                // Check if zone already has fire of same or higher severity
                boolean updateZoneStatus = true;
                if (zone.hasFire()) {
                    int currentSeverityWeight = getSeverityWeight(zone.getSeverity());
                    int newSeverityWeight = getSeverityWeight(severity);
                    
                    if (currentSeverityWeight >= newSeverityWeight) {
                        log("Zone " + zoneId + " already has a " + zone.getSeverity() + 
                            " severity fire, ignoring " + severity + " event");
                        updateZoneStatus = false;
                    } else {
                        log("Upgrading Zone " + zoneId + " fire severity: " + 
                            zone.getSeverity() + " -> " + severity);
                    }
                } else {
                    log("Setting Zone " + zoneId + " on fire with severity " + severity);
                }
                
                // Update zone fire status if needed
                if (updateZoneStatus) {
                    droneManager.updateZoneFireStatus(zoneId, true, severity);
                    
                    // Determine drones needed and track requirements
                    int dronesNeeded = getDronesNeededForSeverity(severity);
                    int currentAssigned = fireEventAssignedDrones.getOrDefault(zoneId, 0);
                    int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                    
                    if (currentRequired < dronesNeeded) {
                        // Update required drones if new severity needs more
                        log("Updating drone requirement for Zone " + zoneId + ": " + 
                            currentRequired + " -> " + dronesNeeded);
                        fireEventRequiredDrones.put(zoneId, dronesNeeded);
                    }
                    
                    fireEventAssignedDrones.putIfAbsent(zoneId, 0);
                    log("Zone " + zoneId + " has " + currentAssigned + "/" + 
                        dronesNeeded + " drones assigned");
                }
                
                // Update visualization with fire before dispatching drones
                log("Updating visualization to show fire in Zone " + zoneId);
                updateVisualization();
                
                // Find and dispatch available drones only if we need more
                if (updateZoneStatus) {
                    dispatchDronesToFire(event, fireEventRequiredDrones.get(zoneId));
                }
                
                // After processing this event, check other active fires that might need drones
                checkActiveFiresForDroneAssignment();
            });
        } catch (Exception e) {
            logError("Error processing fire event", e);
        }
    }
    
    /**
     * Checks all active fires and assigns available drones to them based on priority
     */
    private void checkActiveFiresForDroneAssignment() {
        try {
            timeExecution("checkActiveFiresForDroneAssignment", () -> {
                log("Checking all active fires for drone assignments");
                
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
                    logVerbose("No active fires to check");
                    return;
                }
                
                log("Found " + activeFireZones.size() + " active fires");
                
                // Get available drones
                Collection<DroneStatus> allDrones = droneManager.getAllDrones();
                List<DroneStatus> availableDrones = allDrones.stream()
                    .filter(DroneStatus::isAvailable)
                    .collect(Collectors.toList());
                
                if (availableDrones.isEmpty()) {
                    log("No available drones to assign");
                    return;
                }
                
                log("Found " + availableDrones.size() + " available drones");
                
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
                    
                    // Do an actual count of drones currently assigned to this zone
                    // This is more accurate than our tracking which might be out of sync
                    int actualAssignedCount = 0;
                    for (DroneStatus currentDrone : allDrones) {
                        FireEvent currentTask = currentDrone.getCurrentTask();
                        if (currentTask != null && currentTask.getZoneID() == zoneId && 
                            !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                            !currentDrone.getState().equalsIgnoreCase("Idle")) {
                            actualAssignedCount++;
                        }
                    }
                    
                    // Update our tracking to match actual count if different
                    if (actualAssignedCount != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                        log("Updating drone count for Zone " + zoneId + " to match actual count: " + 
                            fireEventAssignedDrones.getOrDefault(zoneId, 0) + " -> " + actualAssignedCount);
                        fireEventAssignedDrones.put(zoneId, actualAssignedCount);
                    }
                    
                    int assignedDrones = actualAssignedCount;
                    int neededDrones = requiredDrones - assignedDrones;
                    
                    // Check if this zone is already fully assigned or has enough drones
                    if (isZoneFullyAssigned(zoneId) || assignedDrones >= requiredDrones) {
                        markZoneAsFullyAssigned(zoneId);
                        log("Zone " + zoneId + " has sufficient drones or is already marked as fully assigned, skipping");
                        continue;
                    }
                    
                    log("Zone " + zoneId + " (" + severity + ") has " + assignedDrones + "/" + 
                        requiredDrones + " drones assigned, needs " + neededDrones + " more");
                    
                    if (neededDrones <= 0) {
                        log("Zone " + zoneId + " has enough drones assigned, skipping");
                        markZoneAsFullyAssigned(zoneId);
                        continue;
                    }
                    
                    if (availableDrones.isEmpty()) {
                        log("No more available drones for assignment");
                        break;
                    }
                    
                    // Create event for the zone
                    FireEvent event = createFireEventForZone(zoneId, severity);
                    
                    // Dispatch drones to this fire
                    log("Dispatching drones to Zone " + zoneId);
                    int dispatched = 0;
                    Set<String> assignedDroneIds = new HashSet<>();
                    
                    for (int i = 0; i < Math.min(neededDrones, availableDrones.size()); i++) {
                        DroneStatus drone = availableDrones.get(i);
                        String droneId = drone.getDroneId();
                        
                        // Assign drone to fire
                        log("Assigning " + droneId + " to Zone " + zoneId);
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
                            log("Failed to send assignment to " + droneId);
                            droneManager.updateDroneStatus(droneId, "Idle", drone.getCurrentLocation(), null);
                            fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                        }
                    }
                    
                    // Remove assigned drones from available list
                    availableDrones.removeIf(drone -> assignedDroneIds.contains(drone.getDroneId()));
                    
                    log("Dispatched " + dispatched + " drones to Zone " + zoneId);
                    
                    // Update visualization after drone assignments
                    if (dispatched > 0) {
                        updateVisualization();
                    }
                }
            });
        } catch (Exception e) {
            logError("Error checking active fires for drone assignment", e);
        }
    }
    
    /**
     * Dispatches the required number of drones to a fire event
     */
    private void dispatchDronesToFire(FireEvent event, int requestedDrones) {
        try {
            timeExecution("dispatchDronesToFire", () -> {
                int zoneId = event.getZoneID();
                Set<String> assignedDroneIds = new HashSet<>();
                
                // Do an actual count of drones currently assigned to this zone
                // This is more accurate than our tracking which might be out of sync
                int currentlyAssigned = 0;
                for (DroneStatus currentDrone : droneManager.getAllDrones()) {
                    FireEvent currentTask = currentDrone.getCurrentTask();
                    if (currentTask != null && currentTask.getZoneID() == zoneId && 
                        !currentDrone.getState().equalsIgnoreCase("IDLE") &&
                        !currentDrone.getState().equalsIgnoreCase("Idle")) {
                        currentlyAssigned++;
                        assignedDroneIds.add(currentDrone.getDroneId()); // Track already assigned drones
                    }
                }
                
                // Update our tracking to match actual count if different
                if (currentlyAssigned != fireEventAssignedDrones.getOrDefault(zoneId, 0)) {
                    log("Updating drone count for Zone " + zoneId + " to match actual count: " + 
                        fireEventAssignedDrones.getOrDefault(zoneId, 0) + " -> " + currentlyAssigned);
                    fireEventAssignedDrones.put(zoneId, currentlyAssigned);
                }
                
                // Check if zone is already fully assigned
                if (isZoneFullyAssigned(zoneId)) {
                    log("Zone " + zoneId + " is marked as fully assigned, skipping dispatch");
                    return;
                }
                
                // Determine the actual number of drones needed
                int actualDronesNeeded = requestedDrones;
                
                // Get the zone to check its current severity
                Zone zone = droneManager.getZone(zoneId);
                if (zone != null && zone.hasFire()) {
                    // Get the actual number of drones needed based on current severity
                    int severityBasedDrones = getDronesNeededForSeverity(zone.getSeverity());
                    // Use the minimum of requested and actual drones needed
                    actualDronesNeeded = Math.min(requestedDrones, severityBasedDrones);
                }
                
                int remainingNeeded = actualDronesNeeded - currentlyAssigned;
                
                // Mark as fully assigned if we already have enough drones
                if (currentlyAssigned >= actualDronesNeeded) {
                    markZoneAsFullyAssigned(zoneId);
                }
                
                log("Zone " + zoneId + " needs " + remainingNeeded + " more drones " +
                    "(" + currentlyAssigned + "/" + actualDronesNeeded + " currently assigned)");
                
                if (remainingNeeded <= 0) {
                    log("Already have enough drones assigned to Zone " + zoneId +
                        " (" + currentlyAssigned + "/" + actualDronesNeeded + ")");
                    markZoneAsFullyAssigned(zoneId);
                    return;
                }
                
                int successfulDispatches = 0;
                
                // Find drones to dispatch
                for (int i = 0; i < remainingNeeded; i++) {
                    log("Looking for drone #" + (i+1) + " of " + remainingNeeded + " needed for Zone " + zoneId);
                    DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
                    
                    if (drone != null) {
                        // Process this drone
                        String droneId = drone.getDroneId();
                        assignedDroneIds.add(droneId);
                        
                        log("Found available drone: " + droneId + " for Zone " + zoneId);
                        
                        // Update tracking count
                        Integer oldCount = fireEventAssignedDrones.get(zoneId);
                        Integer newCount = (oldCount == null) ? 1 : oldCount + 1;
                        fireEventAssignedDrones.put(zoneId, newCount);
                        
                        log("Updated drone count for Zone " + zoneId + ": " + 
                            (oldCount != null ? oldCount : 0) + " -> " + newCount);
                        
                        // Update drone status and send event
                        droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                        
                        boolean sent = sendToDrone(event, droneId);
                        if (sent) {
                            successfulDispatches++;
                            droneAssignmentCount.incrementAndGet();
                        } else {
                            // Revert tracking count if send failed
                            log("Reverting assignment count for Zone " + zoneId + " due to send failure");
                            fireEventAssignedDrones.compute(zoneId, (id, count) -> 
                                count != null && count > 0 ? count - 1 : null);
                        }
                        
                        // Small delay between dispatches to prevent network congestion
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log("Dispatch process interrupted");
                            break;
                        }
                    } else {
                        log("No more available drones found for Zone " + zoneId);
                        break;
                    }
                }
                
                log("Dispatch complete: assigned " + successfulDispatches + 
                    " drones to Zone " + zoneId);
                
                // Update visualization after dispatching
                if (successfulDispatches > 0) {
                    updateVisualization();
                }
            });
        } catch (Exception e) {
            logError("Error dispatching drones to fire", e);
        }
    }
    
    /**
     * Finds an available drone for a fire event, avoiding duplicates
     */
    private DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds) {
        try {
            return timeExecution("findAvailableDrone", () -> {
                logVerbose("Looking for available drone for Zone " + event.getZoneID());
                
                Collection<DroneStatus> allDrones = droneManager.getAllDrones();
                logVerbose("Total drones registered: " + allDrones.size());
                
                // Count available drones (for logging)
                long availableDroneCount = allDrones.stream()
                    .filter(DroneStatus::isAvailable)
                    .filter(d -> !assignedDroneIds.contains(d.getDroneId()))
                    .count();
                
                if (availableDroneCount == 0) {
                    logVerbose("No available drones found");
                    return null;
                }
                
                logVerbose("Available drones: " + availableDroneCount);
                
                // Use DroneManager to select best drone
                DroneStatus selectedDrone = droneManager.selectBestDroneForEvent(event);
                
                if (selectedDrone == null) {
                    logVerbose("DroneManager found no suitable drone");
                    return null;
                }
                
                String droneId = selectedDrone.getDroneId();
                
                // Check if drone is already in the assigned set
                if (assignedDroneIds.contains(droneId)) {
                    logVerbose("Selected drone " + droneId + " is already assigned, skipping");
                    return null;
                }
                
                // Check if drone is still available (could have changed state concurrently)
                if (!selectedDrone.isAvailable()) {
                    logVerbose("Selected drone " + droneId + " is no longer available, state=" + 
                              selectedDrone.getState());
                    return null;
                }
                
                logVerbose("Selected drone " + droneId + " for Zone " + event.getZoneID() + 
                          ", location=(" + selectedDrone.getCurrentLocation().getX() + 
                          "," + selectedDrone.getCurrentLocation().getY() + ")");
                return selectedDrone;
            });
        } catch (Exception e) {
            logError("Error finding available drone", e);
            return null;
        }
    }

    /**
     * Thread function for receiving messages
     */
    private void receiveMessages() {
        try {
            // Initial delay to allow drones to register
            Thread.sleep(2000);
            
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
                            timeExecution("processIncomingFireEvent", () -> {
                                
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
                                    log("Created new Zone " + zoneId + " at location (" + 
                                        zoneLocation.getX() + "," + zoneLocation.getY() + ")");
                                }
                                
                                // Check if zone already has a higher severity fire
                                boolean updateZoneStatus = true;
                                if (zone.hasFire()) {
                                    int currentSeverityWeight = getSeverityWeight(zone.getSeverity());
                                    int newSeverityWeight = getSeverityWeight(severity);
                                    
                                    if (currentSeverityWeight >= newSeverityWeight) {
                                        log("Zone " + zoneId + " already has a " + zone.getSeverity() + 
                                            " severity fire, keeping current severity");
                                        updateZoneStatus = false;
                                    } else {
                                        log("Upgrading Zone " + zoneId + " fire severity: " + 
                                            zone.getSeverity() + " -> " + severity);
                                    }
                                } else {
                                    log("Setting Zone " + zoneId + " on fire with severity " + severity);
                                }
                                
                                // Update zone fire status
                                if (updateZoneStatus) {
                                    droneManager.updateZoneFireStatus(zoneId, true, severity);
                                }
                                
                                // Add to priority queue 
                                eventQueue.add(finalEvent);
                                log("Added event to priority queue, queue size now: " + eventQueue.size());
                                
                                // Track required drones based on severity
                                int dronesNeeded = getDronesNeededForSeverity(severity);
                                int currentRequired = fireEventRequiredDrones.getOrDefault(zoneId, 0);
                                
                                // Only update if new requirement is higher
                                if (currentRequired < dronesNeeded) {
                                    log("Updating drone requirement for Zone " + zoneId + ": " + 
                                        currentRequired + " -> " + dronesNeeded);
                                    fireEventRequiredDrones.put(zoneId, dronesNeeded);
                                }
                                
                                // Initialize assigned count if not present
                                fireEventAssignedDrones.putIfAbsent(zoneId, 0);
                                
                                // Send acknowledgement to FireIncidentSubsystem
                                boolean sent = send(finalEvent, 5001);
                                if (sent) {
                                    log("Sent acknowledgement to FireIncidentSubsystem");
                                } else {
                                    log("Failed to send acknowledgement to FireIncidentSubsystem");
                                }
                                
                                // Update visualization
                                updateVisualization();
                            });
                        } catch (Exception e) {
                            logError("Error processing incoming fire event", e);
                        }
                    });
                }
                
                // Small pause to prevent tight loop
                Thread.yield();
            }
        } catch (InterruptedException e) {
            log("Receive thread interrupted, shutting down");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (isRunning.get()) {
                logError("Error in receive thread", e);
            }
        }
        
        log("Receive thread terminating");
    }

    /**
     * Thread function for processing events and assigning to drones
     */
    private void processEvents() {
        try {
            log("Process thread starting");
            
            // Run periodic cleanup
            scheduledExecutor.scheduleAtFixedRate(() -> {
                try {
                    log("Running periodic cleanup of extinguished fires");
                    cleanupExtinguishedFires();
                } catch (Exception e) {
                    logError("Error in periodic cleanup", e);
                }
            }, 5, 15, TimeUnit.SECONDS);
            
            // Run periodic drone assignment check to ensure fires get drones
            scheduledExecutor.scheduleAtFixedRate(() -> {
                try {
                    log("Running proactive drone assignment check");
                    if (eventQueue.isEmpty()) {
                        checkActiveFiresForDroneAssignment();
                    }
                } catch (Exception e) {
                    logError("Error in periodic drone assignment", e);
                }
            }, 3, 3, TimeUnit.SECONDS);
            
            // Main processing loop
            while (isRunning.get()) {
                // Log queue size periodically for debug
                if (eventQueue.size() > 0) {
                    logVerbose("Event queue size: " + eventQueue.size());
                }
                
                if (!eventQueue.isEmpty()) {
                    log("Processing next fire event from queue");
                    // Process fire events and assign drones
                    processNextFireEvent();
                }

                // Brief pause between processing cycles
                Thread.sleep(250);
            }
        } catch (InterruptedException e) {
            log("Process thread interrupted, shutting down");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (isRunning.get()) {
                logError("Error in process thread", e);
            }
        }
        
        log("Process thread terminating");
    }
    
    /**
     * Shuts down the scheduler and releases resources
     */
    public void shutdown() {
        try {
            log("Shutting down Scheduler");
            isRunning.set(false);
            
            // Log final statistics before shutdown
            logSystemState();
            
            // Shutdown thread pools
            log("Shutting down thread pools");
            scheduledExecutor.shutdownNow();
            workerExecutor.shutdownNow();
            
            try {
                // Wait briefly for tasks to complete
                boolean scheduledTerminated = scheduledExecutor.awaitTermination(1, TimeUnit.SECONDS);
                boolean workerTerminated = workerExecutor.awaitTermination(1, TimeUnit.SECONDS);
                log("Thread pools terminated: scheduled=" + scheduledTerminated + 
                    ", worker=" + workerTerminated);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Interrupted while waiting for thread pools to terminate");
            }
            
            // Close sockets
            log("Closing network sockets");
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.close();
                log("Send socket closed");
            }
            
            if (receiveSocket != null && !receiveSocket.isClosed()) {
                receiveSocket.close();
                log("Receive socket closed");
            }
            
            log("Scheduler shutdown complete");
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
            scheduler = new Scheduler(ip) {
                // Override all log methods to do nothing
                @Override
                protected void log(String message) {}
                @Override
                protected void logVerbose(String message) {}
                @Override
                protected void logError(String message, Throwable e) {}
                @Override
                protected void logSystemState() {}
                @Override
                protected void logDroneStatuses() {}
                @Override
                protected void logActiveFiresDetail() {}
            };
            
            // Start processing threads
            Thread receiveThread = new Thread(scheduler::receiveMessages);
            Thread processThread = new Thread(scheduler::processEvents);
            
            receiveThread.setName("Scheduler-Receive");
            processThread.setName("Scheduler-Process");
            
            receiveThread.setDaemon(false); // Ensure JVM doesn't exit while threads are running
            processThread.setDaemon(false);
            
            receiveThread.start();
            processThread.start();
            
            // Register shutdown hook for clean termination
            final Scheduler finalScheduler = scheduler;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                finalScheduler.shutdown();
            }));
            
            // Wait for threads to complete (they normally won't)
            receiveThread.join();
            processThread.join();
            
        } catch (UnknownHostException e) {
            // Handle silently
        } catch (SocketException e) {
            // Handle silently
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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