package controllers;

import models.FireEvent;

/**
 * The DroneSubsystem is responsible for handling fire event tasks assigned by the Scheduler.
 * It continuously retrieves tasks from the Scheduler, processes them, and sends responses back
 */
public class DroneSubsystem implements Runnable {
    private final Scheduler scheduler;

    /**
     * Constructs a DroneSubsystem with a reference to the Scheduler.
     * @param scheduler The Scheduler instance managing task assignments.
     */
    public DroneSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Continuously retrieves fire event tasks from the Scheduler, simulates processing,
     * and sends responses back to the Scheduler.
     */
    @Override
    public void run() {
        try {
            while (true) {
                FireEvent task = scheduler.getDroneTask();
                if (task == null) {
                    continue;
                }

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
}