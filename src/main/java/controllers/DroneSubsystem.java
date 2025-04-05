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
    /**
     * Handle a fire event
     */
    void handleFireEvent(DroneSubsystem context, FireEvent event);
    
    /**
     * Handle dropping fire-fighting agent
     */
    void dropAgent(DroneSubsystem context);
    
    /**
     * Handle returning to base
     */
    void returningBack(DroneSubsystem context);

    /**
     * Handle drone fault conditions
     */
    void droneFaulted(DroneSubsystem context);

    /**
     * Handle task completion
     */
    void taskCompleted(DroneSubsystem context);

    /**
     * Display the current state
     */
    void displayState();
}

/**
 * Idle state - drone is at base, ready for assignments
 */
class Idle implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.setState(new EnRoute());
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        // Nothing to do in idle state
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        // Already at base
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        // No fault handling needed in idle state
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        // No task to complete
    }

    @Override
    public void displayState() {
        // State display removed
    }
}

/**
 * EnRoute state - drone is traveling to fire location
 */
class EnRoute implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        // Already handling an event
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.setState(new DroppingAgent());
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        // Not ready to return yet
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        // Fault handling
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        // Task not complete yet
    }

    @Override
    public void displayState() {
        // State display removed
    }
}

/**
 * DroppingAgent state - drone is at fire location and deploying fire-fighting agent
 */
class DroppingAgent implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        // Already handling an event
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        // Already dropping agent
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.setState(new ArrivedToBase());
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        // Fault handling
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        // Task not complete yet
    }

    @Override
    public void displayState() {
        // State display removed
    }
}

/**
 * ArrivedToBase state - drone has returned to base after mission
 */
class ArrivedToBase implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        // Cannot accept new tasks until maintenance is complete
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        // Already dropped agent
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        // Already at base
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.setState(new Fault());
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.setState(new Idle());
    }

    @Override
    public void displayState() {
        // State display removed
    }
}

/**
 * Fault state - drone has experienced a fault and needs maintenance
 */
class Fault implements DroneState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        // Cannot handle events while faulted
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        // Cannot drop agent while faulted
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        // Cannot return while faulted
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        // Already faulted
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.setState(new Idle());  // Maintenance complete, return to idle
    }

    @Override
    public void displayState() {
        // State display removed
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
     * Basic constructor with default values
     * @param serverIP The IP address of the scheduler server
     */
    public DroneSubsystem(InetAddress serverIP) {
        this(serverIP, "drone1", new Location(0, 0));
    }

    /**
     * Constructor with drone ID and base location
     * @param serverIP The IP address of the scheduler server
     * @param droneId The unique identifier for this drone
     * @param baseLocation The location of the drone's home base
     */
    public DroneSubsystem(InetAddress serverIP, String droneId, Location baseLocation) {
        this(serverIP, droneId, baseLocation, new DroneSpecifications());
    }

    /**
     * Full constructor with all parameters
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
     * Initialize network sockets for communication
     */
    private void initializeNetworking() {
        try {
            // Create unique ports for each drone based on drone ID
            int droneNumber = 0;
            if (droneId.length() > 5) {
                droneNumber = Integer.parseInt(droneId.substring(5));
            }
            
            // Use offset to ensure unique ports
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
        // Build status message with error and task info if applicable
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        String taskInfo = buildTaskInfo();
        
        String status = droneId + " " +
                currentState.getClass().getSimpleName() + errorInfo + taskInfo + " " +
                currentLocation.getX() + " " +
                currentLocation.getY();
        send(status, 6001); // Send to scheduler
    }
    
    /**
     * Builds the task information part of the status message
     */
    private String buildTaskInfo() {
        if (currentEvent != null) {
            return " TASK:" + currentEvent.getZoneID() + ":" + currentEvent.getSeverity();
        } else if (fireEventQueue.peek() != null) {
            FireEvent event = fireEventQueue.peek();
            return " TASK:" + event.getZoneID() + ":" + event.getSeverity();
        }
        return "";
    }
    
    /**
     * Sends a status update with fire extinguished information
     * @param zoneId The zone ID where fire was extinguished
     */
    public void sendFireExtinguishedStatus(int zoneId) {
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        String fireOutInfo = " FIRE_OUT:" + zoneId;
        
        String status = droneId + " " +
                currentState.getClass().getSimpleName() + errorInfo + fireOutInfo + " " +
                currentLocation.getX() + " " +
                currentLocation.getY();
        send(status, 6001);
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
    private static void processEvent(DroneSubsystem drone, FireEvent event) {
        try {
            // Setup phase
            int zoneId = event.getZoneID();
            Location zoneLocation = getZoneLocation(zoneId);
            String severity = event.getSeverity();
            String droneId = drone.getDroneId();
            

            // Check the drop count before starting mission to avoid redundant work
            int dronesNeeded = getRequiredDronesForSeverity(severity);
            int currentDrops = getDropsForZone(zoneId);
            
            // Check if fire is already extinguished before starting mission
            if (currentDrops >= dronesNeeded) {
                
                // Complete mission immediately
                drone.setCurrentLocation(drone.getBaseLocation());
                drone.currentEvent = null;
                drone.setState(new Idle()); // Force state to idle
                drone.sendStatusUpdate(); // Notify scheduler we're idle
                return;
            }

            // Initialize mission parameters
            drone.setTargetLocation(zoneLocation);
            drone.currentEvent = event;
            drone.scheduleFireEvent(event);
            Thread.sleep(1000);  // Preparation delay

            // Travel to fire location
            simulateMovement(drone, zoneLocation);
            
            // Check again if fire still needs attention upon arrival
            currentDrops = getDropsForZone(zoneId);
            if (currentDrops >= dronesNeeded) {
                
                // Skip firefighting and return to base immediately
                drone.setTargetLocation(drone.getBaseLocation());
                drone.returningBack();
                simulateMovement(drone, drone.getBaseLocation());
                
                // Complete mission
                drone.setCurrentLocation(drone.getBaseLocation());
                Thread.sleep(1000);  // Maintenance time
                drone.currentEvent = null;
                drone.taskCompleted();
                return;
            }

            // Calculate firefighting duration
            int firefightingDuration = calculateFirefightingDuration(severity, drone);

            // Drop firefighting agent
            drone.setCurrentLocation(zoneLocation);
            drone.dropAgent();
            Thread.sleep(firefightingDuration);

            // Update fire status
            int dropCount;
            boolean isExtinguished;
            
            // Use synchronized block to atomically check and update drop count
            synchronized (DroneSubsystem.class) {
                dropCount = recordDropForZone(zoneId);
                isExtinguished = (dropCount >= dronesNeeded);
                
                
                // Only the drone that completes the final drop should send the notification
                if (isExtinguished && dropCount == dronesNeeded) {
                    drone.sendFireExtinguishedStatus(zoneId);
                }
            }

            // Return to base
            drone.setTargetLocation(drone.getBaseLocation());
            drone.returningBack();
            Thread.sleep(500);
            simulateMovement(drone, drone.getBaseLocation());

            // Complete mission
            drone.setCurrentLocation(drone.getBaseLocation());
            Thread.sleep(1000);  // Maintenance time
            drone.currentEvent = null;
            drone.taskCompleted();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
     * Note: This method attempts to get the correct zone center by communicating with the scheduler,
     * which will connect to DroneManager for the actual zone data. If communication fails,
     * it falls back to a hardcoded calculation.
     */
    private static Location getZoneLocation(int zoneId) {
        try {
            // First try to get zone center from the scheduler
            // Send a request to the scheduler asking for zone coordinates
            String request = "ZONE_INFO_REQUEST:" + zoneId;
            byte[] msg = request.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), 6001);
            DatagramSocket tempSocket = new DatagramSocket();
            tempSocket.setSoTimeout(1000); // Wait up to 1 second for response
            tempSocket.send(requestPacket);
            
            // Wait for response
            byte[] buffer = new byte[100];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            tempSocket.receive(responsePacket);
            tempSocket.close();
            
            String response = new String(buffer, 0, responsePacket.getLength());
            if (response.startsWith("ZONE_INFO:")) {
                String[] parts = response.split(":");
                if (parts.length >= 4) {
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);
                    return new Location(x, y);
                }
            }
        } catch (Exception e) {
            // If any error occurs, fall back to hardcoded calculation
        }
        
        // Fallback to hardcoded calculation if communication fails
        // Use actual zone dimensions from zones.txt: 700x500 for columns 1-3
        int x = ((zoneId-1) % 3) * 700 + 350; // 700m wide zones, centered at x+350
        int y = ((zoneId-1) / 3) * 500 + 250; // 500m tall zones, centered at y+250
        return new Location(x, y);
    }

    /**
     * Simulate drone movement with visualization
     */
    private static void simulateMovement(DroneSubsystem drone, Location targetLocation) 
            throws InterruptedException {
        Location currentLocation = drone.getCurrentLocation();
        int distance = currentLocation.distanceTo(targetLocation);
        boolean isFaulted = drone.getCurrentStateName().equalsIgnoreCase("Fault");
        String droneId = drone.getDroneId();

        drone.startMovementTimer();
        
        // Skip if already at target
        if (distance == 0) {
            drone.resetTimers();
            return;
        }

        // Calculate movement parameters
        DroneSpecifications specs = drone.getSpecifications();
        double maxSpeed = isFaulted ? specs.getMaxSpeed() * 0.5 : specs.getMaxSpeed();
        
        // Temporarily modify specs if faulted for accurate calculation
        double originalMaxSpeed = specs.getMaxSpeed();
        if (isFaulted) {
            specs.setMaxSpeed(maxSpeed);
        }
        
        int travelTimeMs = specs.calculateTravelTime(distance);
        
        // Restore original specs
        if (isFaulted) {
            specs.setMaxSpeed(originalMaxSpeed);
        }

        // Simulate movement in steps for smoother visualization
        // Increase steps for much smoother movement
        int steps = Math.max(50, distance / 10);
        if (steps == 0) steps = 1;
        int stepDelayMs = travelTimeMs / steps;

        // Update position in steps
        for (int i = 1; i <= steps; i++) {
            int x = currentLocation.getX() + (targetLocation.getX() - currentLocation.getX()) * i / steps;
            int y = currentLocation.getY() + (targetLocation.getY() - currentLocation.getY()) * i / steps;
            Location intermediateLocation = new Location(x, y);

            drone.setCurrentLocation(intermediateLocation);
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
            DroneSubsystem[] drones = new DroneSubsystem[NUM_DRONES];
            Thread[] threads = new Thread[NUM_DRONES];

            // Create and start drone threads
            for (int i = 0; i < NUM_DRONES; i++) {
                String droneId = "drone" + (i + 1);
                drones[i] = new DroneSubsystem(InetAddress.getLocalHost(), droneId, 
                                              new Location(0, 0), droneSpecs);

                final int droneIndex = i;
                threads[i] = new Thread(() -> runDrone(drones[droneIndex]));
                threads[i].start();
                Thread.sleep(500);  // Stagger starts to avoid port conflicts
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}