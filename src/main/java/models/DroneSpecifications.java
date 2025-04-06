package models;

/**
 * Represents the technical specifications of a drone in the system
 */
public class DroneSpecifications {
    private double maxSpeed; // Maximum speed in km/h
    private int timeToOpenNozzle; // Time to open nozzle in milliseconds
    private double flowRate; // Flow rate in litres/s
    private double carryCapacity; // Carry capacity in litres
    private double currentCapacity; // Current amount of agent in litres
    private int batteryLife; // Battery life in minutes
    private double acceleration; // Acceleration in m/s²
    private double deceleration; // Deceleration in m/s²
    
    /**
     * Creates a new DroneSpecifications with default values
     */
    public DroneSpecifications() {
        this.maxSpeed = 1000.0;        // 56 km/h - standard drone speed
        this.timeToOpenNozzle = 100;  // 100 ms to open nozzle
        this.flowRate = 500.0;          // 500 litres/s flow rate
        this.carryCapacity = 10.0;    // 10 litres total capacity
        this.currentCapacity = 10.0;  // Start with full capacity
        this.batteryLife = 50;        // 50 minutes flight time
        this.acceleration = 4.8;      // 4.8 m/s²
        this.deceleration = 4.8;      // 4.8 m/s²
    }
    
    /**
     * Creates a new DroneSpecifications with the specified values
     * 
     * @param maxSpeed Maximum speed in km/h
     * @param timeToOpenNozzle Time to open nozzle in milliseconds
     * @param flowRate Flow rate in litres/s
     * @param carryCapacity Carry capacity in litres
     * @param batteryLife Battery life in minutes
     * @param acceleration Acceleration in m/s²
     * @param deceleration Deceleration in m/s²
     */
    public DroneSpecifications(double maxSpeed, int timeToOpenNozzle, double flowRate, 
                              double carryCapacity, int batteryLife, double acceleration, 
                              double deceleration) {
        this.maxSpeed = maxSpeed;
        this.timeToOpenNozzle = timeToOpenNozzle;
        this.flowRate = flowRate;
        this.carryCapacity = carryCapacity;
        this.currentCapacity = carryCapacity; // Start with full capacity
        this.batteryLife = batteryLife;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
    }
    
    /**
     * Gets the maximum speed of the drone
     * 
     * @return maximum speed in km/h
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }
    
    /**
     * Sets the maximum speed of the drone
     * 
     * @param maxSpeed the new maximum speed in km/h
     */
    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
    
    /**
     * Gets the time it takes to open the nozzle
     * 
     * @return time to open nozzle in milliseconds
     */
    public int getTimeToOpenNozzle() {
        return timeToOpenNozzle;
    }
    
    /**
     * Sets the time it takes to open the nozzle
     * 
     * @param timeToOpenNozzle the new time to open nozzle
     */
    public void setTimeToOpenNozzle(int timeToOpenNozzle) {
        this.timeToOpenNozzle = timeToOpenNozzle;
    }
    
    /**
     * Gets the flow rate of the drone
     * 
     * @return flow rate in litres/s
     */
    public double getFlowRate() {
        return flowRate;
    }
    
    /**
     * Sets the flow rate of the drone
     * 
     * @param flowRate the new flow rate in litres/s
     */
    public void setFlowRate(double flowRate) {
        this.flowRate = flowRate;
    }
    
    /**
     * Gets the carry capacity of the drone
     * 
     * @return carry capacity in litres
     */
    public double getCarryCapacity() {
        return carryCapacity;
    }
    
    /**
     * Sets the carry capacity of the drone
     * 
     * @param carryCapacity the new carry capacity in litres
     */
    public void setCarryCapacity(double carryCapacity) {
        this.carryCapacity = carryCapacity;
    }
    
    /**
     * Gets the current capacity of the drone
     * 
     * @return current capacity in litres
     */
    public double getCurrentCapacity() {
        return currentCapacity;
    }
    
    /**
     * Sets the current capacity of the drone
     * 
     * @param currentCapacity the new current capacity in litres
     */
    public void setCurrentCapacity(double currentCapacity) {
        this.currentCapacity = currentCapacity;
    }
    
    /**
     * Refills the drone to full capacity
     */
    public void refill() {
        this.currentCapacity = this.carryCapacity;
    }
    
    /**
     * Empties the drone's capacity (sets to 0)
     */
    public void empty() {
        this.currentCapacity = 0.0;
    }
    
    /**
     * Gets the battery life of the drone
     * 
     * @return battery life in minutes
     */
    public int getBatteryLife() {
        return batteryLife;
    }
    
    /**
     * Sets the battery life of the drone
     * 
     * @param batteryLife the new battery life
     */
    public void setBatteryLife(int batteryLife) {
        this.batteryLife = batteryLife;
    }
    
    /**
     * Gets the acceleration of the drone
     * 
     * @return acceleration in m/s²
     */
    public double getAcceleration() {
        return acceleration;
    }
    
    /**
     * Sets the acceleration of the drone
     * 
     * @param acceleration the new acceleration in m/s²
     */
    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }
    
    /**
     * Gets the deceleration of the drone
     * 
     * @return deceleration in m/s²
     */
    public double getDeceleration() {
        return deceleration;
    }
    
    /**
     * Sets the deceleration of the drone
     * 
     * @param deceleration the new deceleration in m/s²
     */
    public void setDeceleration(double deceleration) {
        this.deceleration = deceleration;
    }
    
    /**
     * Calculate the time to travel a given distance based on drone specifications
     * 
     * @param distance the distance to travel in meters
     * @return the time to travel the distance in milliseconds
     */
    public int calculateTravelTime(int distance) {
        // Convert km/h to m/s for calculations
        double maxSpeedInMPS = maxSpeed / 3.6; // 1 km/h = 1/3.6 m/s
        
        // Calculate time to accelerate to max speed
        double timeToMaxSpeed = maxSpeedInMPS / acceleration;
        // Calculate distance covered during acceleration
        double distanceInAcceleration = 0.5 * acceleration * Math.pow(timeToMaxSpeed, 2);
        
        // Calculate time to decelerate from max speed to 0
        double timeToDecelerate = maxSpeedInMPS / deceleration;
        // Calculate distance covered during deceleration
        double distanceInDeceleration = 0.5 * deceleration * Math.pow(timeToDecelerate, 2);
        
        // If distance is less than acceleration + deceleration distance, special case
        if (distance < distanceInAcceleration + distanceInDeceleration) {
            // Simplified calculation for short distances
            double avgSpeed = maxSpeedInMPS / 2;
            return (int) ((distance / avgSpeed) * 1000);
        }
        
        // Calculate distance at constant max speed
        double distanceAtMaxSpeed = distance - distanceInAcceleration - distanceInDeceleration;
        // Calculate time at constant max speed
        double timeAtMaxSpeed = distanceAtMaxSpeed / maxSpeedInMPS;
        
        // Calculate total travel time in seconds
        double totalTimeSeconds = timeToMaxSpeed + timeAtMaxSpeed + timeToDecelerate;
        
        // Convert to milliseconds and return
        return (int) (totalTimeSeconds * 1000);
    }
    
    /**
     * Calculate the time required to extinguish a fire based on its severity
     * The method also updates the current capacity based on the amount used.
     * 
     * @param severity the fire severity (low, moderate, high)
     * @return the time required to extinguish the fire in milliseconds
     */
    public int calculateFirefightingDuration(String severity) {
        // Base fire water requirement in litres by severity
        double litresRequired;
        switch (severity.toLowerCase()) {
            case "high":
                litresRequired = 30.0;
                break;
            case "moderate":
                litresRequired = 20.0;
                break;
            case "low":
                litresRequired = 10.0;
                break;
            default:
                litresRequired = 10.0;  // Default
        }
        
        // Check if we have enough capacity
        if (litresRequired > currentCapacity) {
            // We can only use what we have - will not fully extinguish
            litresRequired = currentCapacity;
        }
        
        // Calculate time based on flow rate (litres/s) and add nozzle opening time
        int firefightingTime = (int) ((litresRequired / flowRate) * 1000); // Time in milliseconds
        int totalTime = firefightingTime + timeToOpenNozzle;
        
        // Note: In DroneSubsystem.dropAgent() we call empty() which sets capacity to 0
        // This calculation is just for time estimation
        
        return totalTime;
    }
    
    @Override
    public String toString() {
        return "DroneSpecifications{" +
                "maxSpeed=" + maxSpeed + " km/h" +
                ", timeToOpenNozzle=" + timeToOpenNozzle + " ms" +
                ", flowRate=" + flowRate + " L/s" +
                ", carryCapacity=" + carryCapacity + " L" +
                ", currentCapacity=" + currentCapacity + " L" +
                ", batteryLife=" + batteryLife + " min" +
                ", acceleration=" + acceleration + " m/s²" +
                ", deceleration=" + deceleration + " m/s²" +
                '}';
    }
}