package controllers;


import models.DroneSpecifications;
import models.FireEvent;
import models.Location;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

import static models.FireEvent.createFireEventFromString;

import static models.FireEvent.ErrorType;

/**
 * ANSI colors for console output
 */
class ConsoleColors {
    // Colors
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
//        System.out.println("preparing to handle new fire event: " + event);
        context.setState(new EnRoute());
    }
    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
//        System.out.println("drone is idle, nothing to drop");
    }

    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
//        System.out.println("drone is idle and stationed at base.");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneSubsystem context) {
//        System.out.println("drone has not faulted");
    }

    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
//        System.out.println("drone is idle, no tasks have been completed");
    }

    /**
     * Display the state of drone
     *
     * */

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.BOLD_YELLOW + "IDLE" + ConsoleColors.RESET);
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
//        System.out.println("Drone is en route, already in motion of handling an event");

    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
//        System.out.println("drone is en route, cannot drop agent yet");
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
//        System.out.println("drone is en route, has not returned yet"); //
    }


    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
//        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
//        System.out.println("drone is en route, no tasks have been completed yet");
    }


    /**
     * Current state of drone
     * */
    @Override
    public void displayState() {
        System.out.println(ConsoleColors.BOLD_GREEN + "EN ROUTE" + ConsoleColors.RESET);
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
//        System.out.println("drone is dropping an agent and already handling task");
    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneSubsystem context) {
//        System.out.println("drone is currently dropping an agent");
    }

    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
//        System.out.println("drone is dropping an agent and has not yet returned to its base");
        context.setState(new ArrivedToBase());
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneSubsystem context) {
//        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneSubsystem context) {
//        System.out.println("drone is dropping an agent and has not yet completed its task");

    }

    /**
     * Current drone state
     *
     * */

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.BOLD_ORANGE + "DROPPING AGENT" + ConsoleColors.RESET);
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
//        System.out.println("drone has arrived to base and cannot accept new task");

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
//        System.out.println("drone has arrived to base and has already dropped agent");
    }

    /**
     * Drone status returning to base
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneSubsystem context) {
//        System.out.println("drone has already arrived to base");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
//        System.out.println("drone has arrived to base and not yet faulted");
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
//        System.out.println("drone has arrived to base and completed its task");
        context.setState(new Idle());
    }


    /**
     * Current status of drone
     *
     * */
    @Override
    public void displayState() {
        System.out.println(ConsoleColors.BOLD_PURPLE + "ARRIVED TO BASE" + ConsoleColors.RESET);
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
//        System.out.println("drone has faulted and cannot handle event");
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
//        System.out.println("drone nozzle/foam cannot open");

    }
    /**
     * Drone arrival status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void returningBack(DroneSubsystem context) {
//        System.out.println("drone has faulted and did not move");

    }

    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneSubsystem context) {
//        System.out.println("drone has faulted");
    }

    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     * */
    @Override
    public void taskCompleted(DroneSubsystem context) {
//        System.out.println("Drone has faulted and returned to base. Now setting to idle.");
        context.setState(new Idle());  // Once at base, transition to idle
    }

    /**
     * Current state of drone
     * **/

    @Override
    public void displayState() {
        System.out.println(ConsoleColors.BOLD_RED + "FAULTED" + ConsoleColors.RESET);
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
    private FireEvent currentEvent; // current event being processed

    // map to track drops by zone ID
    private static final Map<Integer, Integer> zoneDropsMap = new ConcurrentHashMap<>();

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
        //set the initial state of drone
        currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        this.droneId = droneId;
        this.baseLocation = baseLocation;
        this.currentLocation = baseLocation;
        this.targetLocation = baseLocation;
        this.specifications = specifications;
        
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
            
            System.out.println(ConsoleColors.TEAL +
                "[" + droneId.toUpperCase() + "] Initialized at: " + ConsoleColors.BLUE + baseLocation +
                    ConsoleColors.TEAL + " Ports:" + ConsoleColors.BLUE + " send=" + uniqueSendPort + ", receive=" + uniqueReceivePort +
                ConsoleColors.RESET);
                
            // Display drone specifications
            System.out.println(ConsoleColors.TEAL +
                    "[" + droneId.toUpperCase() + "] Specifications:" + ConsoleColors.BLUE + " Max Speed=" + specifications.getMaxSpeed() +
                " km/h, Flow Rate=" + specifications.getFlowRate() + " L/s, Capacity=" + 
                specifications.getCarryCapacity() + " L, Battery Life=" + specifications.getBatteryLife() + " min" +
                ConsoleColors.RESET);
        } catch (SocketException e) {
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Socket error: " + e.getMessage() + ConsoleColors.RESET);
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

        // Simulate 5% packet loss
        if (Math.random() < 0.05) {  // 5% chance of packet loss
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Packet lost!" + ConsoleColors.RESET);
            
            // Always successfully recover the packet after a 2-second delay
            try {
                System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Attempting to recover packet... waiting 2 seconds" + ConsoleColors.RESET);
                Thread.sleep(2000);  // Pause for 2 seconds to simulate recovery attempt
                
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered packet" + ConsoleColors.RESET);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Recovery interrupted" + ConsoleColors.RESET);
                return null;
            }
        }

        try {
            receieveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("receieve error: " + e);
        }

        // Simulate 5% message corruption
        if (Math.random() < 0.05) {  // 5% chance of corruption
            int corruptIndex = (int) (Math.random() * data.length);
            data[corruptIndex] = (byte) (Math.random() * 256);  // Randomly change a byte
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Message corrupted!" + ConsoleColors.RESET);
            
            // Always successfully recover from corruption after a 2-second delay
            try {
                System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Attempting to recover from corruption... waiting 2 seconds" + ConsoleColors.RESET);
                Thread.sleep(2000);  // Pause for 2 seconds to simulate recovery attempt
                
                // Re-receive the packet after corruption is detected
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered from corruption" + ConsoleColors.RESET);
                try {
                    // Create a new packet to receive the re-sent data
                    data = new byte[100];
                    receivePacket = new DatagramPacket(data, data.length);
                    receieveSocket.receive(receivePacket);
                } catch (IOException e) {
                    System.out.println("Corruption recovery error: " + e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Corruption recovery interrupted" + ConsoleColors.RESET);
            }
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        System.out.println(ConsoleColors.TEAL + "[" + droneId.toUpperCase() + "] Received packet: " + ConsoleColors.BLUE + r + ConsoleColors.RESET);
        return createFireEventFromString(r);
    }


    /**
     * Sends a message to the specified port
     *
     * @param message The message to send
     * @param port The port to send to
     */
    public void send(String message, int port) {
        // Simulate 5% packet loss
        if (Math.random() < 0.05) {  // 5% chance of packet loss
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Packet lost during send!" + ConsoleColors.RESET);
            
            // Always successfully recover the transmission after a 2-second delay
            try {
                System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Attempting to recover transmission... waiting 2 seconds" + ConsoleColors.RESET);
                Thread.sleep(2000);  // Pause for 2 seconds to simulate recovery attempt
                
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered transmission" + ConsoleColors.RESET);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Recovery interrupted" + ConsoleColors.RESET);
                return;
            }
        }

        byte[] msg = message.getBytes();

        // Simulate 5% message corruption
        if (Math.random() < 0.05) {  // 5% chance of corruption
            int corruptIndex = (int) (Math.random() * msg.length);
            msg[corruptIndex] = (byte) (Math.random() * 256);  // Randomly change a byte
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Message corrupted!" + ConsoleColors.RESET);
            
            // Always successfully recover from corruption after a 2-second delay
            try {
                System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Attempting to recover from corruption... waiting 2 seconds" + ConsoleColors.RESET);
                Thread.sleep(2000);  // Pause for 2 seconds to simulate recovery attempt
                
                // Restore the original message after corruption recovery
                msg = message.getBytes();  // Get a fresh copy of the bytes
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered from corruption" + ConsoleColors.RESET);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Corruption recovery interrupted" + ConsoleColors.RESET);
            }
        }

        sendPacket = new DatagramPacket(msg, msg.length, serverIP, port);
        System.out.println(ConsoleColors.TEAL + "[" + droneId.toUpperCase() + "] Sending: " + ConsoleColors.BLUE + message + ConsoleColors.RESET);
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
        // Always include error info if there is an error
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        
        // Include task information if drone has a task, especially important for hard faults
        String taskInfo = "";
        if (currentEvent != null) {
            taskInfo = " TASK:" + currentEvent.getZoneID() + ":" + currentEvent.getSeverity();
        } 
        // Then check queue if no current task
        else if (fireEventQueue.peek() != null) {
            FireEvent event = fireEventQueue.peek();
            taskInfo = " TASK:" + event.getZoneID() + ":" + event.getSeverity();
        }
        
        String status = droneId + " " +
                currentState.getClass().getSimpleName() + errorInfo + taskInfo + " " +
                currentLocation.getX() + " " +
                currentLocation.getY();
        send(status, 6001); // Send to scheduler
    }
    
    /**
     * Sends a status update to the scheduler that includes fire extinguished information
     * @param zoneId The zone ID where fire was extinguished
     */
    public void sendFireExtinguishedStatus(int zoneId) {
        // Always include error info if there is an error
        String errorInfo = hasError() ? " ERROR:" + getCurrentError() : "";
        
        // Include the fire extinguished marker
        String fireOutInfo = " FIRE_OUT:" + zoneId;
        
        String status = droneId + " " +
                currentState.getClass().getSimpleName() + errorInfo + fireOutInfo + " " +
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
        System.out.print(ConsoleColors.BOLD_WHITE + "\n["+ droneId.toUpperCase() + "] State before: " + ConsoleColors.RESET);
        currentState.displayState();

        // Check if the event has an error
        if(event.hasError()){
            setError(event.getError());
            // Error message is already printed in setError method
        }
        currentState.handleFireEvent(this, event);
        System.out.print(ConsoleColors.BOLD_WHITE + "["+ droneId.toUpperCase() + "] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }
    /**
     * Drone dropping agent
     * */
    public void dropAgent() {
        System.out.print(ConsoleColors.BOLD_WHITE + "\n["+ droneId.toUpperCase() + "] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        //Start drop agent timer
        startDropAgentTimer();

        currentState.dropAgent(this);

        //Check for drop agent timeout - simulate nozzle jam with 5% probability
        if (isDropAgentTimedOut() && Math.random() < 0.05) {
            // This is a hard fault and will be handled by the setError method
            setError(ErrorType.NOZZLE_JAM);
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Nozzle jammed during agent drop - timeout exceeded" +
                    ConsoleColors.RESET);
        }
        // For all other errors during the drop, simulate a soft fault (e.g., momentary pressure drop)
        else if (isDropAgentTimedOut()) {
            // Simulate a soft fault that can be recovered from
            System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Minor drop system fault detected - attempting recovery..." +
                    ConsoleColors.RESET);
            try {
                Thread.sleep(2000);  // 2-second recovery delay
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered from minor drop system fault" +
                        ConsoleColors.RESET);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Display final state after potential error handling
        System.out.print(ConsoleColors.BOLD_WHITE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();

        //Reset timer
        resetTimers();
    }

    /**
     * Drone returning to base
     * */
    public void returningBack() {
        System.out.print(ConsoleColors.BOLD_WHITE + "\n["+ droneId.toUpperCase() + "] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.returningBack(this);
        System.out.print(ConsoleColors.BOLD_WHITE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }

    /**
     * Drone having a fault
     * **/
    public void droneFaulted(){
        System.out.print(ConsoleColors.BOLD_WHITE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.droneFaulted(this);
        System.out.print(ConsoleColors.BOLD_WHITE + "[DRONE] State after: " + ConsoleColors.RESET);
        currentState.displayState();
    }

    /**
     * Drone completing task
     * */
    public void taskCompleted() {
        System.out.print(ConsoleColors.BOLD_WHITE + "\n[DRONE] State before: " + ConsoleColors.RESET);
        currentState.displayState();
        currentState.taskCompleted(this);
        System.out.print(ConsoleColors.BOLD_WHITE + "[DRONE] State after: " + ConsoleColors.RESET);
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
        // Only log if this is a new error or changing error types
        if (this.currentError != errorType) {
            this.currentError = errorType;
            System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] ERROR DETECTED: " +
                    errorType + ConsoleColors.RESET);
            
            // Handle different error types
            if (errorType == ErrorType.NOZZLE_JAM) {
                // Handle NOZZLE_JAM as a hard fault
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] HARD FAULT: Shutting down drone" +
                        ConsoleColors.RESET);
                // Set to faulted state first so status update shows faulted state
                droneFaulted();
                // Send status update with mission information to notify scheduler to find replacement
                sendStatusUpdate();
            } else if (errorType == ErrorType.COMMUNICATION_FAILURE || errorType == ErrorType.DRONE_STUCK || 
                     errorType == ErrorType.DOOR_STUCK || errorType == ErrorType.ARRIVAL_SENSOR_FAILED) {
                // Handle soft faults with recovery
                try {
                    System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] SOFT FAULT: Attempting recovery... waiting 2 seconds" +
                            ConsoleColors.RESET);
                    Thread.sleep(2000);  // Pause for 2 seconds to recover
                    
                    // Clear the error after recovery delay
                    System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Successfully recovered from " + errorType +
                            ConsoleColors.RESET);
                    this.currentError = ErrorType.NONE;
                    
                    // Send status update to notify about recovery
                    sendStatusUpdate();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Fault recovery interrupted" + ConsoleColors.RESET);
                }
            }
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
//            System.out.println("Drone is idle and ready for new fire event");
            handleFireEvent(event);
        } else{
//            System.out.println("Drone is not idle and cannot handle a new fire event" + event);
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
                if (event == null) {
                    System.out.println(ConsoleColors.RED + "[" + drone.getDroneId() + "] Event is null, skipping." + ConsoleColors.RESET);
                    return;  // Skip processing this event
                }
                // Process events assigned to this drone (either as primary or as part of multi-drone response)
                String primaryDroneId = event.getAssignedDroneId();
                String thisDroneId = drone.getDroneId();

                // Check if this drone should respond - either it's assigned directly or it's one of multiple drones assigned
                if (primaryDroneId == null ||
                        primaryDroneId.equals(thisDroneId) ||
                        event.isDroneAssigned(thisDroneId)) {

                    // For multi-drone responses, indicate which response number this is
                    if (event.getAssignedDroneCount() > 1) {
                        System.out.println(ConsoleColors.CYAN + thisDroneId.toUpperCase() +
                                         "] Part of multi-drone response (" + 
                                         event.getAssignedDroneCount() + " drones total)" + 
                                         ConsoleColors.RESET);
                    }

                    processEvent(drone, event);
                    // Send status update after event is processed
                    drone.sendStatusUpdate();
                } else {
                    System.out.println(ConsoleColors.YELLOW + thisDroneId.toUpperCase() +
                                     "] Ignoring event assigned to " + primaryDroneId + 
                                     ConsoleColors.RESET);
                }
            }
        } catch (Exception e) {
            System.out.println("Error in drone thread for " + drone.getDroneId().toUpperCase() + ": " + e);
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
                // Set the error first - this will log the error detection and handle recovery for soft faults
                drone.setError(event.getError());

                //if it's a hard fault like nozzle/bay door fault, abort mission!
                if (event.getError() == ErrorType.NOZZLE_JAM) {
                    System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Hard fault detected, aborting mission" +
                            ConsoleColors.RESET);
                    // Set to faulted state
                    drone.droneFaulted();
                    // Send status update with task information to notify scheduler to find replacement
                    drone.sendStatusUpdate();
                    return;
                }
                // For soft faults, we can continue after recovery (handled in setError)
                else {
                    System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Soft fault recovered, continuing mission" +
                            ConsoleColors.RESET);
                    // No return here, continue with the mission after soft fault recovery
                }
            }

            // Update drone target location and store current event
            drone.setTargetLocation(zoneLocation);
            drone.currentEvent = event;
            drone.scheduleFireEvent(event);
            Thread.sleep(1000); // Preparation delay

            // Fly to zone
            simulateMovement(drone, zoneLocation);

            //Check if movement failed - already handled in setError method now
            if(drone.hasError() && drone.getCurrentError() == ErrorType.DRONE_STUCK){
                // We don't need to do anything special here anymore since setError handles recovery
                // The 2-second delay is already applied in the setError method
                System.out.println(ConsoleColors.YELLOW + "[" + droneId.toUpperCase() + "] Movement fault detected, but will recover automatically" +
                        ConsoleColors.RESET);
                // Don't return - continue with the mission after recovery
            }

            // Calculate firefighting duration based on severity and drone specifications
            int firefightingDuration = calculateFirefightingDuration(severity, drone);

            // Drop agent at target location
            drone.setCurrentLocation(zoneLocation);
            drone.dropAgent();

            System.out.println(ConsoleColors.BOLD_RED + "[" + droneId.toUpperCase() + "] Fighting fire in Zone " + zoneId + ConsoleColors.RESET);

            // Check if nozzle/bay door fault occurred
            if (drone.hasError() && (drone.getCurrentError() == ErrorType.NOZZLE_JAM)){
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Nozzle/bay door fault detected, cannot drop agent" +
                        ConsoleColors.RESET);
                // Set to faulted state first
                drone.droneFaulted();
                // Send status update with task information to notify scheduler to find replacement
                drone.sendStatusUpdate();
                return;
            }

            // Simulate water tank emptying
            Thread.sleep(firefightingDuration);

            // Determine if fire is fully extinguished or if drone has contributed its capacity
            int dronesNeeded = getRequiredDronesForSeverity(severity);
            boolean fullCapacityUsed = true; // Assume drone used full capacity

            // Increment the drops counter for this zone - using static method for backward compatibility
            int dropCount = recordDropForZone(zoneId);
            
            boolean isExtinguished = (dropCount >= dronesNeeded);
            if (isExtinguished) {
                // If enough drones were dispatched, fire would be fully extinguished
                System.out.println(ConsoleColors.BOLD_LIME + "[" + droneId.toUpperCase() + "] Fire extinguished in Zone " + zoneId +
                               " (Drops: " + dropCount + "/" + dronesNeeded + ")" +
                               ConsoleColors.RESET);
                
                // Update status to include the fire extinguished flag
                drone.sendFireExtinguishedStatus(zoneId);
            } else {
                // Show progress with drops count
                System.out.println(ConsoleColors.BOLD_YELLOW + "[" + droneId.toUpperCase() + "] Fire partially contained in Zone " + zoneId +
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
              
                System.out.println(ConsoleColors.RED + droneId.toUpperCase() + ": Malfunction detected!" +
                               ConsoleColors.RESET);

                Thread.sleep(2000);
            }

            // Return flight
            simulateMovement(drone, drone.getBaseLocation());

            // Check if movement failed during return
            if (drone.hasError() && drone.getCurrentError() == ErrorType.DRONE_STUCK) {
                System.out.println(ConsoleColors.RED + "[" + droneId.toUpperCase() + "] Movement fault detected during return, cannot reach base" +
                        ConsoleColors.RESET);
                drone.droneFaulted();
                return;
            }

            // Complete the task and perform maintenance
            drone.setCurrentLocation(drone.getBaseLocation());
            Thread.sleep(1000); // Shorter maintenance time

            // Clear any non-hard faults when returning to base
            if (drone.hasError() && drone.getCurrentError() != ErrorType.NOZZLE_JAM) {
                System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Non-hard fault cleared during maintenance" +
                        ConsoleColors.RESET);
                drone.clearError();
            }
            
            // Clear current event when mission is done
            drone.currentEvent = null;
            drone.taskCompleted();

            System.out.println(ConsoleColors.GREEN + "[" + droneId.toUpperCase() + "] Mission complete, ready for next assignment\n" +
                           ConsoleColors.RESET);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(ConsoleColors.RED + "[" + drone.getDroneId().toUpperCase() + "] Mission interrupted" +
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

        //Start movement timer
        drone.startMovementTimer();
        // If locations are the same, no movement needed
        if (distance == 0){
            drone.resetTimers();
            return;
        }

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
        
        System.out.println(ConsoleColors.LAVENDER + "[" + droneId.toUpperCase() + "] Flying to " + destinationType + " (" +
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
}
