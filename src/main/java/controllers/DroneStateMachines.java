package controllers;


import models.FireEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;


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
    void handleFireEvent(DroneStateMachines context, FireEvent event);
    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    void dropAgent(DroneStateMachines context);
    /**
     * Drone return state
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    void returningBack(DroneStateMachines context);

    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    void droneFaulted(DroneStateMachines context);

    /**
     * Drone task completion state
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    void taskCompleted(DroneStateMachines context);

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
    public void handleFireEvent(DroneStateMachines context, FireEvent event) {
        System.out.println("preparing to handle new fire event: " + event);
        context.setState(new EnRoute());
    }
    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     * */

    @Override
    public void dropAgent(DroneStateMachines context) {
        System.out.println("drone is idle, nothing to drop");

    }


    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneStateMachines context) {
        System.out.println("drone is idle and stationed at base.");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneStateMachines context) {
        System.out.println("drone has not faulted");
    }

    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneStateMachines context) {
        System.out.println("drone is idle, no tasks have been completed");
    }

    /**
     * Display the state of drone
     *
     * */

    @Override
    public void displayState() {
        System.out.println("IDLE");
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
    public void handleFireEvent(DroneStateMachines context, FireEvent event) {
        System.out.println("Drone is en route, already in motion of handling an event");

    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneStateMachines context) {
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
    public void returningBack(DroneStateMachines context) {
        System.out.println("drone is en route, has not returned yet"); //
    }


    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneStateMachines context) {
        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneStateMachines context) {
        System.out.println("drone is en route, no tasks have been completed yet");
    }


    /**
     * Current state of drone
     *
     * */

    @Override
    public void displayState() {
        System.out.println("EN ROUTE");
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
    public void handleFireEvent(DroneStateMachines context, FireEvent event) {
        System.out.println("drone is dropping an agent and already handling task");
    }

    /**
     * Drone dropping agent status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void dropAgent(DroneStateMachines context) {
        System.out.println("drone is currently dropping an agent");
    }

    /**
     * Drone return status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneStateMachines context) {
        System.out.println("drone is dropping an agent and has not yet returned to its base");
        context.setState(new ArrivedToBase());
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/

    @Override
    public void droneFaulted(DroneStateMachines context) {
        System.out.println("drone has not faulted");
    }
    /**
     * Drone task completion status
     *
     * @param context DroneSateMachines setting drone state
     *
     * */

    @Override
    public void taskCompleted(DroneStateMachines context) {
        System.out.println("drone is dropping an agent and has not yet completed its task");

    }

    /**
     * Current drone state
     *
     * */

    @Override
    public void displayState() {
        System.out.println("DROPPING AGENT");
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
    public void handleFireEvent(DroneStateMachines context, FireEvent event) {
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
    public void dropAgent(DroneStateMachines context) {
        System.out.println("drone has arrived to base and has already dropped agent");
    }

    /**
     * Drone status returning to base
     *
     * @param context DroneSateMachines setting drone state
     *
     * */
    @Override
    public void returningBack(DroneStateMachines context) {
        System.out.println("drone has already arrived to base");
    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneStateMachines context) {
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
    public void taskCompleted(DroneStateMachines context) {
        System.out.println("drone has arrived to base and completed its task");
        context.setState(new Idle());
    }


    /**
     * Current status of drone
     *
     * */
    @Override
    public void displayState() {
        System.out.println("ARRIVED TO BASE");
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
    public void handleFireEvent(DroneStateMachines context, FireEvent event) {
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
    public void dropAgent(DroneStateMachines context) {
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
    public void returningBack(DroneStateMachines context) {
        System.out.println("drone has faulted and did not move");

    }
    /**
     * Drone fault status
     *
     * @param context DroneSateMachines setting drone state
     * **/
    @Override
    public void droneFaulted(DroneStateMachines context) {
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
    public void taskCompleted(DroneStateMachines context) {
        System.out.println("Drone has faulted and returned to base. Now setting to idle.");
        context.setState(new Idle());  // Once at base, transition to idle
    }

    /**
     * Current state of drone
     * **/

    @Override
    public void displayState() {
        System.out.println("FAULTED");

    }
}

/**
 * DroneStateMachines class
 * for switching drone states
 * */
public class DroneStateMachines {
    //current state of drone
    private DroneState currentState;
    private Queue<FireEvent> fireEventQueue; // Queue for fire events

    /**
     * Constructor
     * */
    public DroneStateMachines() {
        //set the initial state of drone
        currentState = new Idle();
        this.fireEventQueue = new LinkedList<>();

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
        System.out.println("\nState before: ");
        currentState.displayState();
        currentState.handleFireEvent(this, event);
        System.out.println("State after: ");
        currentState.displayState();
    }
    /**
     * Drone dropping agent
     * */

    public void dropAgent() {
        System.out.println("\nState before: ");
        currentState.displayState();
        currentState.dropAgent(this);
        System.out.println("State after: ");
        currentState.displayState();
    }

    /**
     * Drone returning to base
     * */
    public void returningBack() {
        System.out.println("\nState before: ");
        currentState.displayState();
        currentState.returningBack(this);
        System.out.println("State after: ");
        currentState.displayState();
    }

    /**
     * Drone having a fault
     * **/
    public void droneFaulted(){
        System.out.println("\nState before: ");
        currentState.displayState();
        currentState.droneFaulted(this);
        System.out.println("State after: ");
        currentState.displayState();
    }

    /**
     * Drone completing task
     * */
    public void taskCompleted() {
        System.out.println("\nState before: ");
        currentState.displayState();
        currentState.taskCompleted(this);
        System.out.println("State after: ");
        currentState.displayState();

        if(!fireEventQueue.isEmpty()){
            System.out.println("\nProcessing next fire event in queue ");
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
     * Main program for droneStateMachines
     * */
    public static void main(String[] args) {
        DroneStateMachines drone = new DroneStateMachines();

        try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/fire_events.txt"))) {
            String line;

            // Formatting
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\\s+");
                if (tokens.length < 4) {
                    System.out.println("Invalid event line: " + line);
                    continue;
                }

                // Parse the event details
                String time = tokens[0];
                int id = Integer.parseInt(tokens[1]);
                String eventType = tokens[2];
                String severity = tokens[3];

                // Create a new FireEvent
                FireEvent event = new FireEvent(time, id, eventType, severity);

                processEvent(drone, event);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes an event by scheduling it and executing the standard sequence of drone actions.
     */
    private static void processEvent(DroneStateMachines drone, FireEvent event) {
        drone.scheduleFireEvent(event);
        drone.dropAgent();
        drone.returningBack();

        if ("DRONE_FAULT".equalsIgnoreCase(event.getEventType())) {
            drone.droneFaulted();
        }

        drone.taskCompleted();
    }
}
