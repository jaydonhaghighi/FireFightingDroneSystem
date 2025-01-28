package controllers;
import java.util.LinkedList;
import java.util.Queue;
import models.*;

public class Scheduler implements Runnable {
    private Queue<FireEvent> events;

    public Scheduler() {
        events = new LinkedList<FireEvent>();
    }

    public synchronized void receiveFireEvent(FireEvent event) {
        events.add(event);
        System.out.println("[Scheduler] Event recieved: " + event);
        notifyAll();
    }

    public FireEvent getResponseForFireIncidentSubsystem() {
        FireEvent event = events.peek();
        return event;
    }

    public FireEvent getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.poll();
            return event;
        } else {
            return null;
        }
    }

    public void run() {
        try {
            while (events.size() < 0) {
                wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Scheduler] Thread interrupted");
        }
    }
}
