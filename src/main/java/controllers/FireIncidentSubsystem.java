package controllers;

import models.FireEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * The FireIncidentSubsystem reads fire event data from a file and sends it to the Scheduler.
 * It also listens for responses from the Scheduler regarding dispatched fire events.
 */
public class FireIncidentSubsystem implements Runnable {
    private final Scheduler scheduler;
    private final String inputFile;

    /**
     * Constructs a FireIncidentSubsystem with a reference to the Scheduler and an input file path
     * @param scheduler The scheduler that processes fire incidents.
     * @param inputFile The file containing fire event data.
     */
    public FireIncidentSubsystem(Scheduler scheduler, String inputFile) {
        this.scheduler = scheduler;
        this.inputFile = inputFile;
    }

    /**
     * Reads fire events from the input file and sends them to the Scheduler for processing.
     * It continuously waits for and handles responses from the Scheduler.
     */
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