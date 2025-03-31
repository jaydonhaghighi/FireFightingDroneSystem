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
    // Regular colors
    static final String RED = "\u001B[31m";
    static final String GREEN = "\u001B[32m";
    static final String YELLOW = "\u001B[33m";
    static final String BLUE = "\u001B[34m";
    static final String PURPLE = "\u001B[35m";
    static final String TEAL = "\u001B[38;5;27m";
    static final String LAVENDER = "\u001B[38;5;183m";
    // Bold colors
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
                    System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Received status update from: " + SchedulerColors.BLUE + droneId + SchedulerColors.RESET);
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
            
            // Check for error in state field
            boolean hasError = state.contains("ERROR");
            boolean hasNozzleJam = state.contains("NOZZLE_JAM");
            boolean hasMissionInfo = message.contains("TASK:");
            
            // Initialize location index based on whether there's task info
            int locationStartIndex = 2;
            int zoneId = -1;
            String severity = null;
            
            // Parse task information if present
            if (hasMissionInfo) {
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].startsWith("TASK:")) {
                        String[] taskParts = parts[i].split(":");
                        if (taskParts.length >= 3) {
                            zoneId = Integer.parseInt(taskParts[1]);
                            severity = taskParts[2];
                            locationStartIndex = i + 1;
                        }
                        break;
                    }
                }
            }
            
            // Parse location
            int x = Integer.parseInt(parts[locationStartIndex]);
            int y = Integer.parseInt(parts[locationStartIndex + 1]);
            Location location = new Location(x, y);

            // Register drone if not already registered
            DroneStatus status = droneManager.getDroneStatus(droneId);
            if (status == null) {
                status = droneManager.registerDrone(droneId);
                System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Registered new drone: " + SchedulerColors.BLUE + droneId + SchedulerColors.RESET);
            } else {
                // Check if state or location actually changed before logging
                boolean stateChanged = !status.getState().equalsIgnoreCase(state);
                boolean locationChanged = !status.getCurrentLocation().equals(location);

                // Only update status in DroneManager (we always want to track latest status)
                droneManager.updateDroneStatus(droneId, state, location, null);
                
                // Handle nozzle jam or communication failure - find a replacement drone
                boolean hasCommunicationFailure = state.contains("COMMUNICATION_FAILURE");
                if ((hasNozzleJam || hasCommunicationFailure) && hasMissionInfo && zoneId > 0) {
                    String errorType = hasNozzleJam ? "nozzle jam" : "communication failure";
                    System.out.println(SchedulerColors.BOLD_RED + "[SCHEDULER] Detected " + errorType + " on drone " + droneId + 
                                     " during mission to Zone " + zoneId + " - finding replacement" + SchedulerColors.RESET);
                    
                    // Create a fire event for the replacement
                    FireEvent replacementEvent = new FireEvent(
                        String.format("%02d:%02d:%02d", java.time.LocalTime.now().getHour(), 
                                       java.time.LocalTime.now().getMinute(), 
                                       java.time.LocalTime.now().getSecond()),
                        zoneId,
                        "FIRE",
                        severity,
                        "NONE"
                    );
                    
                    // Add to queue with high priority (at the front)
                    LinkedList<FireEvent> tempQueue = new LinkedList<>(events);
                    tempQueue.addFirst(replacementEvent);
                    events = tempQueue;
                    
                    System.out.println(SchedulerColors.BOLD_YELLOW + "[SCHEDULER] Added replacement mission to Zone " + 
                                     zoneId + " with " + severity + " severity to the front of queue" + SchedulerColors.RESET);
                }

                // Only print messages if something meaningful changed
                if (stateChanged || locationChanged) {
                    System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Updated drone status: " + SchedulerColors.BLUE + droneId +
                                      " at " + location + " in state " + state + SchedulerColors.RESET);
                }
                return; // Skip the duplicate log below if we're in the else branch
            }

            // This will only run for newly registered drones
            droneManager.updateDroneStatus(droneId, state, location, null);

            System.out.println(SchedulerColors.TEAL + "[SCHEDULER] Updated drone status: " + SchedulerColors.BLUE + droneId +
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
    public void getDroneTask() {
        if (!events.isEmpty()) {
            FireEvent event = events.peek(); // Don't remove yet until we find sufficient drones

            try {
                int zoneId = event.getZoneID();
                String severity = event.getSeverity();
                Location zoneLocation = droneManager.getLocationForZone(zoneId);

                // Determine how many drones we need for this severity
                int dronesNeeded = getDronesNeededForSeverity(severity);

                System.out.println(SchedulerColors.BOLD_RED + "[ALERT] " + severity + " fire in Zone " +
                                 zoneId + " at " + zoneLocation + " (requires " + dronesNeeded + " drones)" + 
                                 SchedulerColors.RESET);
                
                // Brief assessment delay
                Thread.sleep(1000);

                // Track the drones we've dispatched
                List<DroneStatus> dispatchedDrones = new ArrayList<>();

                // Attempt to dispatch the required number of drones
                for (int i = 0; i < dronesNeeded; i++) {
                    // Select best available drone for this event
                    DroneStatus drone = droneManager.selectBestDroneForEvent(event);

                    if (drone != null) {
                        // Add to dispatched list
                        dispatchedDrones.add(drone);

                        // Mission parameters
                        String droneId = drone.getDroneId();
                        int distance = drone.distanceTo(zoneLocation);
                        int previousMissions = drone.getZonesServiced();

                        // Mission assignment - includes drone count information
                        System.out.println(SchedulerColors.GREEN + "[ASSIGNED] " + droneId.toUpperCase() +
                                         " to Zone " + zoneId + " (" + 
                                         "Drone " + (i+1) + "/" + dronesNeeded + ", " +
                                         distance + " meters away)" +
                                         SchedulerColors.RESET);
                        
                        // Update drone status
                        droneManager.updateDroneStatus(droneId, drone.getState(),
                                drone.getCurrentLocation(), event);

                        // Send to the selected drone
                        sendToDrone(event, droneId);

                        // Delay between drone dispatches (3 seconds as requested)
                        if (i < dronesNeeded - 1) {
                            System.out.println(SchedulerColors.BOLD_WHITE + "[TIME] Waiting 2 seconds before dispatching next drone..." +
                                             SchedulerColors.RESET);
                            Thread.sleep(2000);
                        }
                    } else {
                        // Not enough available drones
                        System.out.println(SchedulerColors.YELLOW + "[PARTIAL RESPONSE] Could only dispatch " +
                                dispatchedDrones.size() + "/" + dronesNeeded + " drones to Zone " + zoneId +
                                SchedulerColors.RESET);
                        break;
                    }
                }

                // If we dispatched at least one drone, consider the event handled
                if (!dispatchedDrones.isEmpty()) {
                    // Remove event from queue since we dispatched drones
                    events.poll();

                    // Update fire status in zone
                    droneManager.updateZoneFireStatus(zoneId, true, severity);

                    // If we dispatched less than needed, log a warning
                    if (dispatchedDrones.size() < dronesNeeded) {
                        System.out.println(SchedulerColors.YELLOW + "[WARNING] Insufficient drones for " + severity +
                                " fire (sent " + dispatchedDrones.size() + "/" + dronesNeeded + ")" +
                                SchedulerColors.RESET);
                    } else {
                        System.out.println(SchedulerColors.GREEN + "[RESPONSE COMPLETE] Dispatched " +
                                dispatchedDrones.size() + " drones to Zone " + zoneId +
                                " (" + severity + " fire)" + SchedulerColors.RESET);
                    }
                } else {
                    // No available drones at all
                    System.out.println(SchedulerColors.RED + "[WAITING] No available drones for Zone " +
                            zoneId + " (" + events.size() + " events in queue)" +
                            SchedulerColors.RESET);
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(SchedulerColors.RED + "ERROR: Processing interrupted" + SchedulerColors.RESET);
            }
        }
        // "STANDBY" message moved to processEvents() method for better control
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
//                        System.out.println(SchedulerColors.CYAN + "[STANDBY] System monitoring" + SchedulerColors.RESET);
                        standbyMessageShown = true;
                    }
                    Thread.sleep(3000);
                } else {
                    // Reset standby message flag when events are present
                    standbyMessageShown = false;
                    // Process fire events and assign to drones
                    getDroneTask();
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
