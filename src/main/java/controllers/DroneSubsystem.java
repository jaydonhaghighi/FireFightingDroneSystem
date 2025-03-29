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

import static models.FireEvent.createFireEventFromString;

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
    private Queue<FireEvent> fireEventQueue;
    private String droneId;
    private Location currentLocation; // current physical location
    private Location targetLocation; // target location for movement
    private Location baseLocation; // home base location
    private DroneSpecifications specifications; // specifications of drone
    
    // map to track drops by zone ID
    private static final Map<Integer, Integer> zoneDropsMap = new ConcurrentHashMap<>();

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
        this(serverIP, droneId, baseLocation, new DroneSpecifications());
    }
    
    /**
     * Constructor with drone ID, base location, and specifications
     * @param serverIP The IP address of the scheduler server
     * @param droneId The unique identifier for this drone
     * @param baseLocation The location of the drone's home base
     * @param specifications The technical specifications of the drone
     */
    public DroneSubsystem(InetAddress serverIP, String droneId, Location baseLocation, DroneSpecifications specifications) {
        // set the initial state of drone
        currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        this.droneId = droneId;
        this.baseLocation = baseLocation;
        this.currentLocation = baseLocation;
        this.targetLocation = baseLocation;
        this.specifications = specifications;
        
        try {
            // Create unique ports for each drone
            int droneNumber = 0;
            if (droneId.length() > 5) {
                droneNumber = Integer.parseInt(droneId.substring(5));
            }
            
            // use offset of 100 * droneNumber to ensure ports don't conflict
            int uniqueSendPort = sendPort + (droneNumber * 100);
            int uniqueReceivePort = receivePort + (droneNumber * 100);
            
            sendSocket = new DatagramSocket(uniqueSendPort);
            receieveSocket = new DatagramSocket(uniqueReceivePort);
            
            System.out.println(ConsoleColors.CYAN + 
                "[DRONE " + droneId + "] Initialized at " + baseLocation + 
                " using ports: send=" + uniqueSendPort + ", receive=" + uniqueReceivePort + 
                ConsoleColors.RESET);
                
            // display drone specifications
            System.out.println(ConsoleColors.CYAN + 
                "[DRONE " + droneId + "] Specifications: Max Speed=" + specifications.getMaxSpeed() + 
                " km/h, Flow Rate=" + specifications.getFlowRate() + " L/s, Capacity=" + 
                specifications.getCarryCapacity() + " L, Battery Life=" + specifications.getBatteryLife() + " min" +
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
        String status = droneId + " " + 
                        currentState.getClass().getSimpleName() + " " + 
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
        currentState.dropAgent(this);
        System.out.println(ConsoleColors.BLUE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
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
     * Gets the drone's specifications
     * 
     * @return the drone specifications
     */
    public DroneSpecifications getSpecifications() {
        return specifications;
    }
    
    /**
     * Sets the drone's specifications
     * 
     * @param specifications the new specifications
     */
    public void setSpecifications(DroneSpecifications specifications) {
        this.specifications = specifications;
    }
    
    /**
     * Records a drop for a zone and returns the current count
     * 
     * @param zoneId the zone ID where drop occurred
     * @return the updated drop count for the zone
     */
    public static int recordDropForZone(int zoneId) {
        return zoneDropsMap.compute(zoneId, (key, value) -> (value == null) ? 1 : value + 1);
    }
    
    /**
     * Gets the current drop count for a zone
     * 
     * @param zoneId the zone ID
     * @return the number of drops for the zone, or 0 if none
     */
    public static int getDropsForZone(int zoneId) {
        return zoneDropsMap.getOrDefault(zoneId, 0);
    }
    
    /**
     * Main program for droneStateMachines
     * */
    public static void main(String[] args) {
        try {
            // Create a shared specification object for all drones
            DroneSpecifications droneSpecs = new DroneSpecifications();
            
            // Number of drones to create
            final int NUM_DRONES = 10;
            
            // Arrays to store drone objects and threads
            DroneSubsystem[] drones = new DroneSubsystem[NUM_DRONES];
            Thread[] threads = new Thread[NUM_DRONES];
            
            // Create drones and threads in a loop
            for (int i = 0; i < NUM_DRONES; i++) {
                // Create drone with ID "drone1" through "drone10"
                String droneId = "drone" + (i + 1);
                
                // All drones start at same base location (0, 0)
                drones[i] = new DroneSubsystem(InetAddress.getLocalHost(), droneId, new Location(0, 0), droneSpecs);
                
                // Create a thread for each drone
                final int droneIndex = i; // Need final var for lambda
                threads[i] = new Thread(() -> runDrone(drones[droneIndex]));
                
                // Start the thread
                threads[i].start();
                
                // Larger delay between drone starts to avoid port conflicts
                Thread.sleep(500);
            }
            
            // Wait for all threads to complete
            for (int i = 0; i < NUM_DRONES; i++) {
                threads[i].join();
            }
            
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
                
                // Process events assigned to this drone (either as primary or as part of multi-drone response)
                String primaryDroneId = event.getAssignedDroneId();
                String thisDroneId = drone.getDroneId();
                
                // Check if this drone should respond - either it's assigned directly or it's one of multiple drones assigned
                if (primaryDroneId == null || 
                    primaryDroneId.equals(thisDroneId) || 
                    event.isDroneAssigned(thisDroneId)) {
                    
                    // For multi-drone responses, indicate which response number this is
                    if (event.getAssignedDroneCount() > 1) {
                        System.out.println(ConsoleColors.CYAN + "[DRONE " + thisDroneId + 
                                         "] Part of multi-drone response (" + 
                                         event.getAssignedDroneCount() + " drones total)" + 
                                         ConsoleColors.RESET);
                    }
                    
                    processEvent(drone, event);
                    // Send status update after event is processed
                    drone.sendStatusUpdate();
                } else {
                    System.out.println(ConsoleColors.YELLOW + "[DRONE " + thisDroneId + 
                                     "] Ignoring event assigned to " + primaryDroneId + 
                                     ConsoleColors.RESET);
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
            
            // ──────────────── MISSION START ─────────────────
            System.out.println(ConsoleColors.YELLOW + "\nDRONE " + droneId + ": Mission start to Zone " + zoneId +
                           " - " + severity + " fire" + ConsoleColors.RESET);
            
            // Update drone target location and schedule event
            drone.setTargetLocation(zoneLocation);
            drone.scheduleFireEvent(event);
            Thread.sleep(1000); // Preparation delay
            
            // Fly to zone
            simulateMovement(drone, zoneLocation);
            
            // Calculate firefighting duration based on severity and drone specifications
            int firefightingDuration = calculateFirefightingDuration(severity, drone);
            
            // Drop agent at target location
            drone.setCurrentLocation(zoneLocation);
            drone.dropAgent();
            System.out.println(ConsoleColors.RED + " DRONE " + droneId + ": Fighting fire in Zone " + zoneId +
                           " (" + (firefightingDuration/1000) + "s, flow rate: " + 
                           drone.getSpecifications().getFlowRate() + " L/s)" + ConsoleColors.RESET);
            Thread.sleep(firefightingDuration);
            
            // Determine if fire is fully extinguished or if drone has contributed its capacity
            int dronesNeeded = getRequiredDronesForSeverity(severity);
            boolean fullCapacityUsed = true; // Assume drone used full capacity
            
            // Increment the drops counter for this zone
            int dropCount = recordDropForZone(zoneId);
            
            if (dropCount >= dronesNeeded) {
                // If enough drones were dispatched, fire would be fully extinguished
                System.out.println(ConsoleColors.GREEN + "DRONE " + droneId + ": Fire extinguished in Zone " + zoneId +
                               " (Drops: " + dropCount + "/" + dronesNeeded + ")" +
                               ConsoleColors.RESET);
            } else {
                // Show progress with drops count
                System.out.println(ConsoleColors.YELLOW + "DRONE " + droneId + ": Fire partially contained in Zone " + zoneId +
                               " (Drops: " + dropCount + "/" + dronesNeeded + ")" +
                               ConsoleColors.RESET);
            }
            
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
            
            // Complete the task and perform maintenance
            drone.setCurrentLocation(drone.getBaseLocation());
            Thread.sleep(1000); // Shorter maintenance time
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
     * Calculate firefighting duration based on fire severity using drone specifications
     * 
     * @param severity the fire severity
     * @param drone the drone to use for calculations
     * @return duration in milliseconds
     */
    private static int calculateFirefightingDuration(String severity, DroneSubsystem drone) {
        // Use drone's specifications to calculate duration based on flow rate and nozzle open time
        return drone.getSpecifications().calculateFirefightingDuration(severity);
    }
    
    /**
     * Determines the number of drones required to extinguish a fire based on severity
     * 
     * @param severity the fire severity
     * @return the number of drones needed
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
     * Gets a zone location based on zone ID
     * 
     * @param zoneId the zone ID
     * @return the location of the zone center in meters
     */
    private static Location getZoneLocation(int zoneId) {
        // Create a grid of 700m x 600m zones (3 columns, 4 rows)
        int x = ((zoneId-1) % 3) * 700 + 350; // 700m wide zones, centered at x+350
        int y = ((zoneId-1) / 3) * 600 + 300; // 600m tall zones, centered at y+300
        return new Location(x, y);
    }
    
    /**
     * Simulates drone movement with simplified output, using drone specifications
     * 
     * @param drone the drone to move
     * @param targetLocation the target location
     */
    private static void simulateMovement(DroneSubsystem drone, Location targetLocation) throws InterruptedException {
        Location currentLocation = drone.getCurrentLocation();
        int distance = currentLocation.distanceTo(targetLocation);
        boolean isFaulted = drone.getCurrentStateName().equalsIgnoreCase("Fault");
        String droneId = drone.getDroneId();
        
        // If locations are the same, no movement needed
        if (distance == 0) return;
        
        // Get drone specifications
        DroneSpecifications specs = drone.getSpecifications();
        
        // Calculate travel time based on drone specs including acceleration/deceleration
        double maxSpeed = isFaulted ? specs.getMaxSpeed() * 0.5 : specs.getMaxSpeed(); // Reduced speed if faulted
        
        // Temporarily modify specifications if drone is faulted
        double originalMaxSpeed = specs.getMaxSpeed();
        if (isFaulted) {
            specs.setMaxSpeed(maxSpeed);
        }
        
        // Calculate travel time using drone specs (includes acceleration/deceleration)
        int travelTimeMs = specs.calculateTravelTime(distance);
        
        // Restore original max speed
        if (isFaulted) {
            specs.setMaxSpeed(originalMaxSpeed);
        }
        
        // Show flight start status
        String destinationType = targetLocation.equals(drone.getBaseLocation()) ? "base" : "zone";
        String speedStatus = isFaulted ? "reduced speed" : "normal speed";
        
        System.out.println(ConsoleColors.BLUE + 
            "DRONE " + droneId + ": Flying to " + destinationType + " (" +
            distance + " meters, " + String.format("%.1f", travelTimeMs/1000.0) + "s, " + 
            speedStatus + ", max speed: " + maxSpeed + " km/h)" + ConsoleColors.RESET);
        
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
        drone.setCurrentLocation(targetLocation);
        drone.sendStatusUpdate();
    }
}
