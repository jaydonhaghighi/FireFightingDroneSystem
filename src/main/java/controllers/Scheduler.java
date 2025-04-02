package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import javax.swing.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.io.*;
import java.net.*;
import java.util.*;

import static models.FireEvent.createFireEventFromString;

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
    
    // Event management using priority queue based on severity
    private PriorityBlockingQueue<FireEvent> events = new PriorityBlockingQueue<>(20, new FireEventComparator());
    
    // Drone management
    private DroneManager droneManager;
    private Map<String, Integer> dronePorts = new HashMap<>(); // Maps drone IDs to their receive ports
    
    // Fire events tracking
    private Map<Integer, Integer> fireEventAssignedDrones = new HashMap<>(); // Maps zoneId to number of drones assigned
    private Map<Integer, Integer> fireEventRequiredDrones = new HashMap<>(); // Maps zoneId to number of drones required
    
    // Visualization component
    private DroneVisualization visualization;

    /**
     * Custom comparator for FireEvent to prioritize based on severity
     */
    static class FireEventComparator implements Comparator<FireEvent> {
        @Override
        public int compare(FireEvent e1, FireEvent e2) {
            // Compare severity first (High > Moderate > Low)
            int severityCompare = getSeverityWeight(e2.getSeverity()) - getSeverityWeight(e1.getSeverity());
            if (severityCompare != 0) return severityCompare;
            
            // If severity is the same, compare by time (older events first)
            return e1.getTime().compareTo(e2.getTime());
        }
        
        private int getSeverityWeight(String severity) {
            switch (severity.toLowerCase()) {
                case "high": return 100;
                case "moderate": return 50;
                case "low": return 10;
                default: return 0;
            }
        }
    }

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
            
            // Initialize the visualization component
            javax.swing.SwingUtilities.invokeLater(() -> {
                visualization = new DroneVisualization(droneManager);
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
            e.printStackTrace();
        }

        int len = receivePacket.getLength();
        String r = new String(data, 0, len);
        
        // Check if this is a drone status update
        if (isDroneStatusUpdate(r)) {
            processDroneStatusUpdate(r);
            return null;  // Early return, we don't want to continue parsing as FireEvent if it's a status update
        }

        // Try to parse as a FireEvent
        try {
            FireEvent fireEvent = createFireEventFromString(r);
            if (fireEvent != null) {
                return fireEvent;  // Return the valid fire event
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            
            return true;
        } catch (Exception e) {
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
            } else {
                // Update existing drone
                updateExistingDrone(status, droneId, state, location, taskInfo);
            }
            
            // If a drone becomes idle, check if there are active fires to assign it to
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state))) {
                checkForPendingFireEvents(status);
            }
            
            // Update visualization if needed
            if (visualization != null) {
                visualization.updateVisualization();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            // If drone was previously assigned to a fire, update our tracking
            if (currentTask != null) {
                int zoneId = currentTask.getZoneID();
                // Decrement the count of assigned drones for this zone
                if (fireEventAssignedDrones.containsKey(zoneId)) {
                    int assigned = fireEventAssignedDrones.get(zoneId);
                    fireEventAssignedDrones.put(zoneId, Math.max(0, assigned - 1));
                }
            }
            currentTask = null; // Clear task if drone is idle
        }
        
        // Update drone status
        droneManager.updateDroneStatus(droneId, state, location, currentTask);
        
        // Handle fire extinguished notification
        if (taskInfo.isFireOut && taskInfo.zoneId > 0) {
            // Update zone to mark fire as extinguished
            droneManager.updateZoneFireStatus(taskInfo.zoneId, false, "NONE");
            
            // Remove tracking for this fire event
            fireEventAssignedDrones.remove(taskInfo.zoneId);
            fireEventRequiredDrones.remove(taskInfo.zoneId);
        }
    }

    /**
     * Check if there are pending fire events that need drones and assign this idle drone if needed
     */
    private void checkForPendingFireEvents(DroneStatus drone) {
        // First update all our maps to remove any fires that are extinguished
        cleanupExtinguishedFires();
        
        // Find the highest priority fire that needs more drones
        Integer highestPriorityZoneId = null;
        String highestPrioritySeverity = null;
        
        for (Map.Entry<Integer, Integer> entry : fireEventRequiredDrones.entrySet()) {
            int zoneId = entry.getKey();
            int required = entry.getValue();
            int assigned = fireEventAssignedDrones.getOrDefault(zoneId, 0);
            
            // If this zone needs more drones
            if (assigned < required) {
                Zone zone = droneManager.getZone(zoneId);
                if (zone != null && zone.hasFire()) {
                    String severity = zone.getSeverity();
                    
                    // If this is our first candidate or has higher severity than current candidate
                    if (highestPriorityZoneId == null || getSeverityWeight(severity) > getSeverityWeight(highestPrioritySeverity)) {
                        highestPriorityZoneId = zoneId;
                        highestPrioritySeverity = severity;
                    }
                }
            }
        }
        
        // If we found a fire that needs drones, dispatch this drone to it
        if (highestPriorityZoneId != null) {
            // Create a FireEvent for this zone
            FireEvent event = createFireEventForZone(highestPriorityZoneId, highestPrioritySeverity);
            
            // Send to drone and update tracking
            if (event != null) {
                // Update drone status
                droneManager.updateDroneStatus(drone.getDroneId(), "EnRoute", drone.getCurrentLocation(), event);
                
                // Increment assigned drone count
                int assigned = fireEventAssignedDrones.getOrDefault(highestPriorityZoneId, 0) + 1;
                fireEventAssignedDrones.put(highestPriorityZoneId, assigned);
                
                // Send event to drone
                sendToDrone(event, drone.getDroneId());
            }
        }
    }
    
    /**
     * Removes any tracking for fires that are no longer active
     */
    private void cleanupExtinguishedFires() {
        List<Integer> toRemove = new ArrayList<>();
        
        for (Integer zoneId : fireEventRequiredDrones.keySet()) {
            Zone zone = droneManager.getZone(zoneId);
            if (zone == null || !zone.hasFire()) {
                toRemove.add(zoneId);
            }
        }
        
        for (Integer zoneId : toRemove) {
            fireEventRequiredDrones.remove(zoneId);
            fireEventAssignedDrones.remove(zoneId);
        }
    }
    
    /**
     * Creates a FireEvent object for a specified zone
     */
    private FireEvent createFireEventForZone(int zoneId, String severity) {
        // Generate a timestamp
        String timestamp = String.format("%d", System.currentTimeMillis());
        
        // Create a new fire event
        return new FireEvent(timestamp, zoneId, "FIRE", severity, "NONE");
    }
    
    /**
     * Helper method to get numeric weight for severity levels
     */
    private int getSeverityWeight(String severity) {
        switch (severity.toLowerCase()) {
            case "high": return 100;
            case "moderate": return 50;
            case "low": return 10;
            default: return 0;
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
            e.printStackTrace();
        }
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

            // Update zone fire status BEFORE dispatching drones to fix UI sync issue
            droneManager.updateZoneFireStatus(event.getZoneID(), true, event.getSeverity());
            
            // Update our tracking maps
            fireEventRequiredDrones.put(zoneId, dronesNeeded);
            fireEventAssignedDrones.putIfAbsent(zoneId, 0);
            
            // Update visualization to show fire before drones start moving
            if (visualization != null) {
                visualization.updateVisualization();
            }
            
            // Brief assessment delay before dispatching drones
            Thread.sleep(1000);

            // Dispatch drones
            List<DroneStatus> dispatchedDrones = dispatchDronesToFire(event, dronesNeeded);

            // Handle the results of dispatching
            handleDispatchResults(event, dispatchedDrones, dronesNeeded);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        
        int currentAssignedCount = fireEventAssignedDrones.getOrDefault(zoneId, 0);
        int remainingNeeded = dronesNeeded - currentAssignedCount;
        
        if (remainingNeeded <= 0) {
            return dispatchedDrones; // No need to dispatch more
        }
        
        for (int i = 0; i < remainingNeeded; i++) {
            // Find an available drone
            DroneStatus drone = findAvailableDrone(event, assignedDroneIds);
            
            if (drone != null) {
                // Process this drone
                String droneId = drone.getDroneId();
                dispatchedDrones.add(drone);
                assignedDroneIds.add(droneId);
                
                // Update tracking count
                int assigned = fireEventAssignedDrones.getOrDefault(zoneId, 0) + 1;
                fireEventAssignedDrones.put(zoneId, assigned);
                
                // Update drone status and send event
                droneManager.updateDroneStatus(droneId, "EnRoute", drone.getCurrentLocation(), event);
                sendToDrone(event, droneId);
                
                // Delay between dispatches
                if (i < remainingNeeded - 1) {
                    Thread.sleep(4000);
                }
            } else {
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
        int zoneId = event.getZoneID();
        int totalAssigned = fireEventAssignedDrones.getOrDefault(zoneId, 0);
        
        if (dispatchedDrones.isEmpty() && totalAssigned == 0) {
            // No drones were available at all
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        
        // At least one drone dispatched, or some already assigned, consider event handled
        events.poll(); // Remove event from queue
        
        // Update visualization
        if (visualization != null) {
            visualization.updateVisualization();
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
            e.printStackTrace();
        }
    }

    /**
     * Thread function for receiving messages
     */
    private void receiveMessages() {
        try {
            // Initial delay to allow drones to register
            Thread.sleep(5000); // 5 second delay to wait for drones

            while (true) {
                // Check for both fire events and drone status updates
                FireEvent event = receive();

                // If it's a fire event, add it to queue
                if (event != null) {
                    // Add to queue and send acknowledgement
                    events.add(event);
                    send(event, 5001, "response", "Fire Incident system");
                    
                    // Update the visual UI if initialized
                    if (visualization != null) {
                        visualization.updateVisualization();
                    }
                }

                // Brief pause to prevent tight loop - smaller delay for more responsive UI
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Thread function for processing events and assigning to drones
     */
    private void processEvents() {
        try {
            while (true) {
                if (!events.isEmpty()) {
                    // Process fire events and assign drones
                    processNextFireEvent();
                }

                // Brief pause between processing cycles
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}