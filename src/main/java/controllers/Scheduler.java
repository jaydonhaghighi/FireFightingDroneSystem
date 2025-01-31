package controllers;

import models.FireEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Scheduler class manages the flow of fire incident events and drone responses.
 * It processes incoming fire events, assigns them to drones, and forwards drone responses
 * to the FireIncidentSubsystem
 */
public class Scheduler implements Runnable {
    private final BlockingQueue<FireEvent> fireIncidentQueue;
    private final BlockingQueue<FireEvent> droneTaskQueue;
    private final BlockingQueue<FireEvent> droneResponseQueue;
    private final BlockingQueue<FireEvent> fireIncidentResponseQueue;

    /**
     *Constructs a new Scheduler with separate queues for fire incidents,
     * drone tasks, drone responses, and fire incident responses.
     */
    public Scheduler() {
        this.fireIncidentQueue = new LinkedBlockingQueue<>();
        this.droneTaskQueue = new LinkedBlockingQueue<>();
        this.droneResponseQueue = new LinkedBlockingQueue<>();
        this.fireIncidentResponseQueue = new LinkedBlockingQueue<>();
    }

    // FireIncidentSubsystem -> Scheduler

    /**
     * Recieves a fire event from the FireIncidentSubsystem and adds it to the queue.
     *
     * @param event The fire event to be added to the fireIncidentQueue.
     */
    public void receiveFireEvent(FireEvent event) {
        try {
            synchronized (System.out) {
                System.out.println("[Scheduler] Received fire event: " + event);
            }
            fireIncidentQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while receiving fire event.");
        }
    }

    // Scheduler -> DroneSubsystem

    /**
     * Processes fire events from the fireIncidentQueue and dispatches them to the droneTaskQueue.
     */
    private void processFireEvents() {
        try {
            while (true) {
                FireEvent event = fireIncidentQueue.take(); // wait for a fire event

                synchronized (System.out) {
                    System.out.println("[Scheduler] Dispatching task to DroneSubsystem: " + event);
                }
                droneTaskQueue.put(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while processing fire events.");
        }
    }

    // DroneSubsystem -> Scheduler

    /**
     * Receives a response from the DroneSubsystem and adds it to the droneResponseQueue.
     *
     * @param response The drone response to be added to the queue.
     */
    public void receiveDroneResponse(FireEvent response) {
        try {
            droneResponseQueue.put(response);
            synchronized (System.out) {
                System.out.println("[Scheduler] Received response from DroneSubsystem: " + response);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while receiving drone response.");
        }
    }

    // Scheduler -> FireIncidentSubsystem

    /**
     * Processes drone responses from the droneResponseQueue and forwards them to the fireIncidentResponseQueue.
     */
    private void processDroneResponses() {
        try {
            while (true) {
                FireEvent response = droneResponseQueue.take(); // wait for a response

                synchronized (System.out) {
                    System.out.println("[Scheduler] Sending response to FireIncidentSubsystem: " + response);
                }
                fireIncidentResponseQueue.put(response);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while processing drone responses.");
        }
    }

    // drone fetches task from scheduler

    /**
     * Retrieves a task for the DroneSubsystem from the droneTaskQueue.
     *
     * @return The next fire event to be handled by a drone.
     */
    public FireEvent getDroneTask() {
        try {
            return droneTaskQueue.take(); // block until a task is available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while getting drone task.");
            return null;
        }
    }

    // fireIncidentSubsystem fetches response from scheduler

    /**
     * Retrieves a processed response for the FireIncidentSubsystem from the fireIncidentResponseQueue.
     *
     * @return The next processed response for the FireIncidentSubsystem.
     */
    public FireEvent getResponseForFireIncidentSubsystem() {
        try {
            return fireIncidentResponseQueue.take(); // block until a response is available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while getting response for FireIncidentSubsystem.");
            return null;
        }
    }

    /**
     * Runs the Scheduler, starting threads for processing fire events and drone responses.
     */
    @Override
    public void run() {
        Thread fireEventProcessor = new Thread(this::processFireEvents);
        Thread droneResponseProcessor = new Thread(this::processDroneResponses);
        fireEventProcessor.start();
        droneResponseProcessor.start();

        try {
            fireEventProcessor.join();
            droneResponseProcessor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while running.");
        }
    }
}