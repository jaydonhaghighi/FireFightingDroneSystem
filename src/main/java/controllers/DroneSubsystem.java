package controllers;


import models.FireEvent;
import models.Location;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.net.*;

import static models.FireEvent.createFireEventFromString;

import static models.FireEvent.ErrorType;

/**
 * ANSI colors for console output
 */
class ConsoleColors {
    // Colors
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String CYAN = "\u001B[36m";
}

/**
 * Interface for different states of the
 * drone
 */

interface DroneState {

    /**
     * Drone handling fire status
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     * */
    void handleFireEvent(DroneSubsystem context, FireEvent event);
    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    void dropAgent(DroneSubsystem context);
    /**
     * Drone return state
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    void returningBack(DroneSubsystem context);

    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    void droneFaulted(DroneSubsystem context);

    /**
     * Drone task completion state
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    void taskCompleted(DroneSubsystem context);

    /**
     * Displaying drone's state
     * */
    void displayState();
}

/**
 * This class is the idle state for Drone
 * */

class Idle implements DroneState {

    /**
     * Drone handling fire status
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     * */

    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        //check if for error type here (I think)
        System.out.println("preparing to handle new fire event: " + event);
        context.setState(new EnRoute());
    }
    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
        System.out.println("drone is idle, nothing to drop");

    }


    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
        System.out.println("drone is idle and stationed at base.");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneSubsystem context) {
        System.out.println("drone has not faulted");
    }

    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
        System.out.println("drone is idle, no tasks have been completed");
    }

    /**
     * Display the state of drone
     *
     * */

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.CYAN + "IDLE" + ConsoleColors.RESET);
    }
}

/**
 * Class for enroute state of drone
 * */

class EnRoute implements DroneState {

    /**
     * Drone handling fire status
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     *
     * */
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        System.out.println("Drone is en route, already in motion of handling an event");

    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
        System.out.println("drone is en route, cannot drop agent yet");
        context.setState(new droppingAgent());
    }

    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */


    @Override
    public void returningBack(DroneSubsystem context) {
        System.out.println("drone is en route, has not returned yet"); //
    }


    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
        System.out.println("drone is en route, no tasks have been completed yet");
    }


    /**
     * Current state of drone
     *
     * */

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.YELLOW + "EN ROUTE" + ConsoleColors.RESET);
    }
}
/**
 * Class for dropping agent state
 * */
class droppingAgent implements DroneState {

    /**
     * Drone handling fire status
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     *
     * */
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        System.out.println("drone is dropping an agent and already handling task");
    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
        System.out.println("drone is currently dropping an agent");
    }

    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
        System.out.println("drone is dropping an agent and has not yet returned to its base");
        context.setState(new ArrivedToBase());
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneSubsystem context) {
        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
        System.out.println("drone is dropping an agent and has not yet completed its task");

    }

    /**
     * Current drone state
     *
     * */

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.YELLOW + "DROPPING AGENT" + ConsoleColors.RESET);
    }
}
/***
 * Class for drone ArriveToBase state
 * **/

class ArrivedToBase implements DroneState {
    /**
     * Drone status handling fire
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     *
     * */
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        System.out.println("drone has arrived to base and cannot accept new task");

    }
    /**
     * Drone status dropping agent
     *
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void dropAgent(DroneSubsystem context) {
        System.out.println("drone has arrived to base and has already dropped agent");
    }

    /**
     * Drone status returning to base
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
        System.out.println("drone has already arrived to base");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
        System.out.println("drone has arrived to base and not yet faulted");
        context.setState(new Fault());
    }
    /**
     * Drone status completing task
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
        System.out.println("drone has arrived to base and completed its task");
        context.setState(new Idle());
    }


    /**
     * Current status of drone
     *
     * */
    @Override
    public void displayState() {
        System.out.println(ConsoleColors.GREEN + "ARRIVED TO BASE" + ConsoleColors.RESET);
    }
}
/**
 * This class is the fault state for Drone
 * */
class Fault implements DroneState{

    /**
     * Drone status handling fire
     *
     * @param context DroneSateMachines setting drone state
     *        event FireEvent the fire event to handle
     *
     * */
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        System.out.println("drone has faulted and cannot handle event");
    }
    /**
     * Drone status dropping agent
     *
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
        System.out.println("drone nozzle/foam cannot open");

    }
    /**
     * Drone arrival status
     *
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void returningBack(DroneSubsystem context) {
        System.out.println("drone has faulted and did not move");

    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
        System.out.println("drone has faulted");
    }
    /**
     * Drone task completion status
     *
     *
     * @param context DroneSateMachines setting drone state
     *
     * */


    @Override
    public void taskCompleted(DroneSubsystem context) {
        System.out.println("Drone has faulted and returned to base. Now setting to idle.");
        context.setState(new Idle());  // Once at base, transition to idle
    }

    /**
     * Current state of drone
     * **/

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.RED + "FAULTED" + ConsoleColors.RESET);
    }
}

/**
 * DroneStateMachines class
 * for switching drone states
 * */
public class DroneSubsystem {
    //current state of drone
    private DroneState currentState;
    private Queue<FireEvent> fireEventQueue; // Queue for fire events
    private String droneId; // Unique identifier for this drone
    private Location currentLocation; // Current physical location
    private Location targetLocation; // Target location for movement
    private Location baseLocation; // Home base location

    private long movementStartTime = 0;
    private long dropAgentStartTime = 0;
    private static final long MAX_MOVEMENT_TIME = 30000; //30 seconds max for movement
    private static final long MAX_DROP_AGENT_TIME = 15000; // 15 seconds max for dropping agent
    private ErrorType currentError = ErrorType.NONE;


    private final InetAddress serverIP;

    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receieveSocket;

    private final int sendPort = 7000;
    private final int receivePort = 7001;
    
    /**
     * Constructor
     * @param serverIP The IP address of the scheduler server
     * */
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
        //set the initial state of drone
        currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        this.droneId = droneId;
        this.baseLocation = baseLocation;
        this.currentLocation = baseLocation;
        this.targetLocation = baseLocation;
        
        try {
            // Create more predictable unique ports for each drone
            int droneNumber = 0;
            if (droneId.length() > 5) {
                // Extract drone number from the ID (e.g. "drone1" -> 1)
                droneNumber = Integer.parseInt(droneId.substring(5));
            }
            
            // Use offset of 100 * droneNumber to ensure ports don't conflict
            int uniqueSendPort = sendPort + (droneNumber * 100);
            int uniqueReceivePort = receivePort + (droneNumber * 100);
            
            sendSocket = new DatagramSocket(uniqueSendPort);
            receieveSocket = new DatagramSocket(uniqueReceivePort);
            
            System.out.println(ConsoleColors.CYAN + 
                "[DRONE " + droneId + "] Initialized at " + baseLocation + 
                " using ports: send=" + uniqueSendPort + ", receive=" + uniqueReceivePort + 
                ConsoleColors.RESET);
        } catch (SocketException e) {
            System.out.println(ConsoleColors.RED + "[DRONE " + droneId + "] Socket error: " + e.getMessage() + ConsoleColors.RESET);
            e.printStackTrace();
        }
    }

    /**
     * Receives a fire event from the scheduler
     * 
     * @return The received fire event
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receieveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("receieve error: " + e);
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        System.out.println(ConsoleColors.BLUE + "[DRONE " + droneId + "] Received packet: " + ConsoleColors.YELLOW + r + ConsoleColors.RESET);
        return createFireEventFromString(r);
    }

    /**
     * Sends a message to the specified port
     * 
     * @param message The message to send
     * @param port The port to send to
     */
    public void send(String message, int port) {
        //String message = fire.toString();
        byte[] msg = message.getBytes();
        // No need for try-catch since serverIP is already validated
        sendPacket = new DatagramPacket(msg, msg.length, serverIP, port);
        System.out.println(ConsoleColors.BLUE + "[DRONE " + droneId + "] Sending: " + ConsoleColors.YELLOW + message + ConsoleColors.RESET);
        try {
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sends a status update to the scheduler
     */
    public void sendStatusUpdate() {
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        String status = droneId + " " + 
                        currentState.getClass().getSimpleName() + errorInfo + " " +
                        currentLocation.getX() + " " + 
                        currentLocation.getY();
        send(status, 6001); // Send to scheduler
    }

    /**
     * Returns the number of fire events currently queued
     * @return int the size of the queue
     */
    public int getQueueSize() {
        return fireEventQueue.size();
    }

    /**
     * Handling fire events
     * @param event FireEvent the fire event to handle
     * */
    public void handleFireEvent(FireEvent event) {
        System.out.println(ConsoleColors.BLUE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();

        // Check if the event has an error
        if(event.hasError()){
            setError(event.getError());
            System.out.println(ConsoleColors.RED + "[DRONE " + droneId + "] Error detected in fire event: " +
                    event.getError() + ConsoleColors.RESET);
        }
        currentState.handleFireEvent(this, event);
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }
    /**
     * Drone dropping agent
     * */

    public void dropAgent() {
        System.out.println(ConsoleColors.BLUE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        //Start drop agent timer
        startDropAgentTimer();

        currentState.dropAgent(this);

        //Check for drop agent timeout
        if (isDropAgentTimedOut()){
            setError(ErrorType.NOZZLE_JAM);
            System.out.println(ConsoleColors.RED + "[DRONE " + droneId + "] Nozzle jammed during agent drop - timeout exceeded" +
                    ConsoleColors.RESET);
        }
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();

        //Reset timer
        resetTimers();
    }

    /**
     * Drone returning to base
     * */
    public void returningBack() {
        System.out.println(ConsoleColors.BLUE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.returningBack(this);
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }

    /**
     * Drone having a fault
     * **/
    public void droneFaulted(){
        System.out.println(ConsoleColors.BLUE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.droneFaulted(this);
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }

    /**
     * Drone completing task
     * */
    public void taskCompleted() {
        System.out.println(ConsoleColors.BLUE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.taskCompleted(this);
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();

        if(!fireEventQueue.isEmpty()){
            System.out.println(ConsoleColors.GREEN + "\n[DRONE] Processing next fire event in queue" + ConsoleColors.RESET);
            FireEvent fireEvent = fireEventQueue.poll();
            scheduleFireEvent(fireEvent);
        }

    }

    /**
     * Sets an error on the drone
     * @param errorType The type of error
     */
    public void setError(ErrorType errorType) {
        this.currentError = errorType;
        System.out.println(ConsoleColors.RED + "[DRONE " + droneId + "] ERROR DETECTED: " +
                errorType + ConsoleColors.RESET);
        //If it's a hard fault, immediately transition to faulted state (nozzle bay/bay door issue)
        if(errorType == ErrorType.NOZZLE_JAM){
            System.out.println(ConsoleColors.RED + "[DRONE " + droneId + "] HARD FAULT: Shutting down drone" +
                    ConsoleColors.RESET);
            droneFaulted();
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
     * Starts the movement timer
     */
    public void startMovementTimer() {
        this.movementStartTime = System.currentTimeMillis();
    }

    /**
     * Checks if movement has timed out
     * @return true if movement has timed out
     */
    public boolean isMovementTimedOut() {
        if (movementStartTime == 0) return false;
        return (System.currentTimeMillis() - movementStartTime) > MAX_MOVEMENT_TIME;
    }

    /**
     * Starts the drop agent timer
     */
    public void startDropAgentTimer() {
        this.dropAgentStartTime = System.currentTimeMillis();
    }

    /**
     * Checks if drop agent has timed out
     * @return true if drop agent has timed out
     */
    public boolean isDropAgentTimedOut() {
        if (dropAgentStartTime == 0) return false;
        return (System.currentTimeMillis() - dropAgentStartTime) > MAX_DROP_AGENT_TIME;
    }

    /**
     * Resets all timers
     */
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
     *
     * @param state DroneState the state of the drone
     * */

    public void setState(DroneState state) {
        if (this.currentState instanceof Fault && !(state instanceof Idle)) {
            System.out.println("Drone faulted. Returning to base before going idle.");
            this.currentState = new ArrivedToBase(); // Transition to ArrivedToBase first
        } else {
            this.currentState = state;
        }

    }

    public void scheduleFireEvent(FireEvent event){
        if (currentState instanceof Idle){
            System.out.println("Drone is idle and ready for new fire event");
            handleFireEvent(event);
        } else{
            System.out.println("Drone is not idle and cannot handle a new fire event" + event);
            fireEventQueue.add(event);
        }

    }

    /**
     * Gets the drone's ID
     * 
     * @return the drone ID
     */
    public String getDroneId() {
        return droneId;
    }
    
    /**
     * Gets the drone's current location
     * 
     * @return the current location
     */
    public Location getCurrentLocation() {
        return currentLocation;
    }
    
    /**
     * Sets the drone's current location
     * 
     * @param location the new location
     */
    public void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }
    
    /**
     * Gets the drone's target location
     * 
     * @return the target location
     */
    public Location getTargetLocation() {
        return targetLocation;
    }
    
    /**
     * Sets the drone's target location
     * 
     * @param location the new target location
     */
    public void setTargetLocation(Location location) {
        this.targetLocation = location;
    }
    
    /**
     * Gets the drone's base location
     * 
     * @return the base location
     */
    public Location getBaseLocation() {
        return baseLocation;
    }
    
    /**
     * Main program for droneStateMachines
     * */
    public static void main(String[] args) {
        try {
            // Create multiple drones with different IDs and locations
            DroneSubsystem drone1 = new DroneSubsystem(InetAddress.getLocalHost(), "drone1", new Location(0, 0));
            DroneSubsystem drone2 = new DroneSubsystem(InetAddress.getLocalHost(), "drone2", new Location(10, 10));
            DroneSubsystem drone3 = new DroneSubsystem(InetAddress.getLocalHost(), "drone3", new Location(20, 20));
            
            // Start each drone in its own thread
            Thread thread1 = new Thread(() -> runDrone(drone1));
            Thread thread2 = new Thread(() -> runDrone(drone2));
            Thread thread3 = new Thread(() -> runDrone(drone3));
            
            thread1.start();
            thread2.start();
            thread3.start();
            
            // Wait for all threads to complete (they won't normally unless exception occurs)
            thread1.join();
            thread2.join();
            thread3.join();
            
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error in DroneSubsystem main: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Runs a single drone in a continuous loop
     * 
     * @param drone The drone to run
     */
    private static void runDrone(DroneSubsystem drone) {
        try {
            // Send initial status to scheduler
            drone.sendStatusUpdate();
            
            // Continuously process fire events until interrupted
            while (true) {
                FireEvent event = drone.receive();
                
                // Only process events assigned to this drone or with no assignment
                String assignedDroneId = event.getAssignedDroneId();
                if (assignedDroneId == null || assignedDroneId.equals(drone.getDroneId())) {
                    processEvent(drone, event);
                    // Send status update after event is processed
                    drone.sendStatusUpdate();
                } else {
                    System.out.println(ConsoleColors.YELLOW + "[DRONE " + drone.getDroneId() + "] Ignoring event assigned to " + assignedDroneId + ConsoleColors.RESET);
                }
            }
        } catch (Exception e) {
            System.out.println("Error in drone thread for " + drone.getDroneId() + ": " + e);
            e.printStackTrace();
        }
    }

    /**
     * Processes an event by scheduling it and executing the standard sequence of drone actions.
     */
    private static void processEvent(DroneSubsystem drone, FireEvent event) {
        // Simulate realistic drone operations with appropriate delays
        try {
            // Get zone location based on event
            int zoneId = event.getZoneID();
            Location zoneLocation = getZoneLocation(zoneId);
            String severity = event.getSeverity();
            String droneId = drone.getDroneId();

            //Check for injected errors from the event
            if(event.hasError()){
                System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Error injected from input: " +
                        event.getError() + ConsoleColors.RESET);
                drone.setError(event.getError());

                //if it's a hard fault like nozzle/bay door fault, we abort mission!
                if (event.getError() == ErrorType.NOZZLE_JAM) {
                    System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Hard fault detected, aborting mission" +
                            ConsoleColors.RESET);
                    return;
                }
            }
            
            // ──────────────── MISSION START ─────────────────
            System.out.println(ConsoleColors.YELLOW + "\nDRONE " + droneId + ": Mission start to Zone " + zoneId +
                           " - " + severity + " fire" + ConsoleColors.RESET);
            
            // Update drone target location and schedule event
            drone.setTargetLocation(zoneLocation);
            drone.scheduleFireEvent(event);
            Thread.sleep(1000); // Preparation delay
            
            // Fly to zone
            simulateMovement(drone, zoneLocation);

            //Check if movement failed
            if(drone.hasError() && drone.getCurrentError() == ErrorType.DRONE_STUCK){
                System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Movement fault detected, cannot reach zone" +
                        ConsoleColors.RESET);
                drone.droneFaulted();
                return;
            }
            
            // Calculate firefighting duration based on severity
            int firefightingDuration = calculateFirefightingDuration(severity);
            
            // Drop agent at target location
            drone.setCurrentLocation(zoneLocation);
            drone.dropAgent();

            // Check if nozzle/bay door fault occurred
            if (drone.hasError() && (drone.getCurrentError() == ErrorType.NOZZLE_JAM)){
                System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Nozzle/bay door fault detected, cannot drop agent" +
                        ConsoleColors.RESET);
                drone.droneFaulted();
                return;
            }


            System.out.println(ConsoleColors.RED + " DRONE " + droneId + ": Fighting fire in Zone " + zoneId +
                           " (" + (firefightingDuration/1000) + "s)" + ConsoleColors.RESET);
            Thread.sleep(firefightingDuration);
            
            // Fire extinguished
            System.out.println(ConsoleColors.GREEN + "DRONE " + droneId + ": Fire extinguished in Zone " + zoneId +
                           ConsoleColors.RESET);
            
            // Return to base
            drone.setTargetLocation(drone.getBaseLocation());
            drone.returningBack();
            Thread.sleep(500);
            
            // Handle faults if needed
            if ("DRONE_FAULT".equalsIgnoreCase(event.getEventType())) {
                drone.droneFaulted();
                System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Malfunction detected!" +
                               ConsoleColors.RESET);
                Thread.sleep(2000);
            }
            
            // Return flight
            simulateMovement(drone, drone.getBaseLocation());

            // Check if movement failed during return
            if (drone.hasError() && drone.getCurrentError() == ErrorType.DRONE_STUCK) {
                System.out.println(ConsoleColors.RED + "DRONE " + droneId + ": Movement fault detected during return, cannot reach base" +
                        ConsoleColors.RESET);
                drone.droneFaulted();
                return;
            }
            
            // Complete the task and perform maintenance
            drone.setCurrentLocation(drone.getBaseLocation());
            Thread.sleep(1000); // Shorter maintenance time

            // Clear any non-hard faults when returning to base
            if (drone.hasError() && drone.getCurrentError() != ErrorType.NOZZLE_JAM) {
                System.out.println(ConsoleColors.GREEN + "DRONE " + droneId + ": Non-hard fault cleared during maintenance" +
                        ConsoleColors.RESET);
                drone.clearError();
            }

            drone.taskCompleted();
            
            // ──────────────── MISSION COMPLETE ─────────────────
            System.out.println(ConsoleColors.GREEN + "DRONE " + droneId + ": Mission complete, ready for next assignment\n" +
                           ConsoleColors.RESET);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(ConsoleColors.RED + "DRONE " + drone.getDroneId() + ": Mission interrupted" +
                           ConsoleColors.RESET);
        }
    }
    
    /**
     * Calculate firefighting duration based on fire severity
     * 
     * @param severity the fire severity
     * @return duration in milliseconds
     */
    private static int calculateFirefightingDuration(String severity) {
        switch (severity.toLowerCase()) {
            case "high":
                return 8000; // 8 seconds for high severity
            case "moderate":
                return 5000; // 5 seconds for moderate severity
            case "low":
                return 3000; // 3 seconds for low severity
            default:
                return 4000; // Default duration
        }
    }
    
    /**
     * Gets a zone location based on zone ID
     * 
     * @param zoneId the zone ID
     * @return the location of the zone
     */
    private static Location getZoneLocation(int zoneId) {
        // Create a simple grid of zones for demonstration
        int x = ((zoneId-1) % 3) * 10 + 10; // Create a 3x4 grid
        int y = ((zoneId-1) / 3) * 10 + 10;
        return new Location(x, y);
    }
    
    /**
     * Simulates drone movement with simplified output
     * 
     * @param drone the drone to move
     * @param targetLocation the target location
     */
    private static void simulateMovement(DroneSubsystem drone, Location targetLocation) throws InterruptedException {
        Location currentLocation = drone.getCurrentLocation();
        int distance = currentLocation.distanceTo(targetLocation);
        boolean isFaulted = drone.getCurrentStateName().equalsIgnoreCase("Fault");
        String droneId = drone.getDroneId();

        //Start movement timer
        drone.startMovementTimer();
        // If locations are the same, no movement needed
        if (distance == 0){
            drone.resetTimers();
            return;
        }
        
        // Calculate travel time based on distance and speed
        double baseSpeed = isFaulted ? 5.0 : 10.0; // units per second
        int travelTimeMs = Math.max((int)(distance / baseSpeed * 1000), 1000);
        
        // Show flight start status
        String destinationType = targetLocation.equals(drone.getBaseLocation()) ? "base" : "zone";
        String speedStatus = isFaulted ? "reduced speed" : "normal speed";
        
        System.out.println(ConsoleColors.BLUE + 
            "DRONE " + droneId + ": Flying to " + destinationType + " (" +
            distance + " units, " + String.format("%.1f", travelTimeMs/1000.0) + "s, " + 
            speedStatus + ")" + ConsoleColors.RESET);
        
        // Determine number of updates (fewer updates for cleaner output)
        int steps = Math.min(3, distance / 10); // max 3 steps for any distance
        if (steps == 0) steps = 1; // at least 1 step
        int stepDelayMs = travelTimeMs / steps;
        
        // Simulate movement in steps
        for (int i = 1; i <= steps; i++) {
            // Calculate intermediate position
            int x = currentLocation.getX() + (targetLocation.getX() - currentLocation.getX()) * i / steps;
            int y = currentLocation.getY() + (targetLocation.getY() - currentLocation.getY()) * i / steps;
            Location intermediateLocation = new Location(x, y);
            
            // Update drone position and send status
            drone.setCurrentLocation(intermediateLocation);
            drone.sendStatusUpdate();
            
            // Only show progress for longer journeys
            if (distance > 30 && steps > 1) {
                int progressPercent = (i * 100) / steps;
                if (i > 0 && i < steps) { // Don't show for first and last step
                    System.out.println(ConsoleColors.BLUE + 
                        "DRONE " + droneId + ": Flight " + progressPercent + "% complete" +
                        ConsoleColors.RESET);
                }
            }
            
            // Delay between movement steps
            Thread.sleep(stepDelayMs);
        }
        
        // Ensure final position is exactly the target
        if(!drone.hasError() || drone.getCurrentError() != ErrorType.DRONE_STUCK){
            drone.setCurrentLocation(targetLocation);
            drone.sendStatusUpdate();
        }

        //Reset timers
        drone.resetTimers();

    }
}
