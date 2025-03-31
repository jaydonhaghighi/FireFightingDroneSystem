package controllers;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Test class for the DroneManager controller
 */
public class DroneManagerTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private DroneManager droneManager;
    private Location baseLocation;
    private File zonesFile;

    @Before
    public void setUp() throws IOException {
        // Create a temporary zones file for testing
        zonesFile = tempFolder.newFile("zones.txt");

        // Write test zones data to the file
        try (FileWriter writer = new FileWriter(zonesFile)) {
            writer.write("# Test zones data\n");
            writer.write("1 10 10 20 20\n");  // Zone 1: (10,10) to (20,20)
            writer.write("2 30 30 40 40\n");  // Zone 2: (30,30) to (40,40)
            writer.write("3 50 50 60 60\n");  // Zone 3: (50,50) to (60,60)
        }

        // Initialize test objects
        baseLocation = new Location(0, 0);

        // We need to create a DroneManager that uses our test zones file
        // Since we can't modify the constructor to take a file path, we'll test with default zones
        droneManager = new DroneManager(baseLocation);

        // The actual zones from the file won't be loaded since the path is hardcoded in DroneManager
        // But we can still test the rest of the functionality
    }

    @Test
    public void testRegisterDrone() {
        // Test registering a drone
        DroneStatus drone = droneManager.registerDrone("drone1");

        assertNotNull("Registered drone should not be null", drone);
        assertEquals("Drone ID should match", "drone1", drone.getDroneId());
        assertEquals("Initial location should be base location", baseLocation, drone.getCurrentLocation());
        assertEquals("Initial state should be IDLE", "IDLE", drone.getState());
        assertEquals("Initial zones serviced should be 0", 0, drone.getZonesServiced());
    }

    @Test
    public void testGetDroneStatus() {
        // Register a drone first
        DroneStatus registeredDrone = droneManager.registerDrone("drone2");

        // Test getting status of registered drone
        DroneStatus retrievedDrone = droneManager.getDroneStatus("drone2");
        assertNotNull("Retrieved drone should not be null", retrievedDrone);
        assertEquals("Retrieved drone should match registered drone", registeredDrone, retrievedDrone);

        // Test getting status of non-existent drone
        DroneStatus nonExistentDrone = droneManager.getDroneStatus("nonexistent");
        assertNull("Status of non-existent drone should be null", nonExistentDrone);
    }

    @Test
    public void testGetAllDrones() {
        // Register multiple drones
        DroneStatus drone1 = droneManager.registerDrone("drone1");
        DroneStatus drone2 = droneManager.registerDrone("drone2");
        DroneStatus drone3 = droneManager.registerDrone("drone3");

        // Test getting all drones
        Collection<DroneStatus> allDrones = droneManager.getAllDrones();

        assertNotNull("Collection of drones should not be null", allDrones);
        assertEquals("Collection should contain all registered drones", 3, allDrones.size());
        assertTrue("Collection should contain first drone", allDrones.contains(drone1));
        assertTrue("Collection should contain second drone", allDrones.contains(drone2));
        assertTrue("Collection should contain third drone", allDrones.contains(drone3));
    }

    @Test
    public void testGetAllZones() {
        // Test getting all zones
        Map<Integer, Zone> allZones = droneManager.getAllZones();

        assertNotNull("Map of zones should not be null", allZones);
        assertFalse("Map of zones should not be empty", allZones.isEmpty());

        // We can't assert exact size because it depends on initialization (file or default)
        // But we can check some core functionality
    }

    @Test
    public void testGetZone() {
        // Test getting zones - first zone should always exist (either from file or default)
        Zone zone = droneManager.getZone(1);
        assertNotNull("Zone with ID 1 should exist", zone);
        assertEquals("Zone ID should match", 1, zone.getId());

        // Test getting non-existent zone
        Zone nonExistentZone = droneManager.getZone(999);
        assertNull("Non-existent zone should return null", nonExistentZone);
    }

    @Test
    public void testGetLocationForZone() {
        // Test getting location for existing zone
        Location zoneLocation = droneManager.getLocationForZone(1);
        assertNotNull("Location for existing zone should not be null", zoneLocation);

        // Test getting location for non-existent zone (should return base location)
        Location fallbackLocation = droneManager.getLocationForZone(999);
        assertEquals("Location for non-existent zone should be base location",
                baseLocation, fallbackLocation);
    }

    @Test
    public void testUpdateZoneFireStatus() {
        // Test updating fire status of a zone
        int zoneId = 1;
        Zone before = droneManager.getZone(zoneId);
        assertNotNull("Zone should exist before update", before);
        assertFalse("Zone should not have fire initially", before.hasFire());

        // Update fire status
        droneManager.updateZoneFireStatus(zoneId, true, "HIGH");

        // Verify update
        Zone after = droneManager.getZone(zoneId);
        assertTrue("Zone should have fire after update", after.hasFire());
        assertEquals("Zone should have correct severity", "HIGH", after.getSeverity());

        // Test updating to no fire
        droneManager.updateZoneFireStatus(zoneId, false, "NONE");

        // Verify update
        Zone afterClear = droneManager.getZone(zoneId);
        assertFalse("Zone should not have fire after clearing", afterClear.hasFire());
        assertEquals("Zone should have correct severity", "NONE", afterClear.getSeverity());
    }

    @Test
    public void testSelectBestDroneForEvent_AvailabilityBased() {
        // Register two drones
        DroneStatus availableDrone = droneManager.registerDrone("available");
        DroneStatus busyDrone = droneManager.registerDrone("busy");

        // Make one drone busy
        busyDrone.setState("EN ROUTE");

        // Create a fire event
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);

        // Test selection
        DroneStatus selected = droneManager.selectBestDroneForEvent(event);
        assertNotNull("A drone should be selected", selected);
        assertEquals("Available drone should be selected", availableDrone.getDroneId(), selected.getDroneId());
    }

    @Test
    public void testSelectBestDroneForEvent_WorkloadBased() {
        // Register two available drones with different workloads
        DroneStatus lowWorkloadDrone = droneManager.registerDrone("lowWorkload");
        DroneStatus highWorkloadDrone = droneManager.registerDrone("highWorkload");

        // Position them at same distance from target
        lowWorkloadDrone.setCurrentLocation(new Location(10, 0));
        highWorkloadDrone.setCurrentLocation(new Location(10, 0));

        // Set different workloads
        lowWorkloadDrone.setState("IDLE");
        highWorkloadDrone.setState("IDLE");
        for (int i = 0; i < 5; i++) {
            highWorkloadDrone.incrementZonesServiced(); // 5 previous missions
        }

        // Create a fire event for zone 1
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);

        // Test selection
        DroneStatus selected = droneManager.selectBestDroneForEvent(event);
        assertNotNull("A drone should be selected", selected);
        assertEquals("Drone with lower workload should be selected when distances are equal",
                lowWorkloadDrone.getDroneId(), selected.getDroneId());
    }

    @Test
    public void testSelectBestDroneForEvent_ProximityBased() {
        // Register two available drones with same workload but at different distances
        DroneStatus closeDrone = droneManager.registerDrone("close");
        DroneStatus farDrone = droneManager.registerDrone("far");

        // Position them at different distances from zone 1
        // Assuming zone 1 center is around (15,15) based on zone definition in setUp
        closeDrone.setCurrentLocation(new Location(10, 10)); // Closer to zone 1
        farDrone.setCurrentLocation(new Location(50, 50));   // Further from zone 1

        // Make both idle with same workload
        closeDrone.setState("IDLE");
        farDrone.setState("IDLE");

        // Create a fire event for zone 1
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);

        // Test selection
        DroneStatus selected = droneManager.selectBestDroneForEvent(event);
        assertNotNull("A drone should be selected", selected);
        assertEquals("Closer drone should be selected when workloads are equal",
                closeDrone.getDroneId(), selected.getDroneId());
    }

    @Test
    public void testSelectBestDroneForEvent_NoDronesAvailable() {
        // Register a drone but make it busy
        DroneStatus busyDrone = droneManager.registerDrone("busy");
        busyDrone.setState("EN ROUTE");

        // Create a fire event
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);

        // Test selection with no available drones
        DroneStatus selected = droneManager.selectBestDroneForEvent(event);
        assertNull("No drone should be selected when none are available", selected);
    }

    @Test
    public void testUpdateDroneStatus() {
        // Register a drone
        String droneId = "testDrone";
        DroneStatus drone = droneManager.registerDrone(droneId);

        // Create a fire event
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);
        Location newLocation = new Location(15, 15);

        // Update drone status
        droneManager.updateDroneStatus(droneId, "EN ROUTE", newLocation, event);

        // Verify update
        DroneStatus updated = droneManager.getDroneStatus(droneId);
        assertEquals("State should be updated", "EN ROUTE", updated.getState());
        assertEquals("Location should be updated", newLocation, updated.getCurrentLocation());
        assertEquals("Task should be updated", event, updated.getCurrentTask());

        // Test that target location is updated based on event
        assertNotEquals("Target location should be updated to zone location",
                baseLocation, updated.getTargetLocation());

        // Test transition to IDLE (should increment zones serviced)
        int zonesServiced = updated.getZonesServiced();
        droneManager.updateDroneStatus(droneId, "IDLE", baseLocation, null);

        // Verify update after completion
        DroneStatus completed = droneManager.getDroneStatus(droneId);
        assertEquals("State should be updated to IDLE", "IDLE", completed.getState());
        assertEquals("Zones serviced should be incremented", zonesServiced + 1, completed.getZonesServiced());
        assertNull("Task should be null", completed.getCurrentTask());
        assertEquals("Target location should be reset to base", baseLocation, completed.getTargetLocation());
    }

    @Test
    public void testUpdateNonExistentDrone() {
        // Test updating a drone that doesn't exist (should not throw exception)
        String nonExistentId = "nonexistent";
        Location location = new Location(10, 10);
        FireEvent event = new FireEvent("10:30", 1, "FIRE", "HIGH", false);

        // This should not throw an exception
        droneManager.updateDroneStatus(nonExistentId, "EN ROUTE", location, event);

        // Drone should still not exist
        assertNull("Non-existent drone should still not exist after update attempt",
                droneManager.getDroneStatus(nonExistentId));
    }
}