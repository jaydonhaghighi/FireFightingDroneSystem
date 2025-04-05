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
    // Default implementations for all methods - do nothing by default
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
     * @return The received fire event
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);

        try {
            receiveSocket.receive(receivePacket);
            int len = receivePacket.getLength();
            String message = new String(data, 0, len);
            return createFireEventFromString(message);
        } catch (IOException e) {
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
        
        return droneId + " " +
               currentState.getClass().getSimpleName() + 
               errorInfo + 
               taskInfo + 
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
     * Clears the current error
     */
    public void clearError() {
        this.currentError = ErrorType.NONE;
    }

    /**
     * Timer management methods
     */
    public void startMovementTimer() {
        this.movementStartTime = System.currentTimeMillis();
    }

    public boolean isMovementTimedOut() {
        if (movementStartTime == 0) return false;
        return (System.currentTimeMillis() - movementStartTime) > MAX_MOVEMENT_TIME;
    }

    public void startDropAgentTimer() {
        this.dropAgentStartTime = System.currentTimeMillis();
    }

    public boolean isDropAgentTimedOut() {
        if (dropAgentStartTime == 0) return false;
        return (System.currentTimeMillis() - dropAgentStartTime) > MAX_DROP_AGENT_TIME;
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

    public void setSpecifications(DroneSpecifications specifications) {
        this.specifications = specifications;
    }

    /**
     * Zone tracking methods with proper synchronization to prevent concurrency issues
     */
    public static synchronized int recordDropForZone(int zoneId) {
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
                FireEvent event = drone.receive();
                if (event == null) {
                    continue;
                }
                
                // Check if this drone should handle the event
                if (shouldHandleEvent(drone, event)) {
                    processEvent(drone, event);
                    drone.sendStatusUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Determine if this drone should handle the given event
     */
    private static boolean shouldHandleEvent(DroneSubsystem drone, FireEvent event) {
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
        
        // Check if fire is already extinguished before starting mission
        if (currentDrops >= dronesNeeded) {
            drone.setCurrentLocation(drone.getBaseLocation());
            drone.currentEvent = null;
            drone.setState(new Idle());
            drone.sendStatusUpdate();
            return;
        }

        // Initialize mission parameters
        drone.setTargetLocation(zoneLocation);
        drone.currentEvent = event;
        drone.scheduleFireEvent(event);

        // Travel to fire location
        simulateMovement(drone, zoneLocation);
        
        // Check again if fire still needs attention upon arrival
        if (getDropsForZone(zoneId) >= dronesNeeded) {
            returnToBase(drone);
            return;
        }

        // Drop firefighting agent
        drone.setCurrentLocation(zoneLocation);
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
        simulateMovement(drone, drone.getBaseLocation());
        
        drone.setCurrentLocation(drone.getBaseLocation());
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
        switch (severity.toLowerCase()) {
            case "high":
                return 3; // 30L water needed
            case "moderate":
                return 2; // 20L water needed
            case "low":
            default:
                return 1; // 10L water needed
        }
    }

    /**
     * Get zone location from zone ID
     * 
     * First attempts to get zone center via network request to scheduler,
     * then falls back to hardcoded calculation if that fails.
     */
    private static Location getZoneLocation(int zoneId) {
        try {
            // Request zone info from scheduler
            DatagramSocket tempSocket = new DatagramSocket();
            tempSocket.setSoTimeout(1000); // 1 second timeout
            
            // Send request
            String request = "ZONE_INFO_REQUEST:" + zoneId;
            byte[] requestData = request.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(
                requestData, requestData.length, 
                InetAddress.getLocalHost(), 6001
            );
            tempSocket.send(requestPacket);
            
            // Receive response
            byte[] responseData = new byte[100];
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
            tempSocket.receive(responsePacket);
            tempSocket.close();
            
            // Parse response
            String response = new String(responseData, 0, responsePacket.getLength());
            if (response.startsWith("ZONE_INFO:")) {
                String[] parts = response.split(":");
                if (parts.length >= 4) {
                    return new Location(
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                    );
                }
            }
        } catch (Exception e) {
            // Network request failed, use fallback
        }
        
        // Fallback: calculate zone location based on grid layout
        // 700x500 zones in a 3-column grid
        return new Location(
            ((zoneId-1) % 3) * 700 + 350, // centered at x+350
            ((zoneId-1) / 3) * 500 + 250  // centered at y+250
        );
    }

    /**
     * Simulate drone movement with visualization
     */
    private static void simulateMovement(DroneSubsystem drone, Location targetLocation) throws InterruptedException {
        Location currentLocation = drone.getCurrentLocation();
        int distance = currentLocation.distanceTo(targetLocation);
        
        drone.startMovementTimer();
        
        // Skip if already at target
        if (distance == 0) {
            drone.resetTimers();
            return;
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

        // Simulate movement in steps for visualization
        int steps = Math.max(50, distance / 10);
        int stepDelayMs = travelTimeMs / steps;

        // Update position in steps
        for (int i = 1; i <= steps; i++) {
            int x = currentLocation.getX() + (targetLocation.getX() - currentLocation.getX()) * i / steps;
            int y = currentLocation.getY() + (targetLocation.getY() - currentLocation.getY()) * i / steps;
            
            drone.setCurrentLocation(new Location(x, y));
            drone.sendStatusUpdate();
            Thread.sleep(stepDelayMs);
        }

        // Final position update
        drone.setCurrentLocation(targetLocation);
        drone.sendStatusUpdate();
        drone.resetTimers();
    }

    /**
     * Main method to start a fleet of drones
     */
    public static void main(String[] args) {
        try {
            final int NUM_DRONES = 10;
            DroneSpecifications droneSpecs = new DroneSpecifications();
            Thread[] threads = new Thread[NUM_DRONES];
            InetAddress localHost = InetAddress.getLocalHost();

            // Create and start drone threads
            for (int i = 0; i < NUM_DRONES; i++) {
                String droneId = "drone" + (i + 1);
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