package models;

/**
 * Represents a physical zone in the firefighting system
 * Zones are rectangular areas defined by their top-left and bottom-right coordinates in meters
 */
public class Zone {
    private int id;
    private Location topLeft;      // Top-left corner of the zone area
    private Location bottomRight;  // Bottom-right corner of the zone area
    private Location center;       // Center point of the zone (for targeting)
    private boolean hasFire;
    private String severity;
    
    /**
     * Creates a new zone with the specified ID and single location
     * (this constructor is maintained for backward compatibility)
     * 
     * @param id the zone ID
     * @param location the physical location of the zone in meters (will be used as center)
     */
    public Zone(int id, Location location) {
        this.id = id;
        this.center = location;
        // Default boundaries around the center point (175m x 150m rectangular zone) - halved size
        this.topLeft = new Location(location.getX() - 175, location.getY() - 150);
        this.bottomRight = new Location(location.getX() + 175, location.getY() + 150);
        this.hasFire = false;
        this.severity = "NONE";
    }
    
    /**
     * Creates a new zone with the specified ID and boundaries in meters
     * 
     * @param id the zone ID
     * @param x1 top-left X coordinate in meters
     * @param y1 top-left Y coordinate in meters
     * @param x2 bottom-right X coordinate in meters
     * @param y2 bottom-right Y coordinate in meters
     */
    public Zone(int id, int x1, int y1, int x2, int y2) {
        this.id = id;
        this.topLeft = new Location(x1, y1);
        this.bottomRight = new Location(x2, y2);
        
        // Calculate center point
        int centerX = (x1 + x2) / 2;
        int centerY = (y1 + y2) / 2;
        this.center = new Location(centerX, centerY);
        
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
     * Gets the zone center location
     * 
     * @return the center location
     */
    public Location getLocation() {
        return center;
    }
    
    /**
     * Gets the top-left corner of the zone
     * 
     * @return the top-left location
     */
    public Location getTopLeft() {
        return topLeft;
    }
    
    /**
     * Gets the bottom-right corner of the zone
     * 
     * @return the bottom-right location
     */
    public Location getBottomRight() {
        return bottomRight;
    }
    
    /**
     * Gets the width of the zone in meters
     * 
     * @return zone width in meters
     */
    public int getWidth() {
        return bottomRight.getX() - topLeft.getX();
    }
    
    /**
     * Gets the height of the zone in meters
     * 
     * @return zone height in meters
     */
    public int getHeight() {
        return bottomRight.getY() - topLeft.getY();
    }
    
    /**
     * Checks if a location is within this zone
     * 
     * @param location the location to check
     * @return true if the location is within the zone boundaries
     */
    public boolean contains(Location location) {
        int x = location.getX();
        int y = location.getY();
        return x >= topLeft.getX() && x <= bottomRight.getX() && 
               y >= topLeft.getY() && y <= bottomRight.getY();
    }
    
    /**
     * Checks if this zone overlaps with another zone
     * 
     * @param other the other zone to check
     * @return true if the zones overlap
     */
    public boolean overlaps(Zone other) {
        return !(other.topLeft.getX() > bottomRight.getX() ||
                 other.bottomRight.getX() < topLeft.getX() ||
                 other.topLeft.getY() > bottomRight.getY() ||
                 other.bottomRight.getY() < topLeft.getY());
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
        return "Zone " + id + ": " + topLeft + " to " + bottomRight + " (center: " + center + ")" +
               (hasFire ? ", FIRE(" + severity + ")" : ", NO FIRE");
    }
}