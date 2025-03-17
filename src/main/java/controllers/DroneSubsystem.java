package controllers;

import models.FireEvent;

import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static models.FireEvent.createFireEventFromString;

/**
 * Interface for different states of the drone
 */
interface DroneState {
    void handleFireEvent(DroneSubsystem context, FireEvent event);
    void dropAgent(DroneSubsystem context);
    void returningBack(DroneSubsystem context);
    void droneFaulted(DroneSubsystem context);
    void taskCompleted(DroneSubsystem context);
    void displayState();
    String getName();
}

/**
 * Base class for all drone states
 */
abstract class BaseState implements DroneState {
    @Override
    public String getName() {
        return this.getClass().getSimpleName().toUpperCase();
    }
}

/**
 * This class is the idle state for Drone
 */
class Idle extends BaseState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.logInfo("Preparing to handle new fire event: " + event);
        context.setCurrentZone(0); // Start from base
        context.setTargetZone(event.getZoneID());
        context.setState(new EnRoute());
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.logInfo("Drone is idle, nothing to drop");
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.logInfo("Drone is idle and stationed at base");
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.logInfo("Drone has not faulted");
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.logInfo("Drone is idle, no tasks have been completed");
    }

    @Override
    public void displayState() {
        System.out.println("IDLE");
    }
}

/**
 * Class for enroute state of drone
 */
class EnRoute extends BaseState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.logInfo("Drone is en route, already handling an event");
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.logInfo("Drone is en route, now preparing to drop agent");
        // Update current zone to the target zone once we arrive
        context.setCurrentZone(context.getTargetZone());
        context.setState(new droppingAgent());
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.logInfo("Drone is en route, has not returned yet");
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.logInfo("Drone has not faulted");
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.logInfo("Drone is en route, no tasks have been completed yet");
    }

    @Override
    public void displayState() {
        System.out.println("EN ROUTE");
    }
}

/**
 * Class for dropping agent state
 */
class droppingAgent extends BaseState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.logInfo("Drone is dropping an agent and already handling task");
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.logInfo("Drone is currently dropping an agent");
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.logInfo("Drone has dropped agent, now returning to base");
        context.setState(new ArrivedToBase());
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.logInfo("Drone has not faulted");
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.logInfo("Drone is dropping an agent and has not yet completed its task");
    }

    @Override
    public void displayState() {
        System.out.println("DROPPING AGENT");
    }
}

/**
 * Class for drone ArriveToBase state
 */
class ArrivedToBase extends BaseState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.logInfo("Drone has arrived to base and cannot accept new task");
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.logInfo("Drone has arrived to base and has already dropped agent");
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.logInfo("Drone has already arrived to base");
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.logInfo("Drone has encountered a fault while at base");
        context.setState(new Fault());
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.logInfo("Drone has arrived to base and completed its task");
        context.setCurrentZone(0); // Base is zone 0
        context.incrementServicesCompleted();
        context.setState(new Idle());
    }

    @Override
    public void displayState() {
        System.out.println("ARRIVED TO BASE");
    }
}

/**
 * This class is the fault state for Drone
 */
class Fault extends BaseState {
    @Override
    public void handleFireEvent(DroneSubsystem context, FireEvent event) {
        context.logInfo("Drone has faulted and cannot handle event");
    }

    @Override
    public void dropAgent(DroneSubsystem context) {
        context.logInfo("Drone nozzle/foam cannot open");
    }

    @Override
    public void returningBack(DroneSubsystem context) {
        context.logInfo("Drone has faulted and did not move");
    }

    @Override
    public void droneFaulted(DroneSubsystem context) {
        context.logInfo("Drone has already faulted");
    }

    @Override
    public void taskCompleted(DroneSubsystem context) {
        context.logInfo("Drone fault has been resolved, now setting to idle");
        context.setState(new Idle());
    }

    @Override
    public void displayState() {
        System.out.println("FAULTED");
    }
}

/**
 * DroneStateMachines class for switching drone states
 */
public class DroneSubsystem {
    private final int droneId;
    private DroneState currentState;
    private Queue<FireEvent> fireEventQueue;
    private int currentZone = 0; // Start at base
    private int targetZone = 0;
    private int servicesCompleted = 0;

    private final InetAddress serverIP;

    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receiveSocket, statusSocket;

    private final int receivePort;
    private final int sendPort;
    private final int statusPort = 6002; // Fixed port for drone status updates
    private boolean running = true;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor
     * @param droneId The unique identifier for this drone
     * @param serverIP The IP address of the scheduler server
     * @param receivePort The port on which this drone receives messages
     */
    public DroneSubsystem(int droneId, InetAddress serverIP, int receivePort) {
        this.droneId = droneId;
        this.currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        this.receivePort = receivePort;
        this.sendPort = 6001; // Scheduler receive port

        try {
            // Initialize sockets with proper error handling
            try {
                sendSocket = new DatagramSocket();
                System.out.println("Drone " + droneId + " send socket created on port " + sendSocket.getLocalPort());
            } catch (SocketException e) {
                System.err.println("Error creating send socket for Drone " + droneId + ": " + e.getMessage());
                throw e; // Re-throw to be caught by outer try-catch
            }

            try {
                receiveSocket = new DatagramSocket(receivePort);
                System.out.println("Drone " + droneId + " receive socket created on port " + receivePort);
            } catch (SocketException e) {
                System.err.println("Error creating receive socket for Drone " + droneId + " on port " + receivePort);
                System.err.println("The port " + receivePort + " may already be in use. Try a different port.");
                throw e; // Re-throw to be caught by outer try-catch
            }

            try {
                statusSocket = new DatagramSocket();
                System.out.println("Drone " + droneId + " status socket created on port " + statusSocket.getLocalPort());
            } catch (SocketException e) {
                System.err.println("Error creating status socket for Drone " + droneId + ": " + e.getMessage());
                throw e; // Re-throw to be caught by outer try-catch
            }

        } catch (SocketException e) {
            logError("Socket initialization error", e);
            System.err.println("Fatal error initializing sockets for Drone " + droneId + ". Exiting.");
            System.exit(1);
        }
    }

    /**
     * Gets the drone's unique ID
     * @return The drone ID
     */
    public int getDroneId() {
        return droneId;
    }

    /**
     * Gets the drone's current zone location
     * @return The zone ID
     */
    public int getCurrentZone() {
        return currentZone;
    }

    /**
     * Sets the drone's current zone location
     * @param zoneId The zone ID
     */
    public void setCurrentZone(int zoneId) {
        this.currentZone = zoneId;
        sendStatusUpdate();
    }

    /**
     * Gets the drone's target zone
     * @return The target zone ID
     */
    public int getTargetZone() {
        return targetZone;
    }

    /**
     * Sets the drone's target zone
     * @param zoneId The target zone ID
     */
    public void setTargetZone(int zoneId) {
        this.targetZone = zoneId;
    }

    /**
     * Gets the number of fire events this drone has serviced
     * @return The count of completed services
     */
    public int getServicesCompleted() {
        return servicesCompleted;
    }

    /**
     * Increments the count of completed services
     */
    public void incrementServicesCompleted() {
        this.servicesCompleted++;
        sendStatusUpdate();
    }

    /**
     * Logs information messages with timestamp
     * @param message The message to log
     */
    public void logInfo(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String stateName = currentState.getName();
        System.out.println("[" + timestamp + "][DRONE " + droneId + "][" + stateName + "] " + message);
    }

    /**
     * Logs state transition messages
     * @param fromState Previous state
     * @param toState New state
     */
    private void logStateTransition(String fromState, String toState) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println("[" + timestamp + "][DRONE " + droneId + "][STATE CHANGE] " + fromState + " → " + toState);
    }

    /**
     * Logs error messages with timestamp
     * @param message The error message
     * @param e The exception that occurred
     */
    private void logError(String message, Exception e) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.err.println("[" + timestamp + "][DRONE " + droneId + "][ERROR] " + message + ": " + e.getMessage());
    }

    /**
     * Receives a packet from the socket and attempts to parse it as a FireEvent
     * @return FireEvent if successfully parsed, null otherwise
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            logError("Receive error", e);
            return null;
        }

        int len = receivePacket.getLength();
        String message = new String(data, 0, len);
        logInfo("RECEIVED: " + message);

        try {
            return createFireEventFromString(message);
        } catch (Exception e) {
            logError("Error parsing message as FireEvent", e);
            return null;
        }
    }

    /**
     * Sends a message through the UDP socket
     * @param message The message to send
     * @param port The port to send to
     */
    public void send(String message, int port) {
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, serverIP, port);
            logInfo("SENDING to port " + port + ": " + message);
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            logError("Send error", e);
        }
    }

    /**
     * Sends a status update to the scheduler
     * Format: STATUS droneId currentZone state available
     */
    public void sendStatusUpdate() {
        boolean available = currentState instanceof Idle;
        String message = "STATUS " + droneId + " " + currentZone + " " +
                currentState.getName() + " " + available;

        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, serverIP, statusPort);
            logInfo("SENDING STATUS to port " + statusPort + ": " + message);
            statusSocket.send(sendPacket);
        } catch (IOException e) {
            logError("Send error", e);
        }
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
     */
    public void handleFireEvent(FireEvent event) {
        String fromState = currentState.getName();
        logInfo("Handling fire event: " + event);

        currentState.handleFireEvent(this, event);

        // If the state changed, no need to log it again as setState() handles that
    }

    /**
     * Drone dropping agent
     */
    public void dropAgent() {
        logInfo("Attempting to drop fire suppressant agent");
        currentState.dropAgent(this);
    }

    /**
     * Drone returning to base
     */
    public void returningBack() {
        logInfo("Attempting to return to base");
        currentState.returningBack(this);
    }

    /**
     * Drone having a fault
     */
    public void droneFaulted() {
        logInfo("Checking drone fault status");
        currentState.droneFaulted(this);
    }

    /**
     * Drone completing task
     */
    public void taskCompleted() {
        logInfo("Completing current task");
        currentState.taskCompleted(this);

        // Process next event in queue if available
        if (!fireEventQueue.isEmpty()) {
            logInfo("Processing next fire event in queue (" + fireEventQueue.size() + " remaining)");
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
     */
    public void setState(DroneState state) {
        String fromState = currentState.getName();
        String toState = state.getName();

        if (this.currentState instanceof Fault && !(state instanceof Idle)) {
            logInfo("Drone faulted. Returning to base before going idle");
            this.currentState = new ArrivedToBase();
            logStateTransition(fromState, "ARRIVED_TO_BASE");
        } else {
            this.currentState = state;
            logStateTransition(fromState, toState);
        }

        // Send a status update whenever state changes
        sendStatusUpdate();
    }

    /**
     * Schedule a fire event for processing
     * @param event The fire event to schedule
     */
    public void scheduleFireEvent(FireEvent event) {
        if (currentState instanceof Idle) {
            logInfo("Drone is idle and ready for new fire event");
            handleFireEvent(event);
        } else {
            logInfo("Drone is busy, queuing event for later: " + event);
            fireEventQueue.add(event);
        }
    }

    /**
     * Processes an event by scheduling it and executing the standard sequence of drone actions.
     * Adds a random delay based on travel time calculation.
     */
    private void processEvent(FireEvent event) {
        logInfo("=============================================");
        logInfo("STARTING EVENT PROCESSING: " + event);
        logInfo("=============================================");

        // Calculate travel time based on zones
        int travelTime = Math.abs(event.getZoneID() - currentZone) * 500;  // 500ms per zone
        logInfo("Calculated travel time: " + travelTime + "ms");

        scheduleFireEvent(event);

        // Simulate travel time
        try {
            logInfo("Traveling to zone " + event.getZoneID() + "...");
            Thread.sleep(travelTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Travel interrupted", e);
        }

        dropAgent();

        // Simulate agent drop time
        try {
            logInfo("Dropping fire suppressant agent...");
            Thread.sleep(1000);  // 1 second to drop agent
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Agent drop interrupted", e);
        }

        returningBack();

        // Simulate return time
        try {
            logInfo("Returning to base...");
            Thread.sleep(travelTime);  // Same time to return
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Return interrupted", e);
        }

        if ("DRONE_FAULT".equalsIgnoreCase(event.getEventType())) {
            logInfo("DRONE FAULT detected in event, initiating fault procedure");
            droneFaulted();

            // Simulate fault resolution time
            try {
                logInfo("Resolving drone fault...");
                Thread.sleep(3000);  // 3 seconds to fix
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logError("Fault resolution interrupted", e);
            }
        }

        taskCompleted();

        logInfo("=============================================");
        logInfo("EVENT PROCESSING COMPLETE: " + event);
        logInfo("=============================================");

        // Acknowledge completion
        send("Processed fire event: " + event.toString(), sendPort);
    }

    /**
     * Run the drone state machine continuously
     */
    public void run() {
        logInfo("=====================================================================");
        logInfo("STARTING CONTINUOUS OPERATION - WAITING FOR FIRE EVENTS");
        logInfo("DRONE ID: " + droneId + " LISTENING ON PORT: " + receivePort);
        logInfo("=====================================================================");

        // Send initial status update
        sendStatusUpdate();

        while (running) {
            try {
                logInfo("Waiting for incoming fire events...");
                FireEvent event = receive();

                if (event != null) {
                    logInfo("Valid fire event received, beginning processing");
                    processEvent(event);

                    // Send acknowledgment to scheduler
                    send("Received fire event from scheduler", sendPort);
                }

                // Short pause to prevent high CPU usage
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logError("Interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logError("Error processing events", e);
                // Continue running even if there's an error
            }
        }
    }

    /**
     * Stop the continuous operation
     */
    public void stop() {
        running = false;
        logInfo("Shutdown requested");
    }

    /**
     * Main program for droneStateMachines
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments for drone ID and port
            int droneId = 1;  // Default drone ID
            int receivePort = 7001;  // Default receive port

            if (args.length >= 1) {
                droneId = Integer.parseInt(args[0]);
            }

            if (args.length >= 2) {
                receivePort = Integer.parseInt(args[1]);
            }

            DroneSubsystem drone = new DroneSubsystem(droneId, InetAddress.getLocalHost(), receivePort);

            // Run continuously
            drone.run();

            // The following code will only execute if run() method completes or throws an exception
            drone.logInfo("Shutting down");
            if (drone.sendSocket != null) drone.sendSocket.close();
            if (drone.receiveSocket != null) drone.receiveSocket.close();
            if (drone.statusSocket != null) drone.statusSocket.close();

        } catch (UnknownHostException e) {
            System.err.println("[DRONE][ERROR] Unknown host error: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("[DRONE][ERROR] Invalid command line arguments: " + e.getMessage());
            System.err.println("Usage: java DroneStateMachines [droneId] [receivePort]");
        }
    }
}