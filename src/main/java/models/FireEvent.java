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

    public static FireEvent createFireEventFromString(String input) {
        // Split the string into its components based on whitespace
        String[] parts = input.split(" ");
        String time = parts[0];
        int zoneID = Integer.parseInt(parts[1]);
        String eventType = parts[2];
        String severity = parts[3];

        // Create and return the new FireEvent object
        return new FireEvent(time, zoneID, eventType, severity);
    }

    /**
     * Returns a string representation of the fire event
     * @return A formatted string containing the event details.
     */
    @Override
    public String toString() {
        return time + " " + zoneID + " " + eventType + " " + severity;
    }
}
