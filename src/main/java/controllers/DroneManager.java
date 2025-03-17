package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import java.util.*;

/**
 * Manages multiple drones and their assignments
 */
public class DroneManager {
    private Map<String, DroneStatus> drones;
    private Map<Integer, Zone> zones;
    private Location baseLocation;
    
    /**
     * Creates a new drone manager with the specified base location
     * 
     * @param baseLocation the location of the drone base
     */
    public DroneManager(Location baseLocation) {
        this.drones = new HashMap<>();
        this.zones = new HashMap<>();
        this.baseLocation = baseLocation;
        initializeZones();
    }
    
    /**
     * Initialize the zone map with predefined zones
     */
    private void initializeZones() {
        // Create a simple grid of zones
        for (int i = 1; i <= 10; i++) {
            int x = ((i-1) % 3) * 10 + 10; // Create a 3x4 grid
            int y = ((i-1) / 3) * 10 + 10;
            zones.put(i, new Zone(i, new Location(x, y)));
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
     * Updates the fire status of a zone
     * 
     * @param zoneId the zone identifier
     * @param hasFire whether there is a fire
     * @param severity the severity of the fire
     */
    public void updateZoneFireStatus(int zoneId, boolean hasFire, String severity) {
        Zone zone = zones.get(zoneId);
        if (zone != null) {
            zone.setHasFire(hasFire);
            zone.setSeverity(severity);
        }
    }
    
    /**
     * Finds the best drone to handle a fire event based on availability, workload balance,
     * and proximity
     * 
     * @param event the fire event to handle
     * @return the selected drone status or null if no drones available
     */
    public DroneStatus selectBestDroneForEvent(FireEvent event) {
        int zoneId = event.getZoneID();
        Location fireLocation = getLocationForZone(zoneId);
        String severity = event.getSeverity();
        
        System.out.println("[DRONE MANAGER] Selecting best drone for fire in zone " + zoneId + 
                         " at " + fireLocation + " with severity " + severity);
        
        // First, look for an en-route drone that can be reassigned based on path
        DroneStatus enRouteDrone = findEnRouteDroneThatPasses(fireLocation, severity);
        if (enRouteDrone != null) {
            System.out.println("[DRONE MANAGER] Found en-route drone: " + enRouteDrone.getDroneId());
            return enRouteDrone;
        }
        
        // Otherwise, find available drone with balanced workload and proximity
        for (DroneStatus drone : drones.values()) {
            System.out.println("[DRONE MANAGER] Checking drone: " + drone.getDroneId() + 
                             ", state: '" + drone.getState() + "'" +
                             ", available: " + drone.isAvailable());
        }
        
        List<DroneStatus> availableDrones = drones.values().stream()
            .filter(DroneStatus::isAvailable)
            .sorted(Comparator.comparingInt(DroneStatus::getZonesServiced)
                    .thenComparingInt(d -> d.distanceTo(fireLocation)))
            .toList();
            
        if (availableDrones.isEmpty()) {
            System.out.println("[DRONE MANAGER] No available drones found.");
            return null;
        } else {
            DroneStatus selected = availableDrones.get(0);
            System.out.println("[DRONE MANAGER] Selected drone: " + selected.getDroneId() + 
                             " at " + selected.getCurrentLocation() +
                             ", distance: " + selected.distanceTo(fireLocation));
            return selected;
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
}