package controllers;

import models.FireEvent;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The FireIncidentSubsystem reads fire event data from a file and sends it to the Scheduler.
 * It also listens for responses from the Scheduler regarding dispatched fire events.
 */
public class FireIncidentSubsystem {
    private final String inputFile;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receieveSocket;

    private final InetAddress serverIP;

    private final int sendPort = 5000;
    private final int receivePort = 5001;

    /**
     * Constructs a FireIncidentSubsystem with a reference to the Scheduler and an input file path
     * @param inputFile The file containing fire event data.
     * @param serverIP The IP address of the server/scheduler
     */
    public FireIncidentSubsystem(String inputFile, InetAddress serverIP) {
        this.inputFile = inputFile;
        this.serverIP = serverIP;
        try {
            sendSocket = new DatagramSocket(sendPort);
            receieveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            logError("Socket initialization error", e);
        }
    }

    /**
     * Logs information messages with timestamp
     * @param message The message to log
     */
    private void logInfo(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println("[" + timestamp + "][FIRE INCIDENT] " + message);
    }

    /**
     * Logs error messages with timestamp
     * @param message The error message
     * @param e The exception that occurred
     */
    private void logError(String message, Exception e) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.err.println("[" + timestamp + "][FIRE INCIDENT][ERROR] " + message + ": " + e.getMessage());
    }

    /**
     * Receives response from the scheduler
     */
    public void receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receieveSocket.receive(receivePacket);
            int len = receivePacket.getLength();
            String response = new String(data, 0, len);
            logInfo("RECEIVED RESPONSE: " + response);
        } catch (IOException e) {
            logError("Receive error", e);
        }
    }

    /**
     * Sends a fire event to the scheduler
     * @param fire The fire event to send
     */
    public void send(FireEvent fire) {
        String message = fire.toString();
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), 6001);
            logInfo("SENDING EVENT: " + message);
            sendSocket.send(sendPacket);
        } catch (UnknownHostException e) {
            logError("Cannot find host", e);
        } catch (IOException e) {
            logError("Send error", e);
        }
    }

    /**
     * Reads all fire events from the input file and returns them as a list.
     * @return List of FireEvent objects read from the file
     */
    public List<FireEvent> readAllEvents() {
        List<FireEvent> events = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            int lineNumber = 0;

            // read line by line
            while ((line = br.readLine()) != null) {
                lineNumber++;
                // split line by space
                String[] parts = line.split(" ");

                if (parts.length < 4) {
                    logInfo("Skipping invalid line " + lineNumber + ": " + line);
                    continue;
                }

                try {
                    String time = parts[0];
                    int zoneID = Integer.parseInt(parts[1]);
                    String eventType = parts[2];
                    String severity = parts[3];

                    FireEvent event = new FireEvent(time, zoneID, eventType, severity);
                    events.add(event);
                } catch (NumberFormatException e) {
                    logError("Invalid zoneID at line " + lineNumber, e);
                }
            }
        } catch (IOException e) {
            logError("File reading error", e);
        }

        return events;
    }

    /**
     * Processes all fire events in the input file, sending each to the scheduler
     * and waiting for a response before sending the next one.
     */
    public void processAllEvents() {
        List<FireEvent> events = readAllEvents();

        logInfo("=====================================================================");
        logInfo("STARTING PROCESSING OF " + events.size() + " FIRE EVENTS");
        logInfo("=====================================================================");

        // Process each event
        int count = 0;
        for (FireEvent event : events) {
            count++;
            logInfo("---------------------------------------------");
            logInfo("PROCESSING EVENT " + count + " OF " + events.size());
            logInfo("---------------------------------------------");

            // Send the event to the scheduler
            send(event);

            // Wait for response
            receive();

            // Add delay between events
            try {
                logInfo("Waiting 2 seconds before next event...");
                Thread.sleep(2000); // 2 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logError("Interrupted while processing events", e);
                break;
            }
        }

        logInfo("=====================================================================");
        logInfo("ALL " + events.size() + " FIRE EVENTS HAVE BEEN PROCESSED");
        logInfo("=====================================================================");
    }

    public static void main(String[] args) {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/main/resources/fire_events.txt", ip);

            // Process all events at once
            fireSystem.processAllEvents();

        } catch (UnknownHostException e) {
            System.err.println("[FireIncidentSubsystem] Error: " + e.getMessage());
        }
    }
}