package controllers;

import models.FireEvent;

public class DroneSubsystem implements Runnable {
    private final Scheduler scheduler;

    public DroneSubsystem(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

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
