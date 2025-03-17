package models;

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

    /**
     * Constructors a FireEvent with the specified parameters below
     * @param time The time at which the fire event occurred
     * @param zoneID The ID of the zone the fire occurred
     * @param eventType The type of fire event
     * @param severity The severity level of the fire event(e.g. High, Medium, Low
     */
    public FireEvent(String time, int zoneID, String eventType, String severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        this.assignedDroneId = null;
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
        FireEvent event = new FireEvent(time, zoneID, eventType, severity);
        
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
        if (assignedDroneId != null) {
            return time + " " + zoneID + " " + eventType + " " + severity + " " + assignedDroneId;
        } else {
            return time + " " + zoneID + " " + eventType + " " + severity;
        }
    }
}
