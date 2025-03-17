package models;

/**
 * Represents a physical location in a 2D space
 */
public class Location {
    private int x;
    private int y;
    
    /**
     * Create a new location with the specified coordinates
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Gets the x coordinate
     * 
     * @return the x coordinate
     */
    public int getX() {
        return x;
    }
    
    /**
     * Gets the y coordinate
     * 
     * @return the y coordinate
     */
    public int getY() {
        return y;
    }
    
    /**
     * Calculates Manhattan distance between this location and another location
     * 
     * @param other the other location
     * @return the Manhattan distance
     */
    public int distanceTo(Location other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }
    
    /**
     * Checks if this location is on the path between two other locations
     * 
     * @param start the starting location
     * @param end the ending location
     * @return true if this location is on the path, false otherwise
     */
    public boolean isOnPath(Location start, Location end) {
        int distanceStartToThis = start.distanceTo(this);
        int distanceThisToEnd = this.distanceTo(end);
        int distanceStartToEnd = start.distanceTo(end);
        return distanceStartToThis + distanceThisToEnd == distanceStartToEnd;
    }
    
    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Location location = (Location) obj;
        return x == location.x && y == location.y;
    }
    
    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }
}
