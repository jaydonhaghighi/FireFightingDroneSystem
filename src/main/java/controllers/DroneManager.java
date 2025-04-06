package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

/**
 * Manages multiple drones and their assignments
 */
public class DroneManager {
    private Map<String, DroneStatus> drones;
    private Map<Integer, Zone> zones;
    private Location baseLocation;
    private Map<Integer, Integer> zoneDropCounts; // Tracks the number of drops completed for each zone
    
    /**
     * Creates a new drone manager with the specified base location
     * 
     * @param baseLocation the location of the drone base
     */
    public DroneManager(Location baseLocation) {
        this.drones = new HashMap<>();
        this.zones = new HashMap<>();
        this.baseLocation = baseLocation;
        this.zoneDropCounts = new HashMap<>();
        initializeZones();
    }
    
    /**
     * Initialize the zone map by reading from zones.txt file
     */
    private void initializeZones() {
        String zonesFile = "src/main/resources/zones.txt";
//        System.out.println("[DRONE MANAGER] Loading zones from " + zonesFile);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(zonesFile))) {
            String line;
            int zoneCount = 0;
            
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    try {
                        int id = Integer.parseInt(parts[0]);
                        int x1 = Integer.parseInt(parts[1]);
                        int y1 = Integer.parseInt(parts[2]);
                        int x2 = Integer.parseInt(parts[3]);
                        int y2 = Integer.parseInt(parts[4]);
                        
                        Zone zone = new Zone(id, x1, y1, x2, y2);
                        zones.put(id, zone);
                        zoneCount++;
//                        System.out.println("[DRONE MANAGER] Loaded zone: " + zone);
                    } catch (NumberFormatException e) {
//                        System.err.println("[DRONE MANAGER] Error parsing zone data: " + line);
                    }
                }
            }
            
//            System.out.println("[DRONE MANAGER] Successfully loaded " + zoneCount + " zones");
            
        } catch (IOException e) {
//            System.err.println("[DRONE MANAGER] Error reading zones file: " + e.getMessage());
//            System.err.println("[DRONE MANAGER] Falling back to default zone grid");
            
            // Fallback to default grid if file cannot be read
            createDefaultZones();
        }
        
        // Ensure we have at least some zones defined
        if (zones.isEmpty()) {
//            System.err.println("[DRONE MANAGER] No zones loaded from file, creating defaults");
            createDefaultZones();
        }
    }
    
    /**
     * Creates a default grid of zones if the file cannot be read
     */
    private void createDefaultZones() {
        for (int i = 1; i <= 10; i++) {
            int x = ((i-1) % 3) * 10 + 10; // Create a 3x4 grid
            int y = ((i-1) / 3) * 10 + 10;
            Zone zone = new Zone(i, new Location(x, y));
            zones.put(i, zone);
//            System.out.println("[DRONE MANAGER] Created default zone: " + zone);
        }
    }
    
    /**
     * Registers a new drone with the system
     * 
     * @param droneId the unique drone identifier
     * @return the new drone status object
     */
    public DroneStatus registerDrone(String droneId) {
        DroneStatus status = new DroneStatus(droneId, baseLocation);
        drones.put(droneId, status);
        return status;
    }
    
    /**
     * Gets the status of a specific drone
     * 
     * @param droneId the drone identifier
     * @return the drone status or null if not found
     */
    public DroneStatus getDroneStatus(String droneId) {
        return drones.get(droneId);
    }
    
    /**
     * Gets all registered drones
     * 
     * @return a collection of all drone status objects
     */
    public Collection<DroneStatus> getAllDrones() {
        return drones.values();
    }
    
    /**
     * Gets all defined zones
     * 
     * @return a map of zone IDs to zones
     */
    public Map<Integer, Zone> getAllZones() {
        return Collections.unmodifiableMap(zones);
    }
    
    /**
     * Gets information about a specific zone
     * 
     * @param zoneId the zone identifier
     * @return the zone or null if not found
     */
    public Zone getZone(int zoneId) {
        return zones.get(zoneId);
    }
    
    /**
     * Gets the location for a specific zone
     * 
     * @param zoneId the zone identifier
     * @return the location of the zone or base location if zone not found
     */
    public Location getLocationForZone(int zoneId) {
        Zone zone = zones.get(zoneId);
        return zone != null ? zone.getLocation() : baseLocation;
    }
    
    /**
     * Creates a new zone with the specified ID and location
     * 
     * @param zoneId the zone identifier
     * @param location the location of the zone
     * @return the newly created zone
     */
    public Zone createZone(int zoneId, Location location) {
        Zone zone = new Zone(zoneId, location);
        zones.put(zoneId, zone);
        return zone;
    }
    
    /**
     * Updates the fire status of a zone
     * 
     * @param zoneId the zone identifier
     * @param hasFire whether there is a fire
     * @param severity the severity of the fire
     */
    public void updateZoneFireStatus(int zoneId, boolean hasFire, String severity) {
        Zone zone = zones.get(zoneId);
        
        // Create the zone if it doesn't exist
        if (zone == null) {
            Location zoneLocation = new Location(
                ((zoneId-1) % 3) * 700 + 350,  // 700m wide zones, centered at x+350
                ((zoneId-1) / 3) * 600 + 300); // 600m tall zones, centered at y+300
            
            zone = createZone(zoneId, zoneLocation);
        }
        
        // Update fire status
        zone.setHasFire(hasFire);
        zone.setSeverity(severity);
        
        // If a new fire is started, reset the drop count
        if (hasFire) {
            zoneDropCounts.put(zoneId, 0);
        } else {
            // If fire is being marked as extinguished, remove the drop count
            zoneDropCounts.remove(zoneId);
        }
    }
    
    /**
     * Records a fire agent drop for a zone and returns the new total
     * 
     * @param zoneId the zone identifier
     * @return the total drops for this zone after incrementing
     */
    public int recordDropForZone(int zoneId) {
        int currentDrops = zoneDropCounts.getOrDefault(zoneId, 0);
        int newTotal = currentDrops + 1;
        zoneDropCounts.put(zoneId, newTotal);
        return newTotal;
    }
    
    /**
     * Gets the current drop count for a zone
     * 
     * @param zoneId the zone identifier
     * @return the number of drops recorded for this zone
     */
    public int getDropCountForZone(int zoneId) {
        return zoneDropCounts.getOrDefault(zoneId, 0);
    }
    
    /**
     * Checks if a zone has received enough drops to extinguish its fire
     * 
     * @param zoneId the zone identifier
     * @param requiredDrops the number of drops required to extinguish the fire
     * @return true if the fire should be considered extinguished
     */
    public boolean isFireExtinguished(int zoneId, int requiredDrops) {
        int currentDrops = getDropCountForZone(zoneId);
        return currentDrops >= requiredDrops;
    }
    
    /**
     * Finds the best drone to handle a fire event based on availability, workload balance,
     * and proximity. Also avoids returning drones already assigned to this event.
     * 
     * @param event the fire event to handle
     * @return the selected drone status or null if no drones available
     */
    public DroneStatus selectBestDroneForEvent(FireEvent event) {
        int zoneId = event.getZoneID();
        Location fireLocation = getLocationForZone(zoneId);
        String severity = event.getSeverity();
        
        // Skip en-route drone reassignment - we want separate drones for each assignment
        
        // Find available drone with balanced workload and proximity
        // Also exclude drones already assigned to this event
        List<DroneStatus> availableDrones = drones.values().stream()
                .filter(DroneStatus::isAvailable)
                .filter(drone -> !drone.hasHardFault()) // Explicitly exclude drones with hard faults
                .filter(drone -> !event.isDroneAssigned(drone.getDroneId())) // Exclude drones already assigned
                .sorted(Comparator.comparingInt(DroneStatus::getZonesServiced)
                        .thenComparingInt(d -> d.distanceTo(fireLocation)))
                .toList();
            
        if (availableDrones.isEmpty()) {
            // No available drones found
            return null;
        } else {
            DroneStatus selected = availableDrones.get(0);
            return selected;
        }
    }
    
    /**
     * Finds an available drone for a fire event, avoiding duplicates.
     * Before assigning, checks the highest priority zone with active fire
     * and assigns the drone to that zone if higher priority.
     * 
     * @param event the original fire event
     * @param assignedDroneIds set of drone IDs already assigned
     * @param findHighestPriority whether to find and potentially redirect to highest priority fire 
     * @return the selected drone or null if none available
     */
    public DroneStatus findAvailableDrone(FireEvent event, Set<String> assignedDroneIds, boolean findHighestPriority) {
        try {
            // Use selectBestDroneForEvent to select best drone
            DroneStatus selectedDrone = selectBestDroneForEvent(event);
            
            if (selectedDrone == null) {
                return null;
            }
            
            String droneId = selectedDrone.getDroneId();
            
            // Check if drone is already in the assigned set
            if (assignedDroneIds.contains(droneId)) {
                return null;
            }
            
            // Check if drone is still available (could have changed state concurrently)
            if (!selectedDrone.isAvailable()) {
                return null;
            }
            
            // If we don't need to find highest priority, return drone as is
            if (!findHighestPriority) {
                return selectedDrone;
            }
            
            // 1. Find the zone with highest priority fire
            Map.Entry<Integer, Zone> highestPriorityZone = zones.entrySet().stream()
                .filter(entry -> entry.getValue().hasFire())
                .max((e1, e2) -> {
                    // Compare by severity weight (higher is better)
                    return getSeverityWeight(e1.getValue().getSeverity()) - 
                           getSeverityWeight(e2.getValue().getSeverity());
                })
                .orElse(null);
            
            // If no active fires, keep the original event
            if (highestPriorityZone == null) {
                return selectedDrone;
            }
            
            int highestPriorityZoneId = highestPriorityZone.getKey();
            String highestPrioritySeverity = highestPriorityZone.getValue().getSeverity();
            
            // 2. Check if the highest priority zone has an active fire
            boolean hasActiveFire = highestPriorityZone.getValue().hasFire();
            
            // 3. Compare with the provided event's zone
            int originalZoneId = event.getZoneID();
            String originalSeverity = event.getSeverity();
            
            // If the highest priority zone is different and has higher severity, redirect the drone
            if (hasActiveFire && highestPriorityZoneId != originalZoneId && 
                getSeverityWeight(highestPrioritySeverity) > getSeverityWeight(originalSeverity)) {
                
                // Check if the zone is already has enough drones
                int dronesNeeded = getDronesNeededForSeverity(highestPrioritySeverity);
                int dronesAssigned = countDronesForZone(highestPriorityZoneId);
                
                if (dronesAssigned < dronesNeeded) {
                    // Create a new fire event for this zone
                    FireEvent redirectEvent = createFireEventForZone(highestPriorityZoneId, highestPrioritySeverity);
                    
                    // Update the drone's task (caller will handle actual assignment)
                    redirectEvent.assignDrone(droneId);
                    selectedDrone.setCurrentTask(redirectEvent);
                }
            }
            
            return selectedDrone;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Find a drone that's currently en route to another fire but passes through or near the 
     * new fire location and can be reassigned based on severity
     * 
     * @param fireLocation the location of the new fire
     * @param severity the severity of the new fire
     * @return a drone that can be reassigned or null if none found
     */
    private DroneStatus findEnRouteDroneThatPasses(Location fireLocation, String severity) {
        for (DroneStatus drone : drones.values()) {
            if (("EN ROUTE".equalsIgnoreCase(drone.getState()) || "EnRoute".equalsIgnoreCase(drone.getState())) 
                && drone.getCurrentTask() != null) {
                // If this drone passes through the fire location on its current route
                Location droneCurrentLocation = drone.getCurrentLocation();
                Location droneTargetLocation = drone.getTargetLocation();
                
                // Check if new fire is on path and has same or higher severity
                if (fireLocation.isOnPath(droneCurrentLocation, droneTargetLocation)) {
                    FireEvent currentTask = drone.getCurrentTask();
                    if (isSameSeverityOrHigher(severity, currentTask.getSeverity())) {
                        return drone;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if a severity level is at least as high as another
     * 
     * @param severity1 the first severity level
     * @param severity2 the second severity level to compare against
     * @return true if severity1 is at least as high as severity2
     */
    private boolean isSameSeverityOrHigher(String severity1, String severity2) {
        Map<String, Integer> severityLevels = Map.of(
            "Low", 1,
            "Moderate", 2,
            "High", 3
        );
        
        int level1 = severityLevels.getOrDefault(severity1, 0);
        int level2 = severityLevels.getOrDefault(severity2, 0);
        
        return level1 >= level2;
    }
    
    /**
     * Updates drone status for a given drone ID
     * 
     * @param droneId the drone identifier
     * @param state the new state
     * @param location the new location
     * @param event the current fire event or null if none
     */
    public void updateDroneStatus(String droneId, String state, Location location, FireEvent event) {
        DroneStatus status = drones.get(droneId);
        if (status != null) {
            status.setState(state);
            status.setCurrentLocation(location);
            
            // If task completed, increment zones serviced
            if (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state)) 
                && status.getCurrentTask() != null) {
                status.incrementZonesServiced();
            }
            
            // Update current task
            status.setCurrentTask(event);
            
            // If drone has a task, update its target location
            if (event != null) {
                int zoneId = event.getZoneID();
                Location targetLocation = getLocationForZone(zoneId);
                status.setTargetLocation(targetLocation);
            } else {
                // If no task, target is base
                status.setTargetLocation(baseLocation);
            }
        }
    }
    
    /**
     * Creates a FireEvent object for a specified zone
     * @param zoneId the zone ID
     * @param severity the severity of the fire
     * @return a new FireEvent
     */
    public FireEvent createFireEventForZone(int zoneId, String severity) {
        // Generate a timestamp
        String timestamp = String.format("%d", System.currentTimeMillis());
        
        // Create a new fire event
        return new FireEvent(timestamp, zoneId, "FIRE", severity, "NONE");
    }
    
    /**
     * Counts the number of drones assigned to a specific zone
     * @param zoneId the zone ID to check
     * @return the number of drones assigned to this zone
     */
    public int countDronesForZone(int zoneId) {
        int count = 0;
        for (DroneStatus drone : getAllDrones()) {
            FireEvent task = drone.getCurrentTask();
            if (task != null && task.getZoneID() == zoneId &&
                !drone.getState().equalsIgnoreCase("IDLE") &&
                !drone.getState().equalsIgnoreCase("Idle")) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Determines the number of drones needed based on fire severity
     * @param severity the fire severity
     * @return the number of drones to dispatch
     */
    public int getDronesNeededForSeverity(String severity) {
        try {
            switch (severity.toLowerCase()) {
                case "high":
                    return 3; // 30L total capacity needed - 3 drones with 10L each
                case "moderate":
                    return 2; // 20L total capacity needed - 2 drones with 10L each
                case "low":
                default:
                    return 1; // 10L total capacity needed - 1 drone with 10L
            }
        } catch (Exception e) {
            // Default to 1 drone in case of error
            return 1;
        }
    }
    
    /**
     * Get zone location from zone ID
     * First attempts to get zone center via network request to scheduler,
     * then falls back to hardcoded calculation if that fails.
     * 
     * @param zoneId the zone ID
     * @return the location of the zone
     */
    public static Location getZoneLocation(int zoneId, InetAddress serverIP) {
        try {
            // Request zone info from scheduler
            DatagramSocket tempSocket = new DatagramSocket();
            tempSocket.setSoTimeout(1000); // 1 second timeout
            
            // Send request
            String request = "ZONE_INFO_REQUEST:" + zoneId;
            byte[] requestData = request.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(
                requestData, requestData.length, 
                serverIP, 6001
            );
            tempSocket.send(requestPacket);
            
            // Receive response
            byte[] responseData = new byte[100];
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length);
            tempSocket.receive(responsePacket);
            tempSocket.close();
            
            // Parse response
            String response = new String(responseData, 0, responsePacket.getLength());
            if (response.startsWith("ZONE_INFO:")) {
                String[] parts = response.split(":");
                if (parts.length >= 4) {
                    return new Location(
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                    );
                }
            }
        } catch (Exception e) {
            // Network request failed, use fallback
        }
        
        // Fallback: calculate zone location based on grid layout
        return new Location(
            ((zoneId-1) % 3) * 700 + 350, // centered at x+350
            ((zoneId-1) / 3) * 600 + 300  // centered at y+300
        );
    }
    
    /**
     * Returns the severity weight for a given severity level
     * @param severity the severity level
     * @return the numeric weight
     */
    public static int getSeverityWeight(String severity) {
        switch (severity.toLowerCase()) {
            case "high": return 100;
            case "moderate": return 50;
            case "low": return 10;
            default: return 0;
        }
    }
    
    /**
     * Helper class to store task info extracted from status messages
     */
    public static class DroneStatusInfo {
        public String droneId;
        public String state;
        public Location location;
        public int zoneId = -1;
        public String severity = null;
        public boolean isFireOut = false;
        public double currentCapacity = -1;
    }
    
    /**
     * Parse a drone status message
     * @param message The status message to parse
     * @return A DroneStatusInfo object containing the parsed information
     */
    public static DroneStatusInfo parseDroneStatusMessage(String message) {
        DroneStatusInfo info = new DroneStatusInfo();
        
        try {
            String[] parts = message.split(" ");
            
            // Must have at least: droneId state x y
            if (parts.length < 4) {
                return null;
            }
            
            // Extract basic information
            info.droneId = parts[0];
            info.state = parts[1];
            
            // Extract location (last two elements are coordinates)
            try {
                int x = Integer.parseInt(parts[parts.length - 2]);
                int y = Integer.parseInt(parts[parts.length - 1]);
                info.location = new Location(x, y);
            } catch (NumberFormatException e) {
                return null;
            }
            
            // Look for task data in the message
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
                } else if (part.startsWith("CAPACITY:")) {
                    String[] capacityParts = part.split(":");
                    if (capacityParts.length >= 2) {
                        try {
                            info.currentCapacity = Double.parseDouble(capacityParts[1]);
                        } catch (NumberFormatException e) {
                            // Ignore capacity parse errors
                        }
                    }
                }
            }
            
            return info;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Checks if a message is a valid drone status update
     * @param message The message to check
     * @return true if it's a status update, false otherwise
     */
    public static boolean isDroneStatusMessage(String message) {
        try {
            String[] parts = message.split(" ");
            
            // Must have at least: droneId state x y
            if (parts.length < 4) {
                return false;
            }
            
            // First part must be a drone ID
            String droneId = parts[0];
            if (!droneId.startsWith("drone")) {
                return false;
            }
            
            // Last two elements should be coordinates
            try {
                Integer.parseInt(parts[parts.length - 2]);
                Integer.parseInt(parts[parts.length - 1]);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}