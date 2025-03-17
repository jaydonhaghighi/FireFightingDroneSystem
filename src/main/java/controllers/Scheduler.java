package controllers;

import models.FireEvent;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.*;
import java.net.*;
import java.util.*;

import static models.FireEvent.createFireEventFromString;

/**
 * ANSI colors for console output
 */
class SchedulerColors {
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

    /**
     *Constructs a new Scheduler with separate queues for fire incidents,
     * drone tasks, drone responses, and fire incident responses.
     */
    public Scheduler(InetAddress ip) {
        this.fireIncidentIP = ip;
        try {
            sendSocket = new DatagramSocket(sendPort);
            receiveSocket = new DatagramSocket(receivePort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);
        try {
            receiveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("receieve error: " + e);
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Received packet: " + SchedulerColors.YELLOW + r + SchedulerColors.RESET);
        
        try {
            return createFireEventFromString(r);
        } catch (Exception e) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Warning: Received a message that could not be parsed as a FireEvent: " + r + SchedulerColors.RESET);
            return null;
        }
    }

    /**
     * Sends a UDP packet to the designated ip and port
     * @param fire the fire event that is being sent
     * @param port which port the data should be sent to
     * @param what a description of what is being sent *ONLY USED FOR DATA LOGGING PURPOSES*
     * @param location where the data is being sent *ONLY USED FOR DATA LOGGING PURPOSES*
     */
    //TODO: will eventually have to add an IP address variable once multiple devices are being used
    public void send(FireEvent fire, int port, String what, String location) {
        String message = fire.toString();
        byte[] msg = message.getBytes();
        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), port);
        } catch (UnknownHostException e) {
            System.out.println("Error: cannot find host: " + e);
        }
        System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Sending " + what + " to " + location + ": " + SchedulerColors.YELLOW + message + SchedulerColors.RESET);
        try {
            sendSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Receives a fire event from the FireIncidentSubsystem and adds it to the queue.
     */
    public void receiveFireEvent() {
        FireEvent fire = receive();
        
        // Only process if we received a valid fire event
        if (fire != null) {
            events.add(fire);
            send(fire, 5001, "response", "Fire Incident system");
        }
    }

    // Scheduler -> DroneSubsystem

    /**
     * Processes fire events from the fireIncidentQueue and dispatches them to the droneTaskQueue.
     */
//    private void processFireEvents() {
//        while(!Thread.currentThread().isInterrupted()) {
//            try {
//                FireEvent event = fireIncidentQueue.take(); // wait for a fire event
//                synchronized (System.out) {
//                    System.out.println("[Scheduler] Dispatching task to DroneSubsystem: " + event);
//                }
//                droneTaskQueue.put(event);
//
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                break;//exit loop
//            }
//        }
//        System.err.println("[Scheduler] Interrupted while processing fire events.");
//    }

    // DroneSubsystem -> Scheduler

    /**
     * Receives a response from the DroneSubsystem and adds it to the droneResponseQueue.
     *
     * @param response The drone response to be added to the queue.
     */
//    public void receiveDroneResponse(FireEvent response) {
//        try {
//            droneResponseQueue.put(response);
//            synchronized (System.out) {
//                System.out.println("[Scheduler] Received response from DroneSubsystem: " + response);
//            }
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            System.err.println("[Scheduler] Interrupted while receiving drone response.");
//        }
//    }

    // Scheduler -> FireIncidentSubsystem

    /**
     * Processes drone responses from the droneResponseQueue and forwards them to the fireIncidentResponseQueue.
     */
//    private void processDroneResponses() {
//        while(!Thread.currentThread().isInterrupted()) {
//            try {
//                FireEvent response = droneResponseQueue.take(); // wait for a response
//                synchronized (System.out) {
//                    System.out.println("[Scheduler] Sending response to FireIncidentSubsystem: " + response);
//                }
//                fireIncidentResponseQueue.put(response);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                break;
//            }
//        }
//        System.err.println("[Scheduler] Interrupted while processing drone responses.");
//    }

    // drone fetches task from scheduler

    /**
     * Retrieves a task for the DroneSubsystem from the droneTaskQueue.
     *
     * @return The next fire event to be handled by a drone.
     */
    public void getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.poll();
            send(event, 7001, "fire info", "Drone");
        } else {
            System.out.println(SchedulerColors.CYAN + "[SCHEDULER] No fire events in queue to send to drone" + SchedulerColors.RESET);
            // Could add a small wait here to prevent tight loop when no events
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    public static void main(String[] args) {
        try{
            InetAddress ip = InetAddress.getLocalHost();
            Scheduler scheduler = new Scheduler(ip);
            
            // Continuously process fire events
            while (true) {
                // Receive a fire event from FireIncidentSubsystem
                scheduler.receiveFireEvent();
                
                // Send to drone for processing
                scheduler.getDroneTask();
                
                // Can add a small delay here if needed
                // Thread.sleep(100);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error in Scheduler main: " + e);
            e.printStackTrace();
        }
    }
}
