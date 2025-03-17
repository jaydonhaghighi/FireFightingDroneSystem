package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

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
     * with realistic decision making delays
     */
    public void getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.peek(); // Don't remove yet until we find a drone
            
            try {
                // Log incoming fire alert
                int zoneId = event.getZoneID();
                String severity = event.getSeverity();
                
                System.out.println(SchedulerColors.YELLOW + "\n[SCHEDULER] ALERT: Processing fire in zone " + 
                                 zoneId + " with " + severity + " severity" + SchedulerColors.RESET);
                
                // Simulate the emergency assessment process
                System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Assessing emergency severity and zone access..." + 
                                 SchedulerColors.RESET);
                Thread.sleep(1000); // 1 second for emergency assessment
                
                // Get the zone information
                Location zoneLocation = droneManager.getLocationForZone(zoneId);
                
                // Display emergency details
                System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Fire reported in Zone " + zoneId + 
                                 " at " + zoneLocation + 
                                 ", severity: " + severity + SchedulerColors.RESET);
                
                // Simulate drone fleet assessment
                System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Analyzing available drone fleet and calculating optimal response..." + 
                                 SchedulerColors.RESET);
                Thread.sleep(1500); // 1.5 seconds for drone selection analysis
                
                // Select best drone for this event
                DroneStatus bestDrone = droneManager.selectBestDroneForEvent(event);
                
                if (bestDrone != null) {
                    // Remove event from queue since we found a drone
                    events.poll();
                    
                    // Update fire status in zone
                    droneManager.updateZoneFireStatus(zoneId, true, event.getSeverity());
                    
                    // Prepare mission parameters
                    String droneId = bestDrone.getDroneId();
                    int distance = bestDrone.distanceTo(zoneLocation);
                    double estimatedResponseTime = distance / 10.0; // Assuming 10 units/second speed
                    
                    // Log the drone selection decision
                    System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Selected " + droneId + 
                                     " for Zone " + zoneId + " fire response:" + SchedulerColors.RESET);
                    System.out.println(SchedulerColors.GREEN + "  - Drone location: " + bestDrone.getCurrentLocation() + 
                                     "\n  - Zone location: " + zoneLocation + 
                                     "\n  - Distance: " + distance + " units" + 
                                     "\n  - Est. travel time: " + String.format("%.1f", estimatedResponseTime) + " seconds" + 
                                     "\n  - Previous missions: " + bestDrone.getZonesServiced() + 
                                     SchedulerColors.RESET);
                    
                    // Simulate mission preparation
                    System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Preparing mission parameters and flight plan..." + 
                                     SchedulerColors.RESET);
                    Thread.sleep(1000); // 1 second for mission preparation
                    
                    // Send to the selected drone
                    System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Transmitting mission to " + droneId + "..." + 
                                     SchedulerColors.RESET);
                    sendToDrone(event, droneId);
                    Thread.sleep(500); // 0.5 seconds for transmission
                    
                    // Update drone status with the new task
                    droneManager.updateDroneStatus(droneId, bestDrone.getState(), 
                                                 bestDrone.getCurrentLocation(), event);
                    
                    System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Mission successfully assigned to " + 
                                     droneId + SchedulerColors.RESET);
                } else {
                    // No available drones
                    System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] No available drones for fire in zone " + 
                                     zoneId + ". Keeping in queue." + SchedulerColors.RESET);
                    
                    // Display queue status
                    System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] Current queue size: " + 
                                     events.size() + " events waiting" + SchedulerColors.RESET);
                    
                    // Add a wait to simulate periodic rechecking
                    System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Will reassess availability in 2 seconds..." + 
                                     SchedulerColors.RESET);
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(SchedulerColors.RED + "[SCHEDULER] Processing interrupted" + SchedulerColors.RESET);
            }
        } else {
            // No events in queue
            System.out.println(SchedulerColors.CYAN + "[SCHEDULER] No fire events in queue, system on standby" + 
                             SchedulerColors.RESET);
            
            // Add a longer wait in standby mode
            try {
                System.out.println(SchedulerColors.CYAN + "[SCHEDULER] Performing routine system scan..." + 
                                 SchedulerColors.RESET);
                Thread.sleep(3000);
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
     * Prints a visual representation of the zones and drones
     */
    public void visualizeZonesAndDrones() {
        System.out.println(SchedulerColors.CYAN + "\n[SCHEDULER] Zone and Drone Visualization:" + SchedulerColors.RESET);
        
        // Get all zones
        Map<Integer, Zone> zones = droneManager.getAllZones();
        if (zones.isEmpty()) {
            System.out.println(SchedulerColors.RED + "  No zones defined!" + SchedulerColors.RESET);
            return;
        }
        
        // Find the bounds of all zones to determine display area
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        
        for (Zone zone : zones.values()) {
            minX = Math.min(minX, zone.getTopLeft().getX());
            minY = Math.min(minY, zone.getTopLeft().getY());
            maxX = Math.max(maxX, zone.getBottomRight().getX());
            maxY = Math.max(maxY, zone.getBottomRight().getY());
        }
        
        // Pad the display area
        minX = Math.max(0, minX - 5);
        minY = Math.max(0, minY - 5);
        maxX += 5;
        maxY += 5;
        
        // Get all drones
        Collection<DroneStatus> drones = droneManager.getAllDrones();
        
        // Display summary
        System.out.println(SchedulerColors.YELLOW + "  Map area: (" + minX + "," + minY + ") to (" + 
                         maxX + "," + maxY + ")" + SchedulerColors.RESET);
        System.out.println(SchedulerColors.YELLOW + "  Zones: " + zones.size() + 
                         ", Drones: " + drones.size() + SchedulerColors.RESET);
        
        // Display legend
        System.out.println(SchedulerColors.YELLOW + "  Legend:" + SchedulerColors.RESET);
        System.out.println(SchedulerColors.GREEN + "    Zone Center: Z#" + SchedulerColors.RESET);
        System.out.println(SchedulerColors.RED + "    Zone with Fire: F#" + SchedulerColors.RESET);
        System.out.println(SchedulerColors.BLUE + "    Drone: D#" + SchedulerColors.RESET);
        
        // Display zones
        System.out.println(SchedulerColors.CYAN + "\n  Zones:" + SchedulerColors.RESET);
        for (Zone zone : zones.values()) {
            String status = zone.hasFire() 
                ? SchedulerColors.RED + "FIRE(" + zone.getSeverity() + ")" + SchedulerColors.RESET
                : "NO FIRE";
            System.out.println(SchedulerColors.YELLOW + "    Zone " + zone.getId() + ": " + 
                             zone.getTopLeft() + " to " + zone.getBottomRight() + 
                             " (center: " + zone.getLocation() + "), " + status + SchedulerColors.RESET);
        }
        
        // Display drones
        System.out.println(SchedulerColors.CYAN + "\n  Drones:" + SchedulerColors.RESET);
        for (DroneStatus drone : drones) {
            String assignedZone = drone.getCurrentTask() != null 
                ? "Assigned to Zone " + drone.getCurrentTask().getZoneID() 
                : "No assignment";
            System.out.println(SchedulerColors.BLUE + "    Drone " + drone.getDroneId() + 
                             ": " + drone.getCurrentLocation() + ", State: " + drone.getState() + 
                             ", " + assignedZone + SchedulerColors.RESET);
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
            visualizeZonesAndDrones();
            
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
                    
                    // Visualize zones and drones
                    visualizeZonesAndDrones();
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
