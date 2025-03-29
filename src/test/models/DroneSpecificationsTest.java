package models;

import org.junit.Test;
import static org.junit.Assert.*;

public class DroneSpecificationsTest {
    
    @Test
    public void testDefaultConstructor() {
        DroneSpecifications specs = new DroneSpecifications();
        
        assertEquals(56.0, specs.getMaxSpeed(), 0.001);        // 56 km/h
        assertEquals(100, specs.getTimeToOpenNozzle());        // 100 ms
        assertEquals(5.0, specs.getFlowRate(), 0.001);         // 5 L/s
        assertEquals(30.0, specs.getCarryCapacity(), 0.001);   // 30 L
        assertEquals(30, specs.getBatteryLife());              // 30 min
        assertEquals(4.8, specs.getAcceleration(), 0.001);     // 4.8 m/s²
        assertEquals(5.0, specs.getDeceleration(), 0.001);     // 5.0 m/s²
    }
    
    @Test
    public void testParameterizedConstructor() {
        DroneSpecifications specs = new DroneSpecifications(
            70.0,  // 70 km/h
            600,   // 600 ms
            4.0,   // 4 L/s
            40.0,  // 40 L
            25,    // 25 min
            4.0,   // 4 m/s²
            4.5    // 4.5 m/s²
        );
        
        assertEquals(70.0, specs.getMaxSpeed(), 0.001);        // 70 km/h
        assertEquals(600, specs.getTimeToOpenNozzle());        // 600 ms
        assertEquals(4.0, specs.getFlowRate(), 0.001);         // 4 L/s
        assertEquals(40.0, specs.getCarryCapacity(), 0.001);   // 40 L
        assertEquals(25, specs.getBatteryLife());              // 25 min
        assertEquals(4.0, specs.getAcceleration(), 0.001);     // 4 m/s²
        assertEquals(4.5, specs.getDeceleration(), 0.001);     // 4.5 m/s²
    }
    
    @Test
    public void testSetters() {
        DroneSpecifications specs = new DroneSpecifications();
        
        specs.setMaxSpeed(65.0);                // 65 km/h
        specs.setTimeToOpenNozzle(250);         // 250 ms
        specs.setFlowRate(6.5);                 // 6.5 L/s
        specs.setCarryCapacity(35.0);           // 35 L
        specs.setBatteryLife(35);               // 35 min
        specs.setAcceleration(5.2);             // 5.2 m/s²
        specs.setDeceleration(5.5);             // 5.5 m/s²
        
        assertEquals(65.0, specs.getMaxSpeed(), 0.001);        // 65 km/h
        assertEquals(250, specs.getTimeToOpenNozzle());        // 250 ms
        assertEquals(6.5, specs.getFlowRate(), 0.001);         // 6.5 L/s
        assertEquals(35.0, specs.getCarryCapacity(), 0.001);   // 35 L
        assertEquals(35, specs.getBatteryLife());              // 35 min
        assertEquals(5.2, specs.getAcceleration(), 0.001);     // 5.2 m/s²
        assertEquals(5.5, specs.getDeceleration(), 0.001);     // 5.5 m/s²
    }
    
    @Test
    public void testCalculateTravelTime() {
        DroneSpecifications specs = new DroneSpecifications();
        
        // Short distance test (100m)
        int shortDistance = 100;
        int shortTravelTime = specs.calculateTravelTime(shortDistance);
        assertTrue("Short travel time should be greater than 0", shortTravelTime > 0);
        
        // Medium distance test (500m)
        int mediumDistance = 500;
        int mediumTravelTime = specs.calculateTravelTime(mediumDistance);
        assertTrue("Medium travel time should be greater than short travel time", 
                  mediumTravelTime > shortTravelTime);
        
        // Long distance test (1000m = 1km)
        int longDistance = 1000;
        int longTravelTime = specs.calculateTravelTime(longDistance);
        assertTrue("Long travel time should be greater than medium travel time", 
                  longTravelTime > mediumTravelTime);
        
        // Test that travel time is proportional to distance for long distances
        double ratio1 = (double) longTravelTime / longDistance;
        double ratio2 = (double) mediumTravelTime / mediumDistance;
        assertTrue("Travel time should scale approximately linearly with distance for long distances",
                  Math.abs(ratio1 - ratio2) < 0.3 * ratio1); // Allow for some non-linearity due to acceleration/deceleration
    }
    
    @Test
    public void testCalculateFirefightingDuration() {
        DroneSpecifications specs = new DroneSpecifications();
        
        int lowDuration = specs.calculateFirefightingDuration("low");
        int moderateDuration = specs.calculateFirefightingDuration("moderate");
        int highDuration = specs.calculateFirefightingDuration("high");
        
        assertTrue("High severity duration should be greater than moderate", highDuration > moderateDuration);
        assertTrue("Moderate severity duration should be greater than low", moderateDuration > lowDuration);
        
        // Test that duration includes nozzle opening time
        assertTrue("Duration should include nozzle opening time", 
                  lowDuration > specs.getTimeToOpenNozzle());
        
        // Test with different flow rate
        specs.setFlowRate(10.0); // Double the flow rate
        int newLowDuration = specs.calculateFirefightingDuration("low");
        assertTrue("Higher flow rate should result in shorter duration", 
                  newLowDuration < lowDuration);
        
        // Test with different nozzle opening time
        specs.setFlowRate(5.0); // Reset flow rate
        specs.setTimeToOpenNozzle(1000); // Double the nozzle opening time
        int newerLowDuration = specs.calculateFirefightingDuration("low");
        assertTrue("Longer nozzle opening time should result in longer duration", 
                  newerLowDuration > lowDuration);
    }
}