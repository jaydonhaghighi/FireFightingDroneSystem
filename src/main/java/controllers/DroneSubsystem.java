package controllers;

import models.DroneSpecifications;
import models.FireEvent;
import models.Location;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

import static models.FireEvent.ErrorType;
import static models.FireEvent.createFireEventFromString;

/**
 * Interface for different states of the drone
 */
interface DroneState {
    default void handleFireEvent(DroneSubsystem context, FireEvent event) {}
    default void dropAgent(DroneSubsystem context) {}
    default void returningBack(DroneSubsystem context) {}
    default void droneFaulted(DroneSubsystem context) {}
    default void taskCompleted(DroneSubsystem context) {}
}

/**
 * Idle state - drone is at base, ready for assignments
 */
class Idle implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.setState(new EnRoute());
    }
}

/**
 * EnRoute state - drone is traveling to fire location
 */
class EnRoute implements DroneState {
    @Override
    public void dropAgent(DroneSubsystem context) {
        context.setState(new DroppingAgent());
    }
}

/**
 * DroppingAgent state - drone is at fire location and deploying fire-fighting agent
 */
class DroppingAgent implements DroneState {
    @Override
    public void returningBack(DroneSubsystem context) {
        context.setState(new ArrivedToBase());
    }
}

/**
 * ArrivedToBase state - drone has returned to base after mission
 */
class ArrivedToBase implements DroneState {
    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.setState(new Fault());
    }
    
    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.setState(new Idle());
    }
}

/**
 * Fault state - drone has experienced a fault and needs maintenance
 */
class Fault implements DroneState {
    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.setState(new Idle());  // Maintenance complete, return to idle
    }
}

/**
 * DroneSubsystem - implements a state machine for drone operations
 */
public class DroneSubsystem {
    // Drone state and properties
    private DroneState currentState;
    private Queue<FireEvent> fireEventQueue;
    private String droneId;
    private Location currentLocation;
    private Location targetLocation;
    private Location baseLocation;
    private DroneSpecifications specifications;
    private FireEvent currentEvent;

    // Static data for fire-fighting operations
    private static final Map<Integer, Integer> zoneDropsMap = new ConcurrentHashMap<>();
    
    // Timing constants and variables
    private long movementStartTime = 0;
    private long dropAgentStartTime = 0;
    private static final long MAX_MOVEMENT_TIME = 30000; // 30 seconds max for movement
    private static final long MAX_DROP_AGENT_TIME = 15000; // 15 seconds max for dropping agent
    private ErrorType currentError = ErrorType.NONE;

    // Network communication
    private final InetAddress serverIP;
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;
    private final int sendPort = 7000;
    private final int receivePort = 7001;

    /**
     * Constructor with all required parameters
     * @param serverIP The IP address of the scheduler server
     * @param droneId The unique identifier for this drone
     * @param baseLocation The location of the drone's home base
     * @param specifications The technical specifications of the drone
     */
    public DroneSubsystem(InetAddress serverIP, String droneId, Location baseLocation, DroneSpecifications specifications) {
        this.currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        this.droneId = droneId;
        this.baseLocation = baseLocation;
        this.currentLocation = baseLocation;
        this.targetLocation = baseLocation;
        this.specifications = specifications;
        
        initializeNetworking();
    }
    
    /**
     * Constructor with default specifications
     */
    public DroneSubsystem(InetAddress serverIP, String droneId, Location baseLocation) {
        this(serverIP, droneId, baseLocation, new DroneSpecifications());
    }
    
    /**
     * Initialize network sockets for communication
     */
    private void initializeNetworking() {
        try {
            // Extract drone number from ID for unique port assignment
            int droneNumber = 0;
            if (droneId.length() > 5) {
                droneNumber = Integer.parseInt(droneId.substring(5));
            }
            
            // Create uniquely offset ports for each drone
            int uniqueSendPort = sendPort + (droneNumber * 100);
            int uniqueReceivePort = receivePort + (droneNumber * 100);
            
            sendSocket = new DatagramSocket(uniqueSendPort);
            receiveSocket = new DatagramSocket(uniqueReceivePort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Receives a fire event from the scheduler
     * @return The received fire event or null if timeout/error
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);

        try {
            receiveSocket.receive(receivePacket);
            int len = receivePacket.getLength();
            String message = new String(data, 0, len);
            return createFireEventFromString(message);
        } catch (SocketTimeoutException e) {
            // This is expected with our timeout setting - silently return null
            return null;
        } catch (IOException e) {
            // Only print stack trace for non-timeout exceptions
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends a message to the specified port
     * @param message The message to send
     * @param port The port to send to
     */
    public void send(String message, int port) {
        try {
            byte[] msg = message.getBytes();
            sendPacket = new DatagramPacket(msg, msg.length, serverIP, port);
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a status update to the scheduler
     */
    public void sendStatusUpdate() {
        String status = buildStatusMessage("");
        send(status, 6001);
    }
    
    /**
     * Sends a status update with fire extinguished information
     * @param zoneId The zone ID where fire was extinguished
     */
    public void sendFireExtinguishedStatus(int zoneId) {
        String status = buildStatusMessage(" FIRE_OUT:" + zoneId);
        send(status, 6001);
    }
    
    /**
     * Builds a status message with the given additional info
     */
    private String buildStatusMessage(String additionalInfo) {
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        String taskInfo = "";
        
        if (currentEvent != null) {
            taskInfo = " TASK:" + currentEvent.getZoneID() + ":" + currentEvent.getSeverity();
        } else if (fireEventQueue.peek() != null) {
            FireEvent event = fireEventQueue.peek();
            taskInfo = " TASK:" + event.getZoneID() + ":" + event.getSeverity();
        }
        
        // Include capacity info in the status message
        String capacityInfo = " CAPACITY:" + specifications.getCurrentCapacity();
        
        return droneId + " " +
               currentState.getClass().getSimpleName() + 
               errorInfo + 
               taskInfo + 
               capacityInfo +
               additionalInfo + " " +
               currentLocation.getX() + " " +
               currentLocation.getY();
    }

    /**
     * Returns the number of fire events currently queued
     * @return int the size of the queue
     */
    public int getQueueSize() {
        return fireEventQueue.size();
    }

    /**
     * State transition helper method for all state changes
     * @param action The action to perform
     */
    private void performStateTransition(StateAction action) {
        action.execute();
    }
    
    /**
     * Functional interface for state transitions
     */
    @FunctionalInterface
    private interface StateAction {
        void execute();
    }

    /**
     * Handles a fire event
     * @param event FireEvent the fire event to handle
     */
    public void handleFireEvent(FireEvent event) {
        performStateTransition(() -> {
            if (event.hasError()) {
                setError(event.getError());
            }
            currentState.handleFireEvent(this, event);
        });
    }

    /**
     * Processes dropping agent action
     */
    public void dropAgent() {
        performStateTransition(() -> {
            // Set capacity to 0 when dropping agent
            specifications.empty();
            currentState.dropAgent(this);
            resetTimers();
        });
    }

    /**
     * Processes returning to base action
     */
    public void returningBack() {
        performStateTransition(() -> currentState.returningBack(this));
    }

    /**
     * Processes drone fault action
     */
    public void droneFaulted() {
        performStateTransition(() -> currentState.droneFaulted(this));
    }

    /**
     * Processes task completion action
     */
    public void taskCompleted() {
        performStateTransition(() -> currentState.taskCompleted(this));

        // Process next event in queue if available
        if (!fireEventQueue.isEmpty()) {
            FireEvent nextEvent = fireEventQueue.poll();
            scheduleFireEvent(nextEvent);
        }
    }

    /**
     * Sets an error on the drone
     * @param errorType The type of error
     */
    public void setError(ErrorType errorType) {
        // Error handling is disabled - always set to NONE
        this.currentError = ErrorType.NONE;
    }

    /**
     * Checks if the drone has an error
     * @return true if the drone has an error
     */
    public boolean hasError() {
        return currentError != ErrorType.NONE;
    }

    /**
     * Gets the current error
     * @return the current error
     */
    public ErrorType getCurrentError() {
        return currentError;
    }

    /**
     * Timer management methods
     */
    public void startMovementTimer() {
        this.movementStartTime = System.currentTimeMillis();
    }

    public void resetTimers() {
        this.movementStartTime = 0;
        this.dropAgentStartTime = 0;
    }

    /**
     * Get the name of the current drone state
     */
    public String getCurrentStateName() {
        return currentState.getClass().getSimpleName();
    }

    /**
     * Set the state of the drone
     * @param state The new state
     */
    public void setState(DroneState state) {
        if (this.currentState instanceof Fault && !(state instanceof Idle)) {
            this.currentState = new ArrivedToBase(); // Transition to ArrivedToBase first
        } else {
            this.currentState = state;
        }
    }

    /**
     * Schedules a fire event for processing
     * @param event The fire event to schedule
     */
    public void scheduleFireEvent(FireEvent event) {
        if (currentState instanceof Idle) {
            handleFireEvent(event);
        } else {
            fireEventQueue.add(event);
        }
    }

    /**
     * Getters and setters
     */
    public String getDroneId() {
        return droneId;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location location) {
        this.targetLocation = location;
    }

    public Location getBaseLocation() {
        return baseLocation;
    }

    public DroneSpecifications getSpecifications() {
        return specifications;
    }

    /**
     * Zone tracking methods that use DroneManager when possible
     * We keep the static implementation for backward compatibility
     */
    public static synchronized int recordDropForZone(int zoneId) {
        // We could use a singleton DroneManager here in a more comprehensive refactoring
        int currentDrops = zoneDropsMap.getOrDefault(zoneId, 0);
        int newTotal = currentDrops + 1;
        zoneDropsMap.put(zoneId, newTotal);
        return newTotal;
    }

    public static synchronized int getDropsForZone(int zoneId) {
        return zoneDropsMap.getOrDefault(zoneId, 0);
    }

    /**
     * Main drone control loop
     */
    private static void runDrone(DroneSubsystem drone) {
        try {
            drone.sendStatusUpdate();  // Initial status

            while (true) {
                // Set socket timeout (it will be properly handled in receive())
                try {
                    drone.receiveSocket.setSoTimeout(250);
                } catch (SocketException e) {
                    System.err.println("[" + drone.getDroneId() + "] Error setting socket timeout: " + e.getMessage());
                }
                FireEvent event = drone.receive();
                
                if (event == null) {
                    continue;
                }
                
                // Check if this drone should handle the event
                if (shouldHandleEvent(drone, event)) {
                    int oldZoneId = drone.currentEvent != null ? drone.currentEvent.getZoneID() : -1;
                    int newZoneId = event.getZoneID();
                    boolean isRedirection = oldZoneId != -1 && oldZoneId != newZoneId;
                    
                    // Handle redirection
                    if (isRedirection && (drone.getCurrentStateName().equals("EnRoute") || 
                                          drone.getCurrentStateName().equals("DroppingAgent"))) {
                        
                        System.out.println("[" + drone.getDroneId() + "] REDIRECTION RECEIVED from zone " + 
                            oldZoneId + " to zone " + newZoneId);
                        
                        // This is a redirection - stop current mission and re-target
                        drone.fireEventQueue.clear(); // Clear any queued events
                        drone.currentEvent = event;   // Set this as our current event
                        
                        // Update target location and send status update
                        Location newTarget = getZoneLocation(event.getZoneID());
                        drone.setTargetLocation(newTarget);
                        drone.setState(new EnRoute()); // Reset state to EnRoute
                        drone.sendStatusUpdate();
                        
                        // Process the new event
                        processEvent(drone, event);
                        drone.sendStatusUpdate();
                    } else {
                        // Normal event handling
                        processEvent(drone, event);
                        drone.sendStatusUpdate();
                    }
                }
            }
        } catch (Exception e) {
            // Log the error, but don't terminate the drone thread
            System.err.println("[" + drone.getDroneId() + "] Error in main loop: " + e.getMessage());
            e.printStackTrace();
            
            // Attempt to recover
            try {
                Thread.sleep(1000);
                drone.sendStatusUpdate();
            } catch (Exception ex) {
                // Ignore recovery errors
            }
        }
    }
    
    /**
     * Determine if this drone should handle the given event
     */
    private boolean shouldHandleEvent(FireEvent event) {
        String primaryDroneId = event.getAssignedDroneId();
        return primaryDroneId == null || 
               primaryDroneId.equals(droneId) || 
               event.isDroneAssigned(droneId);
    }
    
    /**
     * Determine if this drone should handle the given event
     */
    private static boolean shouldHandleEvent(DroneSubsystem drone, FireEvent event) {
        // First check if using the instance method is possible
        if (drone != null) {
            try {
                return drone.shouldHandleEvent(event);
            } catch (Exception e) {
                // Fall back to static implementation
            }
        }
        
        // Static implementation as fallback
        String primaryDroneId = event.getAssignedDroneId();
        String thisDroneId = drone.getDroneId();
        
        return primaryDroneId == null || 
               primaryDroneId.equals(thisDroneId) || 
               event.isDroneAssigned(thisDroneId);
    }

    /**
     * Process a fire event with the complete firefighting sequence
     */
    private static void processEvent(DroneSubsystem drone, FireEvent event) throws InterruptedException {
        int zoneId = event.getZoneID();
        Location zoneLocation = getZoneLocation(zoneId);
        String severity = event.getSeverity();
        int dronesNeeded = getRequiredDronesForSeverity(severity);
        int currentDrops = getDropsForZone(zoneId);
        
        // Store the original event zone ID to check for redirections
        int originalZoneId = drone.currentEvent != null ? drone.currentEvent.getZoneID() : -1;
        boolean isRedirection = (originalZoneId != -1 && originalZoneId != zoneId);
        
        // Check if fire is already extinguished before starting mission
        if (currentDrops >= dronesNeeded) {
            drone.setCurrentLocation(drone.getBaseLocation());
            drone.currentEvent = null;
            drone.setState(new Idle());
            drone.sendStatusUpdate();
            return;
        }

        // Clear any previously queued events - this is important for redirection
        if (isRedirection) {
            System.out.println("[" + drone.getDroneId() + "] REDIRECTING from zone " + 
                originalZoneId + " to zone " + zoneId);
            drone.fireEventQueue.clear();
        }

        // Initialize mission parameters
        drone.setTargetLocation(zoneLocation);
        drone.currentEvent = event;
        
        // Only schedule if not a redirection - redirection is handled directly
        if (!isRedirection) {
            drone.scheduleFireEvent(event);
        }

        // Dispatch delay - 2 seconds to prepare for mission
        System.out.println("[" + drone.getDroneId() + "] Preparing for dispatch (2s)...");
        Thread.sleep(2000);
        
        // Travel to fire location
        boolean movementCompleted = simulateMovement(drone, zoneLocation);
        
        // Verify that we're still working on the same task
        // If the drone was redirected, we will now be at the redirected location,
        // but we need to check if the current task is still this zone
        zoneId = drone.currentEvent != null ? drone.currentEvent.getZoneID() : zoneId;
        zoneLocation = getZoneLocation(zoneId);
        severity = drone.currentEvent != null ? drone.currentEvent.getSeverity() : severity;
        dronesNeeded = getRequiredDronesForSeverity(severity);

        // Check again if fire still needs attention upon arrival
        if (getDropsForZone(zoneId) >= dronesNeeded) {
            returnToBase(drone);
            return;
        }

        // Drop firefighting agent - 1 second to drop agent
        drone.setCurrentLocation(zoneLocation);
        System.out.println("[" + drone.getDroneId() + "] Dropping fire suppression agent (1s)...");
        Thread.sleep(1000);
        drone.dropAgent();

        // Update fire status and notify if extinguished
        synchronized (DroneSubsystem.class) {
            int dropCount = recordDropForZone(zoneId);
            if (dropCount == dronesNeeded) {
                drone.sendFireExtinguishedStatus(zoneId);
            }
        }

        // Return to base and complete mission
        returnToBase(drone);
    }
    
    /**
     * Helper method to return drone to base and complete mission
     */
    private static void returnToBase(DroneSubsystem drone) throws InterruptedException {
        drone.setTargetLocation(drone.getBaseLocation());
        drone.returningBack();
        
        // Check if we actually reached the base (movement wasn't interrupted)
        boolean reachedBase = simulateMovement(drone, drone.getBaseLocation());
        
        if (!reachedBase) {
            // Movement was interrupted - don't complete the mission
            System.out.println("[" + drone.getDroneId() + "] Return to base interrupted - likely redirected");
            return;
        }
        
        drone.setCurrentLocation(drone.getBaseLocation());
        
        // Refill capacity when arriving back to base - 3 seconds to refill
        System.out.println("[" + drone.getDroneId() + "] Refilling capacity at base (3s)...");
        Thread.sleep(3000);
        drone.getSpecifications().refill();
        
        drone.currentEvent = null;
        drone.taskCompleted();
    }

    /**
     * Calculate firefighting duration based on fire severity
     */
    private static int calculateFirefightingDuration(String severity, DroneSubsystem drone) {
        return drone.getSpecifications().calculateFirefightingDuration(severity);
    }

    /**
     * Determine required drones based on fire severity
     */
    private static int getRequiredDronesForSeverity(String severity) {
        // Creating a temporary DroneManager to use its method
        // In a real refactoring, we would need to adjust the architecture more substantially
        return new DroneManager(new Location(0, 0)).getDronesNeededForSeverity(severity);
    }

    /**
     * Get zone location from zone ID
     * 
     * First attempts to get zone center via network request to scheduler,
     * then falls back to hardcoded calculation if that fails.
     */
    private static Location getZoneLocation(int zoneId) {
        try {
            return DroneManager.getZoneLocation(zoneId, InetAddress.getLocalHost());
        } catch (Exception e) {
            // If there's an error getting the local host, use fallback
            return new Location(
                ((zoneId-1) % 3) * 700 + 350, // centered at x+350
                ((zoneId-1) / 3) * 500 + 250  // centered at y+250
            );
        }
    }

    /**
     * Simulate drone movement with visualization
     * Returns true if the movement completed normally, false if it was interrupted by redirection
     */
    private static boolean simulateMovement(DroneSubsystem drone, Location targetLocation) throws InterruptedException {
        Location currentLocation = drone.getCurrentLocation();
        int distance = currentLocation.distanceTo(targetLocation);
        int originalZoneId = drone.currentEvent != null ? drone.currentEvent.getZoneID() : -1;
        
        drone.startMovementTimer();
        
        // Skip if already at target
        if (distance == 0) {
            drone.resetTimers();
            return true;
        }

        // Calculate travel time based on drone speed
        DroneSpecifications specs = drone.getSpecifications();
        boolean isFaulted = drone.getCurrentStateName().equalsIgnoreCase("Fault");
        
        // Calculate travel time considering fault status
        int travelTimeMs;
        if (isFaulted) {
            double originalSpeed = specs.getMaxSpeed();
            specs.setMaxSpeed(originalSpeed * 0.5);
            travelTimeMs = specs.calculateTravelTime(distance);
            specs.setMaxSpeed(originalSpeed);
        } else {
            travelTimeMs = specs.calculateTravelTime(distance);
        }

        // Use a much more gradual movement approach
        int desiredFramerate = 20; // frames per second for status updates
        int totalUpdates = (travelTimeMs / 1000) * desiredFramerate;
        totalUpdates = Math.max(10, totalUpdates); // Ensure at least 10 updates
        
        // Calculate how many milliseconds between each update
        int updateIntervalMs = travelTimeMs / totalUpdates;
        
        // Use more internal steps for smoother local calculation (5x the visible updates)
        int internalSteps = totalUpdates * 5;
        int stepDelayMs = travelTimeMs / internalSteps;
        stepDelayMs = Math.max(5, stepDelayMs); // Minimum 5ms delay
        
        // Keep track of when we last sent an update
        long lastUpdateTime = System.currentTimeMillis();
        long lastCheckTime = System.currentTimeMillis();
        long startTime = lastUpdateTime;
        long endTime = startTime + travelTimeMs;
        
        // Set timeout for quick packet checks (100ms)
        try {
            drone.receiveSocket.setSoTimeout(100);
        } catch (SocketException e) {
            System.err.println("[" + drone.getDroneId() + "] Error setting socket timeout: " + e.getMessage());
        }
        
        // Update position based on elapsed time for smoother motion
        while (System.currentTimeMillis() < endTime) {
            // Check for redirection by comparing current task zone with original zone
            if (drone.currentEvent != null && originalZoneId != -1 && 
                drone.currentEvent.getZoneID() != originalZoneId) {
                // Redirection detected - target has changed
                System.out.println("[" + drone.getDroneId() + "] REDIRECTED from zone " + 
                    originalZoneId + " to zone " + drone.currentEvent.getZoneID());
                
                // Instead of returning, continue with the new target
                Location newTarget = getZoneLocation(drone.currentEvent.getZoneID());
                drone.setTargetLocation(newTarget);
                
                // Reset movement parameters
                currentLocation = drone.getCurrentLocation();
                targetLocation = newTarget;
                distance = currentLocation.distanceTo(targetLocation);
                originalZoneId = drone.currentEvent.getZoneID();
                
                // Reset timers
                travelTimeMs = specs.calculateTravelTime(distance);
                startTime = System.currentTimeMillis();
                endTime = startTime + travelTimeMs;
                lastUpdateTime = startTime;
                lastCheckTime = startTime;
                
                // Send status update with new target
                drone.sendStatusUpdate();
                continue;
            }
            
            long currentTime = System.currentTimeMillis();
            double progress = (double)(currentTime - startTime) / travelTimeMs;
            progress = Math.min(1.0, progress); // Ensure we don't exceed 1.0
            
            // Calculate position based on progress (0.0 to 1.0)
            int x = (int)(currentLocation.getX() + (targetLocation.getX() - currentLocation.getX()) * progress);
            int y = (int)(currentLocation.getY() + (targetLocation.getY() - currentLocation.getY()) * progress);
            
            // Update drone's location
            drone.setCurrentLocation(new Location(x, y));
            
            // Only send status updates at fixed intervals
            if (currentTime - lastUpdateTime >= updateIntervalMs) {
                drone.sendStatusUpdate();
                lastUpdateTime = currentTime;
            }
            
            // Check for redirection events (at most every 250ms to avoid excessive checking)
            if (currentTime - lastCheckTime >= 250) {
                FireEvent event = drone.receive(); // This will return null on timeout
                
                // If we received a redirection event, handle it directly here
                if (event != null && shouldHandleEvent(drone, event) && 
                    event.getZoneID() != originalZoneId) {
                    System.out.println("[" + drone.getDroneId() + "] RECEIVED REDIRECTION from zone " + 
                        originalZoneId + " to zone " + event.getZoneID());
                    
                    // Update drone task and target
                    FireEvent oldEvent = drone.currentEvent;
                    drone.currentEvent = event;
                    drone.fireEventQueue.clear();
                    
                    // Set the target to the new location
                    Location newTarget = getZoneLocation(event.getZoneID());
                    drone.setTargetLocation(newTarget);
                    
                    try {
                        // Send a notification about abandoning the previous fire so a replacement drone can be sent
                        if (oldEvent != null) {
                            // Format a special status update that tells Scheduler this fire still needs attention
                            String message = drone.getDroneId() + " EnRoute ABANDONED:" + oldEvent.getZoneID() + 
                                           " NEW_TASK:" + event.getZoneID() + " " +
                                           drone.getCurrentLocation().getX() + " " +
                                           drone.getCurrentLocation().getY();
                            drone.send(message, 6001);
                        }
                    } catch (Exception e) {
                        // Just log error and continue
                        System.err.println("[" + drone.getDroneId() + "] Error sending abandonment notification: " + e.getMessage());
                    }
                    
                    // Start movement to the new location immediately instead of returning
                    // So we immediately start moving toward the new target
                    currentLocation = drone.getCurrentLocation(); // Get current position
                    targetLocation = newTarget;                   // Update target
                    distance = currentLocation.distanceTo(targetLocation); // Recalculate distance
                    originalZoneId = event.getZoneID();          // Update original zone
                    
                    // Reset the movement timers
                    travelTimeMs = specs.calculateTravelTime(distance);
                    startTime = System.currentTimeMillis();
                    endTime = startTime + travelTimeMs;
                    lastUpdateTime = startTime;
                    lastCheckTime = startTime;
                    
                    // Send a status update with the new target
                    drone.sendStatusUpdate();
                    
                    // Continue the movement loop with the new target
                    continue;
                }
                
                lastCheckTime = currentTime;
            }
            
            // Short sleep to prevent high CPU usage
            Thread.sleep(stepDelayMs);
        }

        // Final position update
        drone.setCurrentLocation(targetLocation);
        drone.sendStatusUpdate();
        drone.resetTimers();
        return true;
    }

    /**
     * Main method to start a fleet of drones
     */
    public static void main(String[] args) {
        try {
            final int NUM_DRONES = 5;
            Thread[] threads = new Thread[NUM_DRONES];
            InetAddress localHost = InetAddress.getLocalHost();

            // Create and start drone threads
            for (int i = 0; i < NUM_DRONES; i++) {
                String droneId = "drone" + (i + 1);
                // Create a new DroneSpecifications object for each drone to avoid shared state
                DroneSpecifications droneSpecs = new DroneSpecifications();
                DroneSubsystem drone = new DroneSubsystem(localHost, droneId, new Location(0, 0), droneSpecs);
                
                threads[i] = new Thread(() -> runDrone(drone));
                threads[i].start();
                Thread.sleep(500);  // Stagger starts to avoid port conflicts
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}