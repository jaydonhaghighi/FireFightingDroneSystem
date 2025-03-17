package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;

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
    
    // Drone management
    private DroneManager droneManager;
    private Map<String, Integer> dronePorts = new HashMap<>(); // Maps drone IDs to their receive ports

    /**
     * Constructs a new Scheduler with drone management capability
     * 
     * @param ip The IP address of the fire incident system
     */
    public Scheduler(InetAddress ip) {
        this.fireIncidentIP = ip;
        Location baseLocation = new Location(0, 0);
        this.droneManager = new DroneManager(baseLocation);
        
        // Register drone ports using the same calculation as in DroneSubsystem
        registerDronePort("drone1", 7001 + (1 * 100)); // 7101
        registerDronePort("drone2", 7001 + (2 * 100)); // 7201
        registerDronePort("drone3", 7001 + (3 * 100)); // 7301
        
        try {
            sendSocket = new DatagramSocket(sendPort);
            receiveSocket = new DatagramSocket(receivePort);
            System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Initialized at " + baseLocation + SchedulerColors.RESET);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Registers a drone port for communication
     * 
     * @param droneId The drone identifier
     * @param port The port number for communication
     */
    private void registerDronePort(String droneId, int port) {
        dronePorts.put(droneId, port);
        System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Registered drone " + droneId + " on port " + port + SchedulerColors.RESET);
    }

    /**
     * Receives a message and processes it based on content
     * @return FireEvent if it's a fire event, null otherwise
     */
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
        
        // Check if this is a drone status update
        if (isDroneStatusUpdate(r)) {
            processDroneStatusUpdate(r);
            return null;
        }
        
        // Try to parse as a FireEvent
        try {
            return createFireEventFromString(r);
        } catch (Exception e) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Warning: Received a message that could not be parsed as a FireEvent: " + r + SchedulerColors.RESET);
            return null;
        }
    }
    
    /**
     * Checks if a message is a drone status update
     * @param message The message to check
     * @return true if it's a status update, false otherwise
     */
    private boolean isDroneStatusUpdate(String message) {
        // Expect format: droneId state x y
        try {
            String[] parts = message.split(" ");
            if (parts.length >= 4) {
                String droneId = parts[0];
                // Check if it starts with "drone" and if the next part is not a time format (to differentiate from fire events)
                if (droneId.startsWith("drone")) {
                    // Try to parse the 3rd and 4th parts as integers (x and y coordinates)
                    Integer.parseInt(parts[2]);
                    Integer.parseInt(parts[3]);
                    System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Identified drone status update from: " + droneId + SchedulerColors.RESET);
                    return true;
                }
            }
        } catch (Exception e) {
            // If we can't parse the message as a drone status, it's not a drone status
            System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] Message not recognized as drone status: " + message + SchedulerColors.RESET);
        }
        return false;
    }
    
    /**
     * Processes a drone status update
     * @param message The status update message
     */
    private void processDroneStatusUpdate(String message) {
        try {
            String[] parts = message.split(" ");
            String droneId = parts[0];
            String state = parts[1];
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            Location location = new Location(x, y);
            
            // Register drone if not already registered
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                status = droneManager.registerDrone(droneId);
                System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Registered new drone: " + droneId + SchedulerColors.RESET);
            }
            
            // Update drone status
            droneManager.updateDroneStatus(droneId, state, location, null);
            System.out.println(SchedulerColors.CYAN + "[SCHEDULER] Updated drone status: " + droneId + 
                              " at " + location + " in state " + state + SchedulerColors.RESET);
        } catch (Exception e) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Error processing drone status: " + e + SchedulerColors.RESET);
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
    /**
     * Sends a fire event to the specified port
     * 
     * @param fire The fire event to send
     * @param port The port to send to
     * @param what A description of what is being sent
     * @param location A description of where it's being sent
     */
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
     * Sends a fire event to a specific drone
     * 
     * @param fire The fire event to send
     * @param droneId The ID of the drone to send to
     */
    public void sendToDrone(FireEvent fire, String droneId) {
        // Assign this drone to the fire event
        fire.assignDrone(droneId);
        
        // Get the port for this drone
        Integer port = dronePorts.get(droneId);
        if (port == null) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Error: No port registered for drone " + droneId + SchedulerColors.RESET);
            return;
        }
        
        send(fire, port, "fire assignment", "Drone " + droneId);
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

    /**
     * Retrieves a task for the DroneSubsystem from the droneTaskQueue.
     *
     * @return The next fire event to be handled by a drone.
     */
    /**
     * Processes the next fire event in the queue and assigns it to the most appropriate drone
     */
    public void getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.peek(); // Don't remove yet until we find a drone
            
            // Select best drone for this event
            DroneStatus bestDrone = droneManager.selectBestDroneForEvent(event);
            
            if (bestDrone != null) {
                // Remove event from queue since we found a drone
                events.poll();
                
                // Get the zone location
                int zoneId = event.getZoneID();
                Location zoneLocation = droneManager.getLocationForZone(zoneId);
                
                // Update fire status in zone
                droneManager.updateZoneFireStatus(zoneId, true, event.getSeverity());
                
                // Log the assignment
                String droneId = bestDrone.getDroneId();
                System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Assigning fire in zone " + zoneId + 
                                 " (" + zoneLocation + ") to " + droneId + 
                                 " currently at " + bestDrone.getCurrentLocation() + 
                                 " (distance: " + bestDrone.distanceTo(zoneLocation) + ")" + 
                                 SchedulerColors.RESET);
                
                // Send to the selected drone
                sendToDrone(event, droneId);
                
                // Update drone status with the new task
                droneManager.updateDroneStatus(droneId, bestDrone.getState(), 
                                             bestDrone.getCurrentLocation(), event);
            } else {
                System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] No available drones for fire in zone " + 
                                 event.getZoneID() + ". Keeping in queue." + SchedulerColors.RESET);
                
                // Add a small wait to prevent tight loop
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            System.out.println(SchedulerColors.CYAN + "[SCHEDULER] No fire events in queue to send to drone" + SchedulerColors.RESET);
            // Add a small wait here to prevent tight loop when no events
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    /**
     * Prints the current status of all drones
     */
    public void printDroneStatus() {
        Collection<DroneStatus> allDrones = droneManager.getAllDrones();
        System.out.println(SchedulerColors.CYAN + "\n[SCHEDULER] Current Drone Status:" + SchedulerColors.RESET);
        for (DroneStatus drone : allDrones) {
            System.out.println(SchedulerColors.CYAN + "  " + drone + SchedulerColors.RESET);
        }
        System.out.println();
    }
    
    /**
     * Main entry point for the scheduler
     */
    public static void main(String[] args) {
        try{
            InetAddress ip = InetAddress.getLocalHost();
            Scheduler scheduler = new Scheduler(ip);
            
            // Create separate threads for receiving messages and processing events
            Thread receiveThread = new Thread(() -> scheduler.receiveMessages());
            Thread processThread = new Thread(() -> scheduler.processEvents());
            
            receiveThread.start();
            processThread.start();
            
            // Wait for both threads to complete (they won't normally)
            receiveThread.join();
            processThread.join();
            
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error in Scheduler main: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Thread function for receiving messages
     */
    private void receiveMessages() {
        try {
            // Initial delay to allow drones to register
            System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Waiting for drones to register..." + SchedulerColors.RESET);
            Thread.sleep(5000); // 5 second delay to wait for drones
            
            System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Starting to process messages" + SchedulerColors.RESET);
            printDroneStatus();
            
            while (true) {
                // Check for both fire events and drone status updates
                FireEvent event = receive();
                
                // If it's a fire event, add it to queue
                if (event != null) {
                    // Add to queue and send acknowledgement
                    events.add(event);
                    send(event, 5001, "response", "Fire Incident system");
                    
                    // Print current drone status whenever a new fire event arrives
                    printDroneStatus();
                }
                
                // Brief pause to prevent tight loop
                Thread.sleep(100);
            }
        } catch (Exception e) {
            System.out.println("Error in receive thread: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Thread function for processing events and assigning to drones
     */
    private void processEvents() {
        try {
            while (true) {
                // Process fire events and assign to drones
                getDroneTask();
                
                // Brief pause between processing cycles
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("Error in process thread: " + e);
            e.printStackTrace();
        }
    }
}
