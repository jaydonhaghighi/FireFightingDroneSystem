package controllers;

import models.FireEvent;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;

/**
 * ANSI colors for console output
 */
class FireSystemColors {
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String PURPLE = "\u001B[35m";
}

/**
 * The FireIncidentSubsystem reads fire event data from a file and sends it to the Scheduler.
 * It also listens for responses from the Scheduler regarding dispatched fire events.
 */
public class FireIncidentSubsystem {
    private final String inputFile;

    DatagramPacket sendPacket, receivePacket;
    DatagramSocket sendSocket, receieveSocket;

    private final InetAddress serverIP;

    private final int sendPort = 5000;
    private final int receivePort = 5001;

    /**
     * Constructs a FireIncidentSubsystem with a reference to the Scheduler and an input file path
     * @param inputFile The file containing fire event data.
     * @param serverIP The IP address of the server/scheduler *NOT CURRENTLY IN USE*
     */
    public FireIncidentSubsystem(String inputFile, InetAddress serverIP) {
        this.inputFile = inputFile;
        this.serverIP = serverIP;
        try {
            sendSocket = new DatagramSocket(sendPort);
            receieveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }

    /**
     * Receives a fire event from the scheduler
     *
     * @return The received fire event
     */
    public void receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);

        try {
            receieveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("receive error: " + e);
        }
    }

    /**
     * Sends a message to the specified port
     *
     * @param fire The fire event to send
     */
    public void send(FireEvent fire) {
        String message = fire.toString();
        byte[] msg = message.getBytes();

        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), 6001);
        } catch (UnknownHostException e) {
            System.out.println("Error: cannot find host: " + e);
        }
        System.out.println(FireSystemColors.RED + "FIRE ALERT: " + message + FireSystemColors.RESET);
        try {
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads fire events from the input file and sends them to the Scheduler for processing.
     * It continuously waits for and handles responses from the Scheduler.
     */
    public void readFile() {
        // reads file (Final_event_file.csv)
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            boolean isFirstLine = true;

            // read line by line
            while ((line = br.readLine()) != null) {
                // Skip the header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // split line by comma
                String[] parts = line.split(",");

                String time = parts[0];
                int zoneID = Integer.parseInt(parts[1]);
                String eventType = parts[2];
                String severity = parts[3];
                
                // Default error type to NONE if not present
                String errorType = (parts.length > 4 && !parts[4].isEmpty()) ? parts[4] : "NONE";
                
                FireEvent event = new FireEvent(time, zoneID, eventType, severity, errorType);

                send(event);
                receive();

                try {
                    int delaySeconds = 3;
                    System.out.println(FireSystemColors.GREEN + "Next fire in " + delaySeconds +
                            "s" + FireSystemColors.RESET);
                    Thread.sleep(delaySeconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error: " + e.getMessage());
        }
    }

    public static void main(String[] args){
        try{
            InetAddress ip = InetAddress.getLocalHost();

            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/main/resources/Final_event_file.csv", ip);
            System.out.println(FireSystemColors.PURPLE + "[SYSTEM] Ready to send fire alerts" + FireSystemColors.RESET);
            
            fireSystem.readFile();

            System.out.println(FireSystemColors.PURPLE + "\n[SYSTEM] All fire events processed. Monitoring..." + FireSystemColors.RESET);
            while (true) {
                fireSystem.receive();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error in FireIncidentSubsystem main: " + e);
            e.printStackTrace();
        }
    }
}
