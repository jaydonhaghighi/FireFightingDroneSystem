package controllers;

import models.FireEvent;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.net.*;
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
    // Current state of drone
    private DroneState currentState;
    private Queue<FireEvent> fireEventQueue;

    private final InetAddress serverIP;

    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receieveSocket;

    private final int sendPort = 7000;
    private final int receivePort = 7001;
    private boolean running = true;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor
     */
    public DroneSubsystem(InetAddress serverIP) {
        // Set the initial state of drone
        currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();
        this.serverIP = serverIP;
        try {
            sendSocket = new DatagramSocket(sendPort);
            receieveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            logError("Socket initialization error", e);
        }
    }

    /**
     * Logs information messages with timestamp
     * @param message The message to log
     */
    public void logInfo(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String stateName = currentState.getName();
        System.out.println("[" + timestamp + "][DRONE][" + stateName + "] " + message);
    }

    /**
     * Logs state transition messages
     * @param fromState Previous state
     * @param toState New state
     */
    private void logStateTransition(String fromState, String toState) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println("[" + timestamp + "][DRONE][STATE CHANGE] " + fromState + " → " + toState);
    }

    /**
     * Logs error messages with timestamp
     * @param message The error message
     * @param e The exception that occurred
     */
    private void logError(String message, Exception e) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.err.println("[" + timestamp + "][DRONE][ERROR] " + message + ": " + e.getMessage());
    }

    /**
     * Receives a packet from the socket and attempts to parse it as a FireEvent
     * @return FireEvent if successfully parsed, null otherwise
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receieveSocket.receive(receivePacket);
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
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), port);
            logInfo("SENDING: " + message);
            sendSocket.send(sendPacket);
        } catch (UnknownHostException e) {
            logError("Cannot find host", e);
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
        String fromState = currentState.getName();
        logInfo("Attempting to drop fire suppressant agent");

        currentState.dropAgent(this);

        // If the state changed, no need to log it again as setState() handles that
    }

    /**
     * Drone returning to base
     */
    public void returningBack() {
        String fromState = currentState.getName();
        logInfo("Attempting to return to base");

        currentState.returningBack(this);

        // If the state changed, no need to log it again as setState() handles that
    }

    /**
     * Drone having a fault
     */
    public void droneFaulted() {
        String fromState = currentState.getName();
        logInfo("Checking drone fault status");

        currentState.droneFaulted(this);

        // If the state changed, no need to log it again as setState() handles that
    }

    /**
     * Drone completing task
     */
    public void taskCompleted() {
        String fromState = currentState.getName();
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
     */
    private void processEvent(FireEvent event) {
        logInfo("=============================================");
        logInfo("STARTING EVENT PROCESSING: " + event);
        logInfo("=============================================");

        scheduleFireEvent(event);
        dropAgent();
        returningBack();

        if ("DRONE_FAULT".equalsIgnoreCase(event.getEventType())) {
            logInfo("DRONE FAULT detected in event, initiating fault procedure");
            droneFaulted();
        }

        taskCompleted();

        logInfo("=============================================");
        logInfo("EVENT PROCESSING COMPLETE: " + event);
        logInfo("=============================================");

        // Acknowledge completion
        send("Processed fire event: " + event.toString(), 6001);
    }

    /**
     * Run the drone state machine continuously
     */
    public void run() {
        logInfo("=====================================================================");
        logInfo("STARTING CONTINUOUS OPERATION - WAITING FOR FIRE EVENTS");
        logInfo("=====================================================================");

        while (running) {
            try {
                logInfo("Waiting for incoming fire events...");
                FireEvent event = receive();

                if (event != null) {
                    logInfo("Valid fire event received, beginning processing");
                    processEvent(event);

                    // Send acknowledgment to scheduler
                    send("Received fire event from scheduler", 6001);
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
            DroneSubsystem drone = new DroneSubsystem(InetAddress.getLocalHost());

            // Run continuously
            drone.run();

            // The following code will only execute if run() method completes or throws an exception
            drone.logInfo("Shutting down");
            if (drone.sendSocket != null) drone.sendSocket.close();
            if (drone.receieveSocket != null) drone.receieveSocket.close();

        } catch (UnknownHostException e) {
            System.err.println("[DRONE][ERROR] Unknown host error: " + e.getMessage());
        }
    }
}