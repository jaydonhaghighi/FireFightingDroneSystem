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
 * ANSI colors for console output
 */
class ConsoleColors {
    // Reset
    static final String RESET = "\u001B[0m";
    // Regular colors
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String PURPLE = "\u001B[35m";
    static final String CYAN = "\u001B[36m";
    static final String TEAL = "\u001B[38;5;27m";
    static final String LAVENDER = "\u001B[38;5;183m";
    // Bold colors
    static final String BOLD_RED = "\u001B[1;31m";
    static final String BOLD_GREEN = "\u001B[1;32m";
    static final String BOLD_YELLOW = "\u001B[1;33m";
    static final String BOLD_PURPLE = "\u001B[1;35m";
    static final String BOLD_WHITE = "\u001B[1;37m";
    static final String BOLD_ORANGE = "\u001B[1;38;5;208m";
    static final String BOLD_LIME = "\u001B[1;38;5;154m";
}

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
        System.out.println(ConsoleColors.BOLD_YELLOW + "IDLE" + ConsoleColors.RESET);
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
        System.out.println(ConsoleColors.BOLD_GREEN + "EN ROUTE" + ConsoleColors.RESET);
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
        System.out.println(ConsoleColors.BOLD_ORANGE + "DROPPING AGENT" + ConsoleColors.RESET);
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
        System.out.println(ConsoleColors.BOLD_PURPLE + "ARRIVED TO BASE" + ConsoleColors.RESET);
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
        System.out.println(ConsoleColors.BOLD_RED + "FAULTED" + ConsoleColors.RESET);
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
            
            logInitialization(uniqueSendPort, uniqueReceivePort);
        } catch (SocketException e) {
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Socket error: " + e.getMessage() + ConsoleColors.RESET);
            e.printStackTrace();
        }
    }
    
    /**
     * Log drone initialization information
     */
    private void logInitialization(int sendPort, int receivePort) {
        System.out.println(ConsoleColors.TEAL +
            "[" + droneId.toUpperCase() + "] Initialized at: " + ConsoleColors.BLUE + baseLocation +
            ConsoleColors.TEAL + " Ports:" + ConsoleColors.BLUE + " send=" + sendPort + ", receive=" + receivePort +
            ConsoleColors.RESET);
            
        System.out.println(ConsoleColors.TEAL +
            "[" + droneId.toUpperCase() + "] Specifications:" + ConsoleColors.BLUE + 
            " Max Speed=" + specifications.getMaxSpeed() + " km/h, Flow Rate=" + specifications.getFlowRate() + 
            " L/s, Capacity=" + specifications.getCarryCapacity() + " L, Battery Life=" + 
            specifications.getBatteryLife() + " min" + ConsoleColors.RESET);
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
            System.out.println(ConsoleColors.TEAL + "[" + droneId.toUpperCase() + 
                              "] Received packet: " + ConsoleColors.BLUE + message + 
                              ConsoleColors.RESET);
            return createFireEventFromString(message);
        } catch (IOException e) {
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + 
                              "] Receive error: " + e.getMessage() + ConsoleColors.RESET);
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
            System.out.println(ConsoleColors.TEAL + "[" + droneId.toUpperCase() + 
                              "] Sending: " + ConsoleColors.BLUE + message + 
                              ConsoleColors.RESET);
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + 
                              "] Send error: " + e.getMessage() + ConsoleColors.RESET);
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
        System.out.print(ConsoleColors.BOLD_WHITE + "\n[" + droneId.toUpperCase() + 
                        "] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        
        action.execute();
        
        System.out.print(ConsoleColors.BOLD_WHITE + "[" + droneId.toUpperCase() + 
                        "] State after: " + ConsoleColors.RESET);
        currentState.displayState();
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
            System.out.println(ConsoleColors.GREEN + "\n[" + droneId.toUpperCase() + 
                             "] Processing next fire event in queue" + ConsoleColors.RESET);
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
        
        if (errorType != ErrorType.NONE) {
            System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + 
                             "] ERROR HANDLING DISABLED: Ignoring " + 
                             errorType + " error" + ConsoleColors.RESET);
        }
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
            System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + 
                             "] Drone faulted. Returning to base before going idle." + 
                             ConsoleColors.RESET);
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
     * Zone tracking methods
     */
    public static int recordDropForZone(int zoneId) {
        return zoneDropsMap.compute(zoneId, (key, value) -> (value == null) ? 1 : value + 1);
    }

    public static int getDropsForZone(int zoneId) {
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
                    System.out.println(ConsoleColors.RED + "[" + drone.getDroneId() + 
                                     "] Event is null, skipping." + ConsoleColors.RESET);
                    continue;
                }
                
                // Check if this drone should handle the event
                if (shouldHandleEvent(drone, event)) {
                    logMultiDroneResponse(drone, event);
                    processEvent(drone, event);
                    drone.sendStatusUpdate();
                } else {
                    System.out.println(ConsoleColors.YELLOW + "[" + drone.getDroneId().toUpperCase() +
                                     "] Ignoring event assigned to " + event.getAssignedDroneId() + 
                                     ConsoleColors.RESET);
                }
            }
        } catch (Exception e) {
            System.out.println(ConsoleColors.RED + "Error in drone thread for " + 
                             drone.getDroneId().toUpperCase() + ": " + e.getMessage() + 
                             ConsoleColors.RESET);
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
     * Log information about multi-drone responses
     */
    private static void logMultiDroneResponse(DroneSubsystem drone, FireEvent event) {
        if (event.getAssignedDroneCount() > 1) {
            System.out.println(ConsoleColors.CYAN + "[" + drone.getDroneId().toUpperCase() +
                             "] Part of multi-drone response (" + 
                             event.getAssignedDroneCount() + " drones total)" + 
                             ConsoleColors.RESET);
        }
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

            // Initialize mission parameters
            drone.setTargetLocation(zoneLocation);
            drone.currentEvent = event;
            drone.scheduleFireEvent(event);
            Thread.sleep(1000);  // Preparation delay

            // Travel to fire location
            simulateMovement(drone, zoneLocation);

            // Calculate firefighting duration
            int firefightingDuration = calculateFirefightingDuration(severity, drone);

            // Drop firefighting agent
            drone.setCurrentLocation(zoneLocation);
            drone.dropAgent();
            System.out.println(ConsoleColors.BOLD_RED + "[" + droneId.toUpperCase() + 
                             "] Fighting fire in Zone " + zoneId + ConsoleColors.RESET);
            Thread.sleep(firefightingDuration);

            // Update fire status
            int dronesNeeded = getRequiredDronesForSeverity(severity);
            int dropCount = recordDropForZone(zoneId);
            boolean isExtinguished = (dropCount >= dronesNeeded);
            
            if (isExtinguished) {
                System.out.println(ConsoleColors.BOLD_LIME + "[" + droneId.toUpperCase() + 
                                 "] Fire extinguished in Zone " + zoneId +
                                 " (Drops: " + dropCount + "/" + dronesNeeded + ")" + 
                                 ConsoleColors.RESET);
                drone.sendFireExtinguishedStatus(zoneId);
            } else {
                System.out.println(ConsoleColors.BOLD_YELLOW + "[" + droneId.toUpperCase() + 
                                 "] Fire partially contained in Zone " + zoneId +
                                 " (Drops: " + dropCount + "/" + dronesNeeded + ")" + 
                                 ConsoleColors.RESET);
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

            System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + 
                             "] Mission complete, ready for next assignment\n" +
                             ConsoleColors.RESET);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(ConsoleColors.RED + "[" + drone.getDroneId().toUpperCase() + 
                             "] Mission interrupted" + ConsoleColors.RESET);
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
     */
    private static Location getZoneLocation(int zoneId) {
        // Create a grid of 700m x 600m zones (3 columns, 4 rows)
        int x = ((zoneId-1) % 3) * 700 + 350; // 700m wide zones, centered at x+350
        int y = ((zoneId-1) / 3) * 600 + 300; // 600m tall zones, centered at y+300
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

        // Log flight details
        String destinationType = targetLocation.equals(drone.getBaseLocation()) ? "base" : "zone";
        String speedStatus = isFaulted ? "reduced speed" : "normal speed";
        
        System.out.println(ConsoleColors.LAVENDER + "[" + droneId.toUpperCase() + 
                         "] Flying to " + destinationType + " (" +
                         distance + " meters, " + String.format("%.1f", travelTimeMs/1000.0) + "s, " + 
                         speedStatus + ", max speed: " + maxSpeed + " km/h)" + 
                         ConsoleColors.RESET);
        
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
            final int NUM_DRONES = 5;
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
            System.out.println(ConsoleColors.RED + "Unknown host error: " + e.getMessage() + 
                             ConsoleColors.RESET);
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println(ConsoleColors.RED + "Error in DroneSubsystem main: " + 
                             e.getMessage() + ConsoleColors.RESET);
            e.printStackTrace();
        }
    }
}
