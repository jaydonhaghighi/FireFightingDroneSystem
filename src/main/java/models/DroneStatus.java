package models;

/**
 * Represents the current status of a drone in the system
 */
public class DroneStatus {
    private String droneId;
    private Location currentLocation;
    private Location targetLocation;
    private String state;
    private FireEvent currentTask;
    private int zonesServiced;
    private long lastUpdateTime;
    private FireEvent.ErrorType errorType = FireEvent.ErrorType.NONE;
    
    /**
     * Creates a new DroneStatus with the specified ID and initial location
     * 
     * @param droneId the unique identifier for the drone
     * @param initialLocation the initial location of the drone
     */
    public DroneStatus(String droneId, Location initialLocation) {
        this.droneId = droneId;
        this.currentLocation = initialLocation;
        this.targetLocation = initialLocation; // Initially targeting current location
        this.state = "IDLE";
        this.zonesServiced = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Gets the drone's ID
     * 
     * @return the drone ID
     */
    public String getDroneId() {
        return droneId;
    }
    
    /**
     * Gets the drone's current location
     * 
     * @return the current location
     */
    public Location getCurrentLocation() {
        return currentLocation;
    }
    
    /**
     * Sets the drone's current location
     * 
     * @param currentLocation the new current location
     */
    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Gets the drone's target location
     * 
     * @return the target location
     */
    public Location getTargetLocation() {
        return targetLocation;
    }
    
    /**
     * Sets the drone's target location
     * 
     * @param targetLocation the new target location
     */
    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }
    
    /**
     * Gets the drone's current state
     * 
     * @return the current state
     */
    public String getState() {
        return state;
    }
    
    /**
     * Sets the drone's current state
     * 
     * @param state the new state
     */
    public void setState(String state) {
        this.state = state;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    
    /**
     * Gets the drone's current task
     * 
     * @return the current task or null if no task
     */
    public FireEvent getCurrentTask() {
        return currentTask;
    }
    
    /**
     * Sets the drone's current task
     * 
     * @param currentTask the new current task
     */
    public void setCurrentTask(FireEvent currentTask) {
        this.currentTask = currentTask;
    }
    
    /**
     * Gets the number of zones this drone has serviced
     * 
     * @return number of zones serviced
     */
    public int getZonesServiced() {
        return zonesServiced;
    }
    
    /**
     * Increment the number of zones serviced by this drone
     */
    public void incrementZonesServiced() {
        this.zonesServiced++;
    }
    
    /**
     * Gets the time of the last status update
     * 
     * @return timestamp of the last update
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * Checks if the drone is currently available to take on a new task
     * 
     * @return true if available, false otherwise
     */
    public boolean isAvailable() {
        // A drone is available if it's in the Idle state and doesn't have a hard fault
        boolean isHardFault = (errorType == FireEvent.ErrorType.NOZZLE_JAM);
        // Check case-insensitive since state names might come in different cases
        return (("IDLE".equalsIgnoreCase(state) || "Idle".equalsIgnoreCase(state)) && !isHardFault);
    }
    
    /**
     * Calculates the distance from the drone's current location to the specified location
     * 
     * @param location the target location
     * @return the distance to the target
     */
    public int distanceTo(Location location) {
        return currentLocation.distanceTo(location);
    }

    /**
     * Gets the current error type
     * @return the error type
     */
    public FireEvent.ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Sets the error type
     * @param errorType the error type to set
     */
    public void setErrorType(FireEvent.ErrorType errorType) {
        this.errorType = errorType;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Checks if the drone has a hard fault
     *
     * @return true if the drone has a hard fault, false otherwise
     */
    public boolean hasHardFault() {
        return (errorType == FireEvent.ErrorType.NOZZLE_JAM);
    }
    
    @Override
    public String toString() {
        String errorInfo = (errorType != FireEvent.ErrorType.NONE) ?
                ", error: " + errorType : "";
        return "Drone " + droneId + ": [" + state + "]" + errorInfo + " at " + currentLocation +
               (currentTask != null ? ", handling: " + currentTask.getZoneID() : "") +
               ", zones serviced: " + zonesServiced;
    }

}
