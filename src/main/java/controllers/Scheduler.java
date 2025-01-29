package controllers;

import models.FireEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Scheduler implements Runnable {
    private final BlockingQueue<FireEvent> fireIncidentQueue;
    private final BlockingQueue<FireEvent> droneTaskQueue;
    private final BlockingQueue<FireEvent> droneResponseQueue;
    private final BlockingQueue<FireEvent> fireIncidentResponseQueue;

    public Scheduler() {
        this.fireIncidentQueue = new LinkedBlockingQueue<>();
        this.droneTaskQueue = new LinkedBlockingQueue<>();
        this.droneResponseQueue = new LinkedBlockingQueue<>();
        this.fireIncidentResponseQueue = new LinkedBlockingQueue<>();
    }

    // FireIncidentSubsystem -> Scheduler
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
    public FireEvent getResponseForFireIncidentSubsystem() {
        try {
            return fireIncidentResponseQueue.take(); // block until a response is available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scheduler] Interrupted while getting response for FireIncidentSubsystem.");
            return null;
        }
    }

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