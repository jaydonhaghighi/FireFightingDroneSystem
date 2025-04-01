package models;

import java.util.*;

public class FireEvent {
    /**The time at which the fire event occurred*/
    String time;
    /**The ID of the zone the fire occurred*/
    int zoneID;
    /**The type of fire event*/
    String eventType;
    /**The severity level of the fire event(e.g. High, Medium, Low)*/
    String severity;
    /**The ID of the primary drone assigned to handle this event*/
    String assignedDroneId;

    /**List of all drones assigned to this event (for multi-drone responses)*/
    Set<String> assignedDrones = new HashSet<>();

    /**Count of completed agent drops for this fire*/
    private int dropsCompleted = 0;

    public ErrorType error;

    public enum ErrorType {
        DRONE_STUCK, NOZZLE_JAM, DOOR_STUCK, ARRIVAL_SENSOR_FAILED, COMMUNICATION_FAILURE, NONE
    }

    /**
     * Constructor to create a FireEvent with the specified parameters
     * @param time The time at which the fire event occurred
     * @param zoneID The ID of the zone the fire occurred
     * @param eventType The type of fire event
     * @param severity The severity level of the fire event (e.g., High, Medium, Low)
     * @param errorType The type of error for this fire event (e.g., NOZZLE_JAM, DOOR_STUCK, or NONE)
     */
    public FireEvent(String time, int zoneID, String eventType, String severity, String errorType) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        this.assignedDroneId = null;
        this.assignedDrones = new HashSet<>();

        // Set the error type based on the passed string - no random errors
        if (errorType.equals("ERROR")) {
            // Set to NONE instead of randomly assigning an error
            this.error = ErrorType.NONE;
        } else {
            // Try to match a specific error type
            setErrorFromString(errorType);
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

    public ErrorType getError() {
        return error;
    }

    public boolean hasError() {
        return error != ErrorType.NONE;
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
        
        // Make sure we're not adding the same drone multiple times
        if (this.assignedDrones.contains(droneId)) {
            System.out.println("[FireEvent] Warning: Drone " + droneId + " already assigned to event for Zone " + zoneID);
        } else {
            this.assignedDrones.add(droneId); // Add to the set of all assigned drones
        }
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

    /**
     * Checks if the fire event is corrupted (e.g., missing or invalid fields)
     * @return true if the event data is corrupted, false otherwise
     */
    public boolean isCorrupted() {
        return time == null || time.isEmpty() ||
                zoneID < 0 ||
                eventType == null || eventType.isEmpty() ||
                severity == null || severity.isEmpty();
    }

    /**
     * Creates a FireEvent object from a string representation
     * @param input The string input containing the fire event data
     * @return the FireEvent object created from the string
     */
    public static FireEvent createFireEventFromString(String input) {
        try {
            // Split the string into its components based on whitespace
            String[] parts = input.trim().split(" ");

            // Ensure the string contains at least 4 parts (time, zoneID, eventType, severity)
            if (parts.length < 4) {
                throw new IllegalArgumentException("Incomplete fire event data");
            }

            String time = parts[0];
            int zoneID = Integer.parseInt(parts[1]);
            String eventType = parts[2];
            String severity = parts[3];
            
            // Check if the last part is an error type (could be at position 4 or 5)
            String errorType = "NONE";
            if (parts.length > 4) {
                String lastPart = parts[parts.length - 1];
                if (isErrorType(lastPart)) {
                    errorType = lastPart;
                }
            }

            FireEvent event = new FireEvent(time, zoneID, eventType, severity, errorType);

            // Check if a drone ID is included (but not if it's the error type)
            if (parts.length > 4 && !isErrorType(parts[4])) {
                event.assignDrone(parts[4]);
            }

            return event;

        } catch (Exception e) {
            System.err.println("[FireEvent] Error parsing event: " + input);
            e.printStackTrace();
            return null; // Return null to signify an invalid event
        }
    }

    /**
     * Helper method to parse error types from the string
     */
    private void setErrorFromString(String errorString) {
        switch (errorString) {
            case "NOZZLE_JAM":
                this.error = ErrorType.NOZZLE_JAM;
                break;
            case "DOOR_STUCK":
                this.error = ErrorType.DOOR_STUCK;
                break;
            case "DRONE_STUCK":
                this.error = ErrorType.DRONE_STUCK;
                break;
            case "ARRIVAL_SENSOR_FAILED":
                this.error = ErrorType.ARRIVAL_SENSOR_FAILED;
                break;
            case "COMMUNICATION_FAILURE":
                this.error = ErrorType.COMMUNICATION_FAILURE;
                break;
            default:
                this.error = ErrorType.NONE;
                break;
        }
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

    // Helper method to check if a string is an error type
    private static boolean isErrorType(String str) {
        return str.equals("NOZZLE_JAM") ||
                str.equals("DOOR_STUCK") ||
                str.equals("DRONE_STUCK") ||
                str.equals("ARRIVAL_SENSOR_FAILED") ||
                str.equals("COMMUNICATION_FAILURE") ||
                str.equals("ERROR");
    }
}
