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

    //testing methods

    public String getTime(){

        return time;
    }

    public int getZoneID(){

        return zoneID;
    }

    public String getEventType(){

        return eventType;
    }

    public String getSeverity() {
        return severity;
    }
}