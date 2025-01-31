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

    /**
     * Returns a string representation of the fire event
     * @return A formatted string containing the event details.
     */
    @Override
    public String toString() {
        return "Time: " + time + ", Zone: " + zoneID + ", Event: " + eventType + ", Severity: " + severity;
    }
}