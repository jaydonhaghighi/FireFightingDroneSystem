package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import javax.swing.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.*;
import java.net.*;
import java.util.*;

import static models.FireEvent.createFireEventFromString;

/**
 * ANSI colors for console output
 */
class SchedulerColors {
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String PURPLE = "\u001B[35m";
    static final String TEAL = "\u001B[38;5;27m";
    static final String LAVENDER = "\u001B[38;5;183m";
    static final String BOLD_RED = "\u001B[1;31m";
    static final String BOLD_YELLOW = "\u001B[1;33m";
    static final String BOLD_WHITE = "\u001B[1;37m";
    static final String BOLD_ORANGE = "\u001B[1;38;5;208m";
    static final String BOLD_LIME = "\u001B[1;38;5;154m";
    static final String BOLD_MAROON = "\u001B[1;38;5;88m";
}

/**
 * The Scheduler class manages the flow of fire incident events and drone responses.
 * It processes incoming fire events, assigns them to drones, and forwards drone responses
 * to the FireIncidentSubsystem.
 */
public class Scheduler {
    // Network components
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendSocket, receiveSocket;
    private final int sendPort = 6000;
    private final int receivePort = 6001;
    private final InetAddress fireIncidentIP;
    
    // Event management
    private Queue<FireEvent> events = new LinkedBlockingQueue<>();
    
    // Drone management
    private DroneManager droneManager;
    private Map<String, Integer> dronePorts = new HashMap<>(); // Maps drone IDs to their receive ports
    
    // Visualization component
    private DroneVisualization visualization;

    /**
     * Constructs a new Scheduler with drone management capability
     *
     * @param ip The IP address of the fire incident system
     */
    public Scheduler(InetAddress ip) {
        this.fireIncidentIP = ip;
        Location baseLocation = new Location(0, 0);
        this.droneManager = new DroneManager(baseLocation);

        // Register drone ports for 10 drones using the same calculation as in DroneSubsystem
        for (int i = 1; i <= 10; i++) {
            String droneId = "drone" + i;
            int port = 7001 + (i * 100); // Same calculation as DroneSubsystem uses
            registerDronePort(droneId, port);
        }

        try {
            sendSocket = new DatagramSocket(sendPort);
            receiveSocket = new DatagramSocket(receivePort);
            System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Initialized at " + baseLocation + SchedulerColors.RESET);
            
            // Initialize the visualization component
            javax.swing.SwingUtilities.invokeLater(() -> {
                visualization = new DroneVisualization(droneManager);
                System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Visualization initialized" + SchedulerColors.RESET);
            });
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
        System.out.println(SchedulerColors.PURPLE + "[SCHEDULER] Registered " + droneId + " on port " + port + SchedulerColors.RESET);
    }

    /**
     * Receives a message and processes it based on content
     * @return FireEvent if it's a fire event, null otherwise
     */
    public FireEvent receive() {
        byte[] data = new byte[100];
        receivePacket = new DatagramPacket(data, data.length);

        try {
            receiveSocket.receive(receivePacket);  // Receive the packet as usual
        } catch (IOException e) {
            System.out.println("receive error: " + e);
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);

        System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Received packet: " + SchedulerColors.BLUE + r + SchedulerColors.RESET);
        
        // Check if this is a drone status update
        if (isDroneStatusUpdate(r)) {
            processDroneStatusUpdate(r);
            return null;  // Early return, we don't want to continue parsing as FireEvent if it's a status update
        }

        // Try to parse as a FireEvent
        try {
            FireEvent fireEvent = createFireEventFromString(r);
            if (fireEvent != null) {
                System.out.println(SchedulerColors.GREEN + "[SCHEDULER] Fire event received: " + fireEvent + SchedulerColors.RESET);
                return fireEvent;  // Return the valid fire event
            }
        } catch (Exception e) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Warning: Received a message that could not be parsed as a FireEvent: " + r + SchedulerColors.RESET);
        }

        return null;
    }


    /**
     * Checks if a message is a drone status update
     * @param message The message to check
     * @return true if it's a status update, false otherwise
     */
    private boolean isDroneStatusUpdate(String message) {
        try {
            String[] parts = message.split(" ");
            // Must have at least: droneId state x y
            if (parts.length < 4) {
                return false;
            }
            
            String droneId = parts[0];
            if (!droneId.startsWith("drone")) {
                return false;
            }
            
            // Last two elements should be coordinates
            int x = Integer.parseInt(parts[parts.length - 2]);
            int y = Integer.parseInt(parts[parts.length - 1]);
            
            System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Received status update from: " + 
                              SchedulerColors.BLUE + droneId + SchedulerColors.RESET);
            return true;
        } catch (Exception e) {
            // If we can't parse the message as a drone status, it's not a drone status
            System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] Message not recognized as drone status: " + 
                              message + " (" + e.getMessage() + ")" + SchedulerColors.RESET);
            return false;
        }
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
            
            // Extract location (last two elements are x and y coordinates)
            int x = Integer.parseInt(parts[parts.length - 2]);
            int y = Integer.parseInt(parts[parts.length - 1]);
            Location location = new Location(x, y);
            
            // Parse task and fire status information
            TaskInfo taskInfo = extractTaskInfo(parts);
            
            // Register or update drone
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                // Register new drone
                status = droneManager.registerDrone(droneId);
                droneManager.updateDroneStatus(droneId, state, location, null);
                System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Registered new drone: " + 
                                  SchedulerColors.BLUE + droneId + SchedulerColors.RESET);
            } else {
                // Update existing drone
                updateExistingDrone(status, droneId, state, location, taskInfo);
            }
            
            // Update visualization if needed
            if (visualization != null) {
                visualization.updateVisualization();
            }
        } catch (Exception e) {
            System.out.println(SchedulerColors.RED + "[SCHEDULER] Error processing drone status: " + e + SchedulerColors.RESET);
        }
    }
    
    /**
     * Helper class to store task info extracted from status messages
     */
    private static class TaskInfo {
        int zoneId = -1;
        String severity = null;
        boolean isFireOut = false;
    }
    
    /**
     * Extracts task and fire status information from a status message
     */
    private TaskInfo extractTaskInfo(String[] parts) {
        TaskInfo info = new TaskInfo();
        
        // Look for TASK: and FIRE_OUT: tags in the message
        for (String part : parts) {
            if (part.startsWith("TASK:")) {
                String[] taskParts = part.split(":");
                if (taskParts.length >= 3) {
                    info.zoneId = Integer.parseInt(taskParts[1]);
                    info.severity = taskParts[2];
                }
            } else if (part.startsWith("FIRE_OUT:")) {
                String[] fireOutParts = part.split(":");
                if (fireOutParts.length >= 2) {
                    info.zoneId = Integer.parseInt(fireOutParts[1]);
                    info.isFireOut = true;
                    System.out.println(SchedulerColors.BOLD_LIME + "[SCHEDULER] Received fire extinguished notification for Zone " + 
                                      info.zoneId + SchedulerColors.RESET);
                }
            }
        }
        
        return info;
    }
    
    /**
     * Updates an existing drone with new status information
     */
    private void updateExistingDrone(DroneStatus status, String droneId, String state, 
                                   Location location, TaskInfo taskInfo) {
        // Check if state or location changed (for logging)
        boolean stateChanged = !status.getState().equalsIgnoreCase(state);
        boolean locationChanged = !status.getCurrentLocation().equals(location);

        // Determine current task
        FireEvent currentTask = status.getCurrentTask();
        if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state))) {
            currentTask = null; // Clear task if drone is idle
        }
        
        // Update drone status
        droneManager.updateDroneStatus(droneId, state, location, currentTask);
        
        // Handle fire extinguished notification
        if (taskInfo.isFireOut && taskInfo.zoneId > 0) {
            System.out.println(SchedulerColors.BOLD_LIME + "🔥✓ [SCHEDULER] Fire in Zone " + taskInfo.zoneId + 
                              " has been EXTINGUISHED by " + droneId + SchedulerColors.RESET);
            
            // Update zone to mark fire as extinguished
            droneManager.updateZoneFireStatus(taskInfo.zoneId, false, "NONE");
            
            // Refresh visualization
            visualizeZonesAndDrones();
        }
        
        // Log status change if needed
        if (stateChanged || locationChanged) {
            System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Updated drone status: " + 
                              SchedulerColors.BLUE + droneId + " at " + location + 
                              " in state " + state + SchedulerColors.RESET);
        }
    }

    /**
     * Sends a UDP packet to the designated ip and port
     * @param fire the fire event that is being sent
     * @param port which port the data should be sent to
     * @param what a description of what is being sent *ONLY USED FOR DATA LOGGING PURPOSES*
     * @param location where the data is being sent *ONLY USED FOR DATA LOGGING PURPOSES*
     *
     */
    public void send(FireEvent fire, int port, String what, String location) {
        String message = fire.toString();
        byte[] msg = message.getBytes();

        try {
            sendPacket = new DatagramPacket(msg, msg.length, InetAddress.getLocalHost(), port);
        } catch (UnknownHostException e) {
            System.out.println("Error: cannot find host: " + e);
        }
        System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Sending " + what + " to " + location + ": " + SchedulerColors.BLUE + message + SchedulerColors.RESET);
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
     * Determines the number of drones needed based on fire severity
     *
     * @param severity the fire severity
     * @return the number of drones to dispatch
     */
    private int getDronesNeededForSeverity(String severity) {
        switch (severity.toLowerCase()) {
            case "high":
                return 3; // 30L total capacity needed - 3 drones with 10L each
            case "moderate":
                return 2; // 20L total capacity needed - 2 drones with 10L each
            case "low":
            default:
                return 1; // 10L total capacity needed - 1 drone with 10L
        }
    }

    /**
     * Processes the next fire event in the queue and assigns it to the appropriate number of drones
     * based on fire severity
     */
    public void processNextFireEvent() {
        if (events.isEmpty()) {
            return;
        }
        
        FireEvent event = events.peek(); // Don't remove until we find sufficient drones
        
        try {
            // Extract event details
            int zoneId = event.getZoneID();
            String severity = event.getSeverity();
            Location zoneLocation = droneManager.getLocationForZone(zoneId);
            int dronesNeeded = getDronesNeededForSeverity(severity);

            // Log fire alert
            System.out.println(SchedulerColors.BOLD_RED + "[ALERT] " + severity + " fire in Zone " +
                             zoneId + " at " + zoneLocation + " (requires " + dronesNeeded + " drones)" + 
                             SchedulerColors.RESET);
            
            // Brief assessment delay before dispatching drones
            Thread.sleep(1000);

            // Dispatch drones
            List<DroneStatus> dispatchedDrones = dispatchDronesToFire(event, dronesNeeded);

            // Handle the results of dispatching
            handleDispatchResults(event, dispatchedDrones, dronesNeeded);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(SchedulerColors.RED + "ERROR: Processing interrupted" + SchedulerColors.RESET);
        }
    }
    
    /**
     * Dispatches the required number of drones to a fire event
     */
    private List<DroneStatus> dispatchDronesToFire(FireEvent event, int dronesNeeded) throws InterruptedException {
        List<DroneStatus> dispatchedDrones = new ArrayList<>();
        Set<String> assignedDroneIds = new HashSet<>(); // Track assigned drones
        int zoneId = event.getZoneID();
        Location zoneLocation = droneManager.getLocationForZone(zoneId);
        
        for (int i = 0; i < dronesNeeded; i++) {
            // Find an available drone
            DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
            
            if (drone != null) {
                // Process this drone
                String droneId = drone.getDroneId();
                dispatchedDrones.add(drone);
                assignedDroneIds.add(droneId);
                
                // Log assignment
                int distance = drone.distanceTo(zoneLocation);
                System.out.println(SchedulerColors.GREEN + "[ASSIGNED] " + droneId.toUpperCase() +
                                 " to Zone " + zoneId + " (" + 
                                 "Drone " + (i+1) + "/" + dronesNeeded + ", " +
                                 distance + " meters away)" +
                                 SchedulerColors.RESET);
                
                // Update drone status and send event
                droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                sendToDrone(event, droneId);
                
                // Delay between dispatches
                if (i < dronesNeeded - 1) {
                    System.out.println(SchedulerColors.BOLD_WHITE + 
                                     "[TIME] Waiting 2 seconds before dispatching next drone..." +
                                     SchedulerColors.RESET);
                    Thread.sleep(2000);
                }
            } else {
                // No more available drones
                System.out.println(SchedulerColors.YELLOW + "[PARTIAL RESPONSE] Could only dispatch " +
                                 dispatchedDrones.size() + "/" + dronesNeeded + " drones to Zone " + zoneId +
                                 SchedulerColors.RESET);
                break;
            }
        }
        
        return dispatchedDrones;
    }
    
    /**
     * Finds an available drone for a fire event, avoiding duplicates
     */
    private DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds) 
            throws InterruptedException {
        final int MAX_ATTEMPTS = 10;
        int attempts = 0;
        
        while (attempts < MAX_ATTEMPTS) {
            DroneStatus drone = droneManager.selectBestDroneForEvent(event);
            attempts++;
            
            if (drone == null) {
                return null; // No available drones
            }
            
            if (assignedDroneIds.contains(drone.getDroneId())) {
                System.out.println(SchedulerColors.YELLOW + "[SCHEDULER] Drone " + drone.getDroneId() + 
                                 " already assigned, looking for another drone" + SchedulerColors.RESET);
                
                // Already used this drone, try again
                Thread.sleep(100); // Avoid tight loops
                continue;
            }
            
            return drone; // Found an available drone
        }
        
        return null; // Couldn't find a suitable drone after max attempts
    }
    
    /**
     * Handles the results of dispatching drones to a fire
     */
    private void handleDispatchResults(FireEvent event, List<DroneStatus> dispatchedDrones, int dronesNeeded) {
        if (dispatchedDrones.isEmpty()) {
            // No available drones
            try {
                System.out.println(SchedulerColors.RED + "[WAITING] No available drones for Zone " +
                                 event.getZoneID() + " (" + events.size() + " events in queue)" +
                                 SchedulerColors.RESET);
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        
        // At least one drone dispatched, consider event handled
        events.poll(); // Remove event from queue
        
        // Update zone fire status
        droneManager.updateZoneFireStatus(event.getZoneID(), true, event.getSeverity());
        
        // Log results
        if (dispatchedDrones.size() < dronesNeeded) {
            System.out.println(SchedulerColors.YELLOW + "[WARNING] Insufficient drones for " + 
                             event.getSeverity() + " fire (sent " + dispatchedDrones.size() + 
                             "/" + dronesNeeded + ")" + SchedulerColors.RESET);
        } else {
            System.out.println(SchedulerColors.GREEN + "[RESPONSE COMPLETE] Dispatched " +
                             dispatchedDrones.size() + " drones to Zone " + event.getZoneID() +
                             " (" + event.getSeverity() + " fire)" + SchedulerColors.RESET);
        }
        
        // Update visualization
        if (visualization != null) {
            visualization.updateVisualization();
        }
    }

    /**
     * Prints a simplified visual representation of the system
     */
    public void visualizeZonesAndDrones() {
        // Get all zones and drones
        Map<Integer, Zone> zones = droneManager.getAllZones();
        Collection<DroneStatus> drones = droneManager.getAllDrones();

        if (zones.isEmpty()) {
            System.out.println(SchedulerColors.RED + "[ERROR] No zones defined" + SchedulerColors.RESET);
            return;
        }

        // Show active fires
        List<Zone> firesZones = zones.values().stream()
                .filter(Zone::hasFire)
                .sorted(Comparator.comparing(Zone::getId))
                .toList();

        if (!firesZones.isEmpty()) {
            System.out.println(SchedulerColors.BOLD_RED + "\n[ACTIVE FIRES]" + SchedulerColors.RESET);
            for (Zone zone : firesZones) {
                System.out.println(SchedulerColors.BOLD_MAROON + "Zone " + zone.getId() + ": " +
                                 zone.getSeverity() + " at " + zone.getLocation() + 
                                 SchedulerColors.RESET);
            }
        }

        // Show active drones - only those on missions
        List<DroneStatus> activeDrones = drones.stream()
                .filter(d -> !d.isAvailable())
                .sorted(Comparator.comparing(DroneStatus::getDroneId))
                .toList();

        if (!activeDrones.isEmpty()) {
            for (DroneStatus drone : activeDrones) {
                if (drone.getCurrentTask() != null) {
                    System.out.println(SchedulerColors.BLUE + "    " + drone.getDroneId() + ": " +
                            drone.getCurrentLocation() + " to Zone " +
                            drone.getCurrentTask().getZoneID() +
                            SchedulerColors.RESET);
                }
            }
        }

        // Show available drones - simple count
        long availableCount = drones.stream().filter(DroneStatus::isAvailable).count();
        if (availableCount > 0) {
            System.out.println("\n" + SchedulerColors.BOLD_LIME + availableCount + " drone(s) available" +
                    SchedulerColors.RESET);
            System.out.println(SchedulerColors.BOLD_ORANGE + activeDrones.size() + " drone(s) active\n" +
                           SchedulerColors.RESET);
        } else if (!drones.isEmpty()) {
            System.out.println(SchedulerColors.BOLD_YELLOW + "[WARNING] No available drones" +
                           SchedulerColors.RESET);
        }
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
            visualizeZonesAndDrones();

            while (true) {
                // Check for both fire events and drone status updates
                FireEvent event = receive();

                // If it's a fire event, add it to queue
                if (event != null) {
                    // Add to queue and send acknowledgement
                    events.add(event);
                    send(event, 5001, "response", "Fire Incident system");

                    // Visualize zones and drones
                    visualizeZonesAndDrones();
                    
                    // Update the visual UI if initialized
                    if (visualization != null) {
                        visualization.updateVisualization();
                    }
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
            boolean standbyMessageShown = false;

            while (true) {
                if (events.isEmpty()) {
                    // Only show standby message once while idle
                    if (!standbyMessageShown) {
                        System.out.println(SchedulerColors.LAVENDER + "[STANDBY] System monitoring" + SchedulerColors.RESET);
                        standbyMessageShown = true;
                    }
                    Thread.sleep(3000);
                } else {
                    // Reset standby message flag when events are present
                    standbyMessageShown = false;
                    // Process fire events and assign drones
                    processNextFireEvent();
                }

                // Brief pause between processing cycles
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("Error in process thread: " + e);
            e.printStackTrace();
        }
    }
}
