package models;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;


/**
 * Test class for the DroneStatus model
 */
public class DroneStatusTest {
    private DroneStatus drone;
    private Location baseLocation;
    private Location targetLocation;
    private FireEvent fireEvent;

    @Before
    public void setUp() {
        // Initialize test objects
        baseLocation = new Location(0, 0);
        targetLocation = new Location(10, 20);
        drone = new DroneStatus("drone1", baseLocation);
        fireEvent = new FireEvent("10:30", 5, "FIRE", "high", "NONE");
    }

    @Test
    public void testConstructorAndInitialState() {
        // Test constructor values and initial state
        assertEquals("Drone ID should match constructor value", "drone1", drone.getDroneId());
        assertEquals("Current location should match initial location", baseLocation, drone.getCurrentLocation());
        assertEquals("Target location should initially match current location", baseLocation, drone.getTargetLocation());
        assertEquals("Initial state should be 'IDLE'", "IDLE", drone.getState());
        assertEquals("Initial zones serviced should be 0", 0, drone.getZonesServiced());
        assertNull("Initial current task should be null", drone.getCurrentTask());
        assertTrue("Drone should initially be available", drone.isAvailable());
        assertTrue("Last update time should be initialized", drone.getLastUpdateTime() > 0);
    }

    @Test
    public void testLocationSettersAndGetters() {
        // Test current location setter and getter
        drone.setCurrentLocation(targetLocation);
        assertEquals("Current location should be updated", targetLocation, drone.getCurrentLocation());

        // Test that setCurrentLocation updates lastUpdateTime
        long previousUpdateTime = drone.getLastUpdateTime();

        // Small delay to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        drone.setCurrentLocation(baseLocation);
        assertTrue("Last update time should increase after setCurrentLocation",
                drone.getLastUpdateTime() > previousUpdateTime);

        // Test target location setter and getter
        drone.setTargetLocation(targetLocation);
        assertEquals("Target location should be updated", targetLocation, drone.getTargetLocation());
    }

    @Test
    public void testStateSetterAndGetter() {
        // Test state setter and getter
        drone.setState("EN ROUTE");
        assertEquals("State should be updated", "EN ROUTE", drone.getState());

        // Test that setState updates lastUpdateTime
        long previousUpdateTime = drone.getLastUpdateTime();

        // Small delay to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        drone.setState("DROPPING AGENT");
        assertTrue("Last update time should increase after setState",
                drone.getLastUpdateTime() > previousUpdateTime);
    }

    @Test
    public void testTaskSetterAndGetter() {
        // Test current task setter and getter
        drone.setCurrentTask(fireEvent);
        assertEquals("Current task should be updated", fireEvent, drone.getCurrentTask());
    }

    @Test
    public void testZonesServiced() {
        // Test initial zones serviced
        assertEquals("Initial zones serviced should be 0", 0, drone.getZonesServiced());

        // Test incrementing zones serviced
        drone.incrementZonesServiced();
        assertEquals("Zones serviced should increment by 1", 1, drone.getZonesServiced());

        // Test multiple increments
        drone.incrementZonesServiced();
        drone.incrementZonesServiced();
        assertEquals("Zones serviced should increment correctly", 3, drone.getZonesServiced());
    }

    @Test
    public void testIsAvailable() {
        // Test availability with different state values (case insensitive)
        drone.setState("IDLE");
        assertTrue("Drone should be available when state is 'IDLE' (uppercase)", drone.isAvailable());

        drone.setState("Idle");
        assertTrue("Drone should be available when state is 'Idle' (mixed case)", drone.isAvailable());

        drone.setState("idle");
        assertTrue("Drone should be available when state is 'idle' (lowercase)", drone.isAvailable());

        // Test unavailable states
        drone.setState("EN ROUTE");
        assertFalse("Drone should not be available when state is 'EN ROUTE'", drone.isAvailable());

        drone.setState("DROPPING AGENT");
        assertFalse("Drone should not be available when state is 'DROPPING AGENT'", drone.isAvailable());

        drone.setState("FAULT");
        assertFalse("Drone should not be available when state is 'FAULT'", drone.isAvailable());
    }

    @Test
    public void testDistanceTo() {
        // Test distance calculation to target
        Location target = new Location(30, 40);
        int expectedDistance = Math.abs(0 - 30) + Math.abs(0 - 40); // Manhattan distance from (0,0) to (30,40)
        assertEquals("Distance calculation should match expected", expectedDistance, drone.distanceTo(target));

        // Test with updated current location
        drone.setCurrentLocation(new Location(10, 10));
        int newExpectedDistance = Math.abs(10 - 30) + Math.abs(10 - 40); // Manhattan distance from (10,10) to (30,40)
        assertEquals("Distance calculation should update with current location", newExpectedDistance, drone.distanceTo(target));
    }

    @Test
    public void testToString() {
        // Test toString with basic state (no task)
        String expected = "Drone drone1: [IDLE] at (0,0), zones serviced: 0";
        assertEquals("String representation should match format", expected, drone.toString());

        // Test toString with task
        drone.setCurrentTask(fireEvent);
        expected = "Drone drone1: [IDLE] at (0,0), handling: 5, zones serviced: 0";
        assertEquals("String representation should include task info", expected, drone.toString());

        // Test toString with updated state and location
        drone.setState("EN ROUTE");
        drone.setCurrentLocation(targetLocation);
        drone.incrementZonesServiced();
        expected = "Drone drone1: [EN ROUTE] at (10,20), handling: 5, zones serviced: 1";
        assertEquals("String representation should update with state changes", expected, drone.toString());
    }
}
