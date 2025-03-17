package controllers;

import static org.junit.Assert.*;

import models.FireEvent;
import models.Location;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Tests for the DroneSubsystem class focusing on state machine behavior
 *
 * This test suite focuses on the state machine implementation and event handling
 * without mocking the network components.
 */
public class DroneSubsystemTest {

    private DroneSubsystem droneSubsystem;
    private final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @Before
    public void setUp() throws Exception {
        // Redirect System.out to capture console output for verification
        System.setOut(new PrintStream(outputCapture));

        // Create a special test instance with network disabled
        droneSubsystem = createTestDroneSubsystem();
    }

    @After
    public void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }

    /**
     * Creates a test instance with minimal networking dependency
     * Using a numeric drone ID to avoid NumberFormatException
     */
    private DroneSubsystem createTestDroneSubsystem() throws Exception {
        try {
            // Use "drone1" which has a numeric suffix
            InetAddress localHost = InetAddress.getLocalHost();
            return new DroneSubsystem(localHost, "drone1", new Location(5, 5));
        } catch (UnknownHostException e) {
            System.setOut(originalOut);
            System.out.println("Warning: Unable to get localhost, some network calls may fail in tests");
            return new DroneSubsystem(InetAddress.getByName("127.0.0.1"), "drone1", new Location(5, 5));
        }
    }

    /**
     * Subclass of DroneSubsystem for testing
     * Overrides network methods to avoid actual socket operations
     */
    private static class TestDroneSubsystem extends DroneSubsystem {
        public TestDroneSubsystem(InetAddress serverIP, String droneId, Location baseLocation) {
            super(serverIP, droneId, baseLocation);
        }

        @Override
        public void send(String message, int port) {
            // No-op to avoid actual network operations
            System.out.println("[TEST] Would send: " + message + " to port " + port);
        }

        @Override
        public FireEvent receive() {
            // Return a dummy event instead of waiting for network
            return new FireEvent("12:30", 1, "TEST", "medium");
        }

        @Override
        public void sendStatusUpdate() {
            // No-op to avoid actual network operations
            System.out.println("[TEST] Would send status update");
        }
    }

    @Test
    public void testInitialState() {
        // Test that the drone starts in IDLE state
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertEquals("drone1", droneSubsystem.getDroneId());
        assertEquals(new Location(5, 5), droneSubsystem.getCurrentLocation());
        assertEquals(new Location(5, 5), droneSubsystem.getTargetLocation());
        assertEquals(new Location(5, 5), droneSubsystem.getBaseLocation());
    }

    @Test
    public void testHandleFireEvent() {
        // Create a fire event
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");

        // Handle the fire event - should transition to EN ROUTE
        droneSubsystem.handleFireEvent(event);

        // Verify state change
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());

        // Verify console output contains expected messages
        String output = outputCapture.toString();
        assertTrue("Output should mention handling fire event",
                output.contains("preparing to handle new fire event"));
        assertTrue("Output should show EN ROUTE state",
                output.contains("EN ROUTE"));
    }

    @Test
    public void testDropAgent() {
        // Start in IDLE state
        assertEquals("Idle", droneSubsystem.getCurrentStateName());

        // Try to drop agent while IDLE - should stay IDLE and print message
        droneSubsystem.dropAgent();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention drone is idle",
                outputCapture.toString().contains("drone is idle, nothing to drop"));

        // Reset output capture
        outputCapture.reset();

        // Change to EN ROUTE and try to drop agent - should transition to dropping agent
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");
        droneSubsystem.handleFireEvent(event);
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());

        droneSubsystem.dropAgent();
        assertEquals("droppingAgent", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention cannot drop agent yet",
                outputCapture.toString().contains("drone is en route, cannot drop agent yet"));
    }

    @Test
    public void testReturningBack() {
        // Start in IDLE state
        assertEquals("Idle", droneSubsystem.getCurrentStateName());

        // Try to return while IDLE - should stay IDLE
        droneSubsystem.returningBack();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention drone is at base",
                outputCapture.toString().contains("drone is idle and stationed at base"));

        // Reset output capture
        outputCapture.reset();

        // Change to dropping agent and try to return - should transition to ArrivedToBase
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");
        droneSubsystem.handleFireEvent(event); // IDLE -> EN ROUTE
        droneSubsystem.dropAgent(); // EN ROUTE -> DROPPING AGENT

        droneSubsystem.returningBack(); // DROPPING AGENT -> ARRIVED TO BASE
        assertEquals("ArrivedToBase", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention not yet returned",
                outputCapture.toString().contains("drone is dropping an agent and has not yet returned to its base"));
    }

    @Test
    public void testDroneFaulted() {
        // Start in IDLE state
        droneSubsystem.droneFaulted();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention not faulted",
                outputCapture.toString().contains("drone has not faulted"));

        // Reset output capture
        outputCapture.reset();

        // Change to ArrivedToBase and trigger fault - should transition to Fault
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");
        droneSubsystem.handleFireEvent(event); // IDLE -> EN ROUTE
        droneSubsystem.dropAgent(); // EN ROUTE -> DROPPING AGENT
        droneSubsystem.returningBack(); // DROPPING AGENT -> ARRIVED TO BASE

        droneSubsystem.droneFaulted(); // ARRIVED TO BASE -> FAULT
        assertEquals("Fault", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention not yet faulted",
                outputCapture.toString().contains("drone has arrived to base and not yet faulted"));
    }

    @Test
    public void testTaskCompleted() {
        // Start in IDLE state
        droneSubsystem.taskCompleted();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention no tasks completed",
                outputCapture.toString().contains("drone is idle, no tasks have been completed"));

        // Reset output capture
        outputCapture.reset();

        // Change to ArrivedToBase and complete task - should transition to IDLE
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");
        droneSubsystem.handleFireEvent(event); // IDLE -> EN ROUTE
        droneSubsystem.dropAgent(); // EN ROUTE -> DROPPING AGENT
        droneSubsystem.returningBack(); // DROPPING AGENT -> ARRIVED TO BASE

        droneSubsystem.taskCompleted(); // ARRIVED TO BASE -> IDLE
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention task completed",
                outputCapture.toString().contains("drone has arrived to base and completed its task"));
    }

    @Test
    public void testRecoveryFromFault() {
        // Create a sequence: IDLE -> EN ROUTE -> DROPPING -> ARRIVED -> FAULT
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");
        droneSubsystem.handleFireEvent(event);
        droneSubsystem.dropAgent();
        droneSubsystem.returningBack();
        droneSubsystem.droneFaulted();

        assertEquals("Fault", droneSubsystem.getCurrentStateName());

        // Reset output capture
        outputCapture.reset();

        // Complete task from FAULT state - should transition to IDLE
        droneSubsystem.taskCompleted();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention returning to idle",
                outputCapture.toString().contains("Drone has faulted and returned to base. Now setting to idle"));
    }

    @Test
    public void testScheduleFireEventWhenIdle() {
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");

        // Schedule event when IDLE - should handle immediately
        droneSubsystem.scheduleFireEvent(event);
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());
        assertTrue("Output should mention drone is idle and ready",
                outputCapture.toString().contains("Drone is idle and ready for new fire event"));
    }

    @Test
    public void testScheduleFireEventWhenBusy() {
        FireEvent event1 = new FireEvent("12:30", 3, "FIRE", "high");
        FireEvent event2 = new FireEvent("12:45", 4, "FIRE", "moderate");

        // Schedule first event to make drone busy
        droneSubsystem.scheduleFireEvent(event1);
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());

        // Reset output capture
        outputCapture.reset();

        // Schedule second event when busy - should queue
        droneSubsystem.scheduleFireEvent(event2);
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName()); // State shouldn't change
        assertTrue("Output should mention drone cannot handle new event",
                outputCapture.toString().contains("Drone is not idle and cannot handle a new fire event"));

        // Verify queue size
        assertEquals(1, droneSubsystem.getQueueSize());
    }

    @Test
    public void testQueueProcessingAfterTaskCompletion() {
        FireEvent event1 = new FireEvent("12:30", 3, "FIRE", "high");
        FireEvent event2 = new FireEvent("12:45", 4, "FIRE", "moderate");

        // Schedule first event, then queue second event
        droneSubsystem.scheduleFireEvent(event1);
        droneSubsystem.scheduleFireEvent(event2);

        // Verify first event is being handled
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());
        assertEquals(1, droneSubsystem.getQueueSize()); // One event in queue

        // Reset output capture
        outputCapture.reset();

        // Complete cycle: dropping -> returning -> arrived -> complete
        droneSubsystem.dropAgent();
        droneSubsystem.returningBack();
        droneSubsystem.taskCompleted();

        // Verify second event started processing
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName()); // Back to EnRoute for new event
        assertEquals(0, droneSubsystem.getQueueSize()); // Queue should be empty
        assertTrue("Output should mention processing next fire event",
                outputCapture.toString().contains("Processing next fire event in queue"));
    }

    @Test
    public void testLocationUpdates() {
        // Test location getters and setters
        Location newLocation = new Location(10, 20);
        Location targetLocation = new Location(30, 40);

        droneSubsystem.setCurrentLocation(newLocation);
        droneSubsystem.setTargetLocation(targetLocation);

        assertEquals(newLocation, droneSubsystem.getCurrentLocation());
        assertEquals(targetLocation, droneSubsystem.getTargetLocation());
    }

    @Test
    public void testFullStateTransitionCycle() {
        // This test verifies a complete mission cycle through all states

        // Create a fire event
        FireEvent event = new FireEvent("12:30", 3, "FIRE", "high");

        // 1. Start in IDLE
        assertEquals("Idle", droneSubsystem.getCurrentStateName());

        // 2. Handle fire event - transition to EN ROUTE
        droneSubsystem.handleFireEvent(event);
        assertEquals("EnRoute", droneSubsystem.getCurrentStateName());

        // 3. Drop agent - transition to DROPPING AGENT
        droneSubsystem.dropAgent();
        assertEquals("droppingAgent", droneSubsystem.getCurrentStateName());

        // 4. Return to base - transition to ARRIVED TO BASE
        droneSubsystem.returningBack();
        assertEquals("ArrivedToBase", droneSubsystem.getCurrentStateName());

        // 5. Complete task - transition back to IDLE
        droneSubsystem.taskCompleted();
        assertEquals("Idle", droneSubsystem.getCurrentStateName());

        // Verify the complete cycle worked
        assertTrue(outputCapture.toString().contains("State before") &&
                outputCapture.toString().contains("State after"));
    }
}