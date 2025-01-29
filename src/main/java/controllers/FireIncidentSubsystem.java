package controllers;

import models.FireEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class FireIncidentSubsystem implements Runnable {
    private final Scheduler scheduler;
    private final String inputFile;

    public FireIncidentSubsystem(Scheduler scheduler, String inputFile) {
        this.scheduler = scheduler;
        this.inputFile = inputFile;
    }

    @Override
    public void run() {
        // reads file (fire_events.txt)
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;

            // read line by line
            while ((line = br.readLine()) != null) {
                // split line by space
                String[] parts = line.split(" ");
                String time = parts[0];
                int zoneID = Integer.parseInt(parts[1]);
                String eventType = parts[2];
                String severity = parts[3];

                FireEvent event = new FireEvent(time, zoneID, eventType, severity);

                synchronized (System.out) {
                    System.out.println("[FireIncidentSubsystem] Sent event: " + event);
                }

                scheduler.receiveFireEvent(event); // send to scheduler
            }

            while (true) { // wait for responses
                FireEvent response = scheduler.getResponseForFireIncidentSubsystem();
                if (response != null) {
                    synchronized (System.out) {
                        System.out.println("[FireIncidentSubsystem] Received response: " + response);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error: " + e.getMessage());
        }
    }
}
