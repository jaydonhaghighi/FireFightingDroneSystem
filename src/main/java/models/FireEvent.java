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
    /**The ID of the drone assigned to handle this event*/
    String assignedDroneId;

    public ErrorType error;

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
        this.assignedDroneId = droneId;
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
        int currentPosition = 4;
        if (parts.length > currentPosition && !isErrorType(parts[currentPosition])) {
            event.assignDrone(parts[currentPosition]);
            currentPosition++;
        }

        // Check if an error type is included
        if (parts.length > currentPosition) {
            // Parse error type
            if (parts[currentPosition].contains("NOZZLE_JAM")) {
                event.error = ErrorType.NOZZLE_JAM;
            } else if (parts[currentPosition].contains("DOOR_STUCK")) {
                event.error = ErrorType.DOOR_STUCK;
            } else if (parts[currentPosition].contains("DRONE_STUCK")) {
                event.error = ErrorType.DRONE_STUCK;
            } else if (parts[currentPosition].contains("ARRIVAL_SENSOR")) {
                event.error = ErrorType.ARRIVAL_SENSOR_FAILED;
            }
        }


        
        return event;
    }


    /**
     * Returns a string representation of the fire event
     * @return A formatted string containing the event details.
     */
    @Override
    public String toString() {
        if (assignedDroneId != null) {
            return time + " " + zoneID + " " + eventType + " " + severity + " " + assignedDroneId + " " + error;
        } else {
            return time + " " + zoneID + " " + eventType + " " + severity + " " + error;
        }
    }

    // Helper method to check if a string is an error type
    private static boolean isErrorType(String str) {
        return str.contains("NOZZLE_JAM") ||
                str.contains("DOOR_STUCK") ||
                str.contains("DRONE_STUCK") ||
                str.contains("ARRIVAL_SENSOR");
    }
}
