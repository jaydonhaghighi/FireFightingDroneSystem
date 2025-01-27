package models;

public class FireEvent {
    String time;
    int zoneID;
    String eventType;
    String severity;

    public FireEvent(String time, int zoneID, String eventType, String severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
    }

    @Override
    public String toString() {
        return "Time: " + time + ", Zone: " + zoneID + ", Event: " + eventType + ", Severity: " + severity;
    }
}