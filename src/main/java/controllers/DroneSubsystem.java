package controllers;

import models.FireEvent;

/**
 * The DroneSubsystem is responsible for handling fire event tasks assigned by the Scheduler.
 * It continuously retrieves tasks from the Scheduler, processes them, and sends responses back
 */
public class DroneSubsystem implements Runnable {
    private final Scheduler scheduler;
    private FireEvent lastTask;

    /**
     * Constructs a DroneSubsystem with a reference to the Scheduler.
     * @param scheduler The Scheduler instance managing task assignments.
     */
    public DroneSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.lastTask = null; // Initialize as null
    }

    /**
     * Continuously retrieves fire event tasks from the Scheduler, simulates processing,
     * and sends responses back to the Scheduler.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                FireEvent task = scheduler.getDroneTask();
                if (task == null) {
                    continue;
                }

                lastTask = task; // Store the last task received

                synchronized (System.out) {
                    System.out.println("[DroneSubsystem] Received task: " + task);
                }

                Thread.sleep(2000);

                synchronized (System.out) {
                    System.out.println("[DroneSubsystem] Completed task: " + task);
                }

                scheduler.receiveDroneResponse(task);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[DroneSubsystem] Interrupted!");
        }
    }

    public FireEvent getLastTask() {
        return lastTask;
    }
}