package controllers;

import models.FireEvent;

public class DroneSubsystem implements Runnable {
    private final Scheduler scheduler;

    private FireEvent lastTask;

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

                //just for testing purposes
                synchronized (this) {
                    lastTask = task;
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

    //for testing, get the task that was recieved by the drone
    public synchronized FireEvent getLastTask(){
        return lastTask;
    }
}
