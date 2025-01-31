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
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            br.readLine(); // skipping the first line since it's just the header

            while ((line = br.readLine()) != null) {
                // splitting the csv line based on commas
                String[] parts = line.split(",");
                if (parts.length < 4) continue; // ignoring invalid lines

                String time = parts[0].trim(); // extracting the time when the event happened
                int zoneID = Integer.parseInt(parts[1].trim()); // getting the zone where the event occurred
                String eventType = parts[2].trim(); // checking if it's a fire detected or a drone request
                String severity = parts[3].trim(); // getting the severity level

                // creating a new fire event object with extracted data
                FireEvent event = new FireEvent(time, zoneID, eventType, severity);

                synchronized (System.out) {
                    System.out.println("[FireIncidentSubsystem] sending event: " + event);
                }

                // passing the fire event to the scheduler
                scheduler.receiveFireEvent(event);

                // waiting for the scheduler to send a response before sending another event
                FireEvent response;
                while ((response = scheduler.getResponseForFireIncidentSubsystem()) == null) {
                    Thread.sleep(500); // waiting in small intervals to avoid overloading the system
                }

                synchronized (System.out) {
                    System.out.println("[FireIncidentSubsystem] received response: " + response);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[FireIncidentSubsystem] error: " + e.getMessage());
        }
    }
}