package models;

import java.util.*;

public class FireEvent {
    /**The time at which the fire event occurred*/
    String time;
    /**The ID of the zone the fire occurred*/
    int zoneID;
    /**The type of fire event*/
    String eventType;
    /**The severity level of the fire event(e.g. High, Medium, Low*/
    String severity;
    /**The ID of the primary drone assigned to handle this event*/
    String assignedDroneId;
    
    /**List of all drones assigned to this event (for multi-drone responses)*/
    Set<String> assignedDrones = new HashSet<>();
    
    /**Count of completed agent drops for this fire*/
    private int dropsCompleted = 0;

    ErrorType error;

    public enum ErrorType {
        DRONE_STUCK, NOZZLE_JAM, DOOR_STUCK, ARRIVAL_SENSOR_FAILED, NONE
    }

    /**
     * Constructors a FireEvent with the specified parameters below
     * @param time The time at which the fire event occurred
     * @param zoneID The ID of the zone the fire occurred
     * @param eventType The type of fire event
     * @param severity The severity level of the fire event(e.g. High, Medium, Low
     */
    public FireEvent(String time, int zoneID, String eventType, String severity, boolean error) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        this.assignedDroneId = null;
        this.assignedDrones = new HashSet<>();


        if (error) {
            ErrorType[] errorTypes = ErrorType.values();
            int randomI = new Random().nextInt(errorTypes.length - 1);
            this.error = errorTypes[randomI];
        } else {
            this.error = ErrorType.NONE;
        }
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getZoneID() {
        return zoneID;
    }

    public void setZoneID(int zoneID) {
        this.zoneID = zoneID;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public ErrorType getError() { return error; }

    public boolean hasError() {
        if (error != ErrorType.NONE) {
            return true;
        }
        return false;
    }
    
    /**
     * Gets the ID of the drone assigned to this event
     * @return drone ID or null if no drone assigned
     */
    public String getAssignedDroneId() {
        return assignedDroneId;
    }
    
    /**
     * Assigns a drone to this event
     * @param droneId the ID of the drone to assign
     */
    public void assignDrone(String droneId) {
        this.assignedDroneId = droneId; // Keep for backward compatibility
        this.assignedDrones.add(droneId); // Add to the set of all assigned drones
    }
    
    /**
     * Gets all drones assigned to this event
     * @return set of assigned drone IDs
     */
    public Set<String> getAllAssignedDrones() {
        return assignedDrones;
    }
    
    /**
     * Checks if a specific drone is assigned to this event
     * @param droneId the drone ID to check
     * @return true if the drone is assigned to this event
     */
    public boolean isDroneAssigned(String droneId) {
        return assignedDrones.contains(droneId);
    }
    
    /**
     * Gets the number of drones assigned to this event
     * @return the count of assigned drones
     */
    public int getAssignedDroneCount() {
        return assignedDrones.size();
    }
    
    /**
     * Gets the number of agent drops completed for this fire
     * @return the count of completed drops
     */
    public int getDropsCompleted() {
        return dropsCompleted;
    }
    
    /**
     * Increments the number of agent drops completed for this fire
     */
    public void incrementDropsCompleted() {
        this.dropsCompleted++;
    }

    public static FireEvent createFireEventFromString(String input) {
        // Split the string into its components based on whitespace
        String[] parts = input.split(" ");
        String time = parts[0];
        int zoneID = Integer.parseInt(parts[1]);
        String eventType = parts[2];
        String severity = parts[3];

        // Create the new FireEvent object
        FireEvent event = new FireEvent(time, zoneID, eventType, severity, false);
        
        // Check if a drone ID is included
        if (parts.length > 4) {
            event.assignDrone(parts[4]);
        }
        
        return event;
    }

    /**
     * Returns a string representation of the fire event
     * @return A formatted string containing the event details.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" ")
          .append(zoneID).append(" ")
          .append(eventType).append(" ")
          .append(severity);
          
        // Add primary drone ID for backward compatibility
        if (assignedDroneId != null) {
            sb.append(" ").append(assignedDroneId);
        }
        
        // Add error information
        sb.append(" ").append(error);
        
        return sb.toString();
    }
    
    /**
     * Returns a more detailed string representation including all assigned drones
     * @return A formatted string containing the event details and all assigned drones
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FireEvent{")
          .append("time='").append(time).append("', ")
          .append("zoneID=").append(zoneID).append(", ")
          .append("severity='").append(severity).append("', ")
          .append("drones=").append(assignedDrones).append(", ")
          .append("error=").append(error)
          .append("}");
          
        return sb.toString();
    }
}
