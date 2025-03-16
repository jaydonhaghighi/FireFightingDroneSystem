package controllers;

import models.FireEvent;

import javax.xml.crypto.Data;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;

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

        System.out.print("Received response: ");
        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        System.out.print(r);
    }

    public void send(FireEvent fire) {
        String message = fire.toString();
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), 6001);
        } catch (UnknownHostException e) {
            System.out.println("Error: cannot find host: " + e);
        }
        System.out.println("Sending fire: " + message);
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
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args){
        try{
            InetAddress ip = InetAddress.getLocalHost();
            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("C:\\Users\\brabo\\Desktop\\FireFightingDroneSystem-main\\src\\main\\resources\\fire_events.txt", ip);
            FireEvent fire = fireSystem.readFile();
        } catch (UnknownHostException e) {}

    }
}
