package models;

/**
 * Represents a physical zone in the firefighting system
 */
public class Zone {
    private int id;
    private Location location;
    private boolean hasFire;
    private String severity;
    
    /**
     * Creates a new zone with the specified ID and location
     * 
     * @param id the zone ID
     * @param location the physical location of the zone
     */
    public Zone(int id, Location location) {
        this.id = id;
        this.location = location;
        this.hasFire = false;
        this.severity = "NONE";
    }
    
    /**
     * Gets the zone ID
     * 
     * @return the zone ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Gets the zone location
     * 
     * @return the zone location
     */
    public Location getLocation() {
        return location;
    }
    
    /**
     * Checks if the zone currently has a fire
     * 
     * @return true if there is a fire, false otherwise
     */
    public boolean hasFire() {
        return hasFire;
    }
    
    /**
     * Sets whether the zone has a fire
     * 
     * @param hasFire true if there is a fire, false otherwise
     */
    public void setHasFire(boolean hasFire) {
        this.hasFire = hasFire;
    }
    
    /**
     * Gets the severity of the fire in this zone
     * 
     * @return the severity (or "NONE" if no fire)
     */
    public String getSeverity() {
        return severity;
    }
    
    /**
     * Sets the severity of the fire in this zone
     * 
     * @param severity the severity level
     */
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    @Override
    public String toString() {
        return "Zone " + id + ": " + location + 
               (hasFire ? ", FIRE(" + severity + ")" : ", NO FIRE");
    }
}