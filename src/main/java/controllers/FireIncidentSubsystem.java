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
    // Colors
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String PURPLE = "\u001B[35m";
    static final String CYAN = "\u001B[36m";
}

/**
 * The FireIncidentSubsystem reads fire event data from a file and sends it to the Scheduler.
 * It also listens for responses from the Scheduler regarding dispatched fire events.
 */
public class FireIncidentSubsystem {
    //private final Scheduler scheduler;
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

    public void receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receieveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("receieve error: " + e);
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        System.out.println(FireSystemColors.GREEN + "[FIRE SYSTEM] Received response: " + FireSystemColors.YELLOW + r + FireSystemColors.RESET);
    }

    public void send(FireEvent fire) {
        String message = fire.toString();
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), 6001);
        } catch (UnknownHostException e) {
            System.out.println("Error: cannot find host: " + e);
        }
        System.out.println(FireSystemColors.GREEN + "[FIRE SYSTEM] Sending fire: " + FireSystemColors.YELLOW + message + FireSystemColors.RESET);
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
    public FireEvent readFile() {
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

                send(event);
                receive();
                
                // Add 2-second delay between each fire event
                try {
                    System.out.println(FireSystemColors.CYAN + "[FIRE SYSTEM] Waiting 2 seconds before sending next event..." + FireSystemColors.RESET);
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args){
        try{
            InetAddress ip = InetAddress.getLocalHost();
            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/main/resources/fire_events.txt", ip);
            
            // Read all fire events from the file
            FireEvent fire = fireSystem.readFile();
            
            // After sending all events, keep listening for responses
            System.out.println(FireSystemColors.CYAN + "\n[FIRE SYSTEM] All fire events sent. Waiting for responses..." + FireSystemColors.RESET);
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
