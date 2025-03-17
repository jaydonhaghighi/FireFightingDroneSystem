package controllers;

import models.FireEvent;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static models.FireEvent.createFireEventFromString;

/**
 * The Scheduler class manages the flow of fire incident events and drone responses.
 * It processes incoming fire events, assigns them to drones, and forwards drone responses
 * to the FireIncidentSubsystem
 */
public class Scheduler {
    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receiveSocket;

    Queue<FireEvent> events = new LinkedBlockingQueue<FireEvent>();
    private final int sendPort = 6000;
    private final int receivePort = 6001;
    private final InetAddress fireIncidentIP;
    private boolean running = true;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructs a new Scheduler with separate queues for fire incidents,
     * drone tasks, drone responses, and fire incident responses.
     */
    public Scheduler(InetAddress ip) {
        this.fireIncidentIP = ip;
        try {
            sendSocket = new DatagramSocket(sendPort);
            receiveSocket = new DatagramSocket(receivePort);
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
        System.out.println("[" + timestamp + "][SCHEDULER] " + message);
    }

    /**
     * Logs error messages with timestamp
     * @param message The error message
     * @param e The exception that occurred
     */
    private void logError(String message, Exception e) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.err.println("[" + timestamp + "][SCHEDULER][ERROR] " + message + ": " + e.getMessage());
    }

    /**
     * Receives a packet from the socket and attempts to parse it as a FireEvent
     * @return FireEvent if successfully parsed, null otherwise
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            logError("Receive error", e);
            return null;
        }

        int len = receivePacket.getLength();
        String message = new String(data, 0, len);
        logInfo("RECEIVED: " + message);

        // Check if this is an acknowledgment message rather than a fire event
        if (message.startsWith("Processed") || message.startsWith("Received")) {
            logInfo("↳ Acknowledgment message - no action needed");
            return null;
        }

        try {
            FireEvent event = createFireEventFromString(message);
            logInfo("↳ Valid fire event parsed");
            return event;
        } catch (Exception e) {
            logError("Error parsing message as FireEvent", e);
            return null;
        }
    }

    /**
     * Sends a UDP packet to the designated ip and port
     * @param fire the fire event that is being sent
     * @param port which port the data should be sent to
     * @param what a description of what is being sent
     * @param location where the data is being sent
     */
    public void send(FireEvent fire, int port, String what, String location) {
        String message = fire.toString();
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), port);
            logInfo("SENDING to " + location + " (" + what + "): " + message);
            sendSocket.send(sendPacket);
        } catch (UnknownHostException e) {
            logError("Cannot find host", e);
        } catch (IOException e) {
            logError("Send error", e);
        }
    }

    /**
     * Receives a fire event from the FireIncidentSubsystem and adds it to the queue.
     */
    public void receiveFireEvent() {
        FireEvent fire = receive();
        if (fire != null) {
            logInfo("EVENT QUEUED: " + fire.toString());
            events.add(fire);
            send(fire, 5001, "response", "Fire Incident System");
        }
    }

    /**
     * Retrieves a task for the DroneSubsystem from the events queue.
     */
    public void getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.poll();
            logInfo("DISPATCHING EVENT TO DRONE: " + event.toString());
            send(event, 7001, "fire event", "Drone");
        }
    }

    /**
     * Continuously runs the scheduler to process events
     */
    public void run() {
        logInfo("=====================================================================");
        logInfo("STARTING CONTINUOUS EVENT PROCESSING");
        logInfo("=====================================================================");

        while (running) {
            try {
                logInfo("Waiting for incoming messages...");
                receiveFireEvent();

                // Process any received events
                if (!events.isEmpty()) {
                    logInfo("Events in queue: " + events.size() + " - dispatching to drone");
                    getDroneTask();
                }

                // Short pause to prevent high CPU usage
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logError("Interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logError("Error processing events", e);
                // Continue running even if there's an error
            }
        }
    }

    /**
     * Stops the scheduler's continuous processing
     */
    public void stop() {
        running = false;
        logInfo("Shutdown requested");
    }

    public static void main(String[] args) {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            Scheduler scheduler = new Scheduler(ip);

            // Run the scheduler continuously
            scheduler.run();

            // The following code will only execute if run() method completes or throws an exception
            scheduler.logInfo("Shutting down");
            if (scheduler.sendSocket != null) scheduler.sendSocket.close();
            if (scheduler.receiveSocket != null) scheduler.receiveSocket.close();

        } catch (UnknownHostException e) {
            System.err.println("[SCHEDULER][ERROR] Unknown host error: " + e.getMessage());
        }
    }
}