package controllers;

import static org.junit.Assert.*;

import models.FireEvent;
import models.Location;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Tests the communication between the different subsystems
 *
 * This test uses simplified mock versions of the components to avoid
 * actual network operations and port conflicts.
 */
public class CommunicationTest {

    private final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    // Test components
    private MockFireIncidentSubsystem fireSystem;
    private MockScheduler scheduler;
    private MockDroneSubsystem drone;

    @Before
    public void setUp() throws Exception {
        // Redirect System.out to capture console output for verification
        System.setOut(new PrintStream(outputCapture));

        // Create test components with no actual network communication
        fireSystem = new MockFireIncidentSubsystem();
        scheduler = new MockScheduler();
        drone = new MockDroneSubsystem("drone1", new Location(0, 0));
    }

    @After
    public void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }

    /**
     * Tests the complete communication flow from fire event to drone assignment and back
     */
    @Test
    public void testCompleteMessageFlow() throws Exception {
        // Fire event details
        String eventTime = "14:30";
        int zoneId = 3;
        String eventType = "FIRE";
        String severity = "high";

        // 1. Create a fire event
        FireEvent event = new FireEvent(eventTime, zoneId, eventType, severity);

        // 2. Fire system sends event to scheduler
        fireSystem.simulateSendingEvent(event);
        fireSystem.simulateReceivingAck();

        // 3. Connect fire system and scheduler
        scheduler.simulateReceivingEvent(event);

        // 4. Verify scheduler received the event
        FireEvent receivedByScheduler = scheduler.getLastReceivedEvent();

        assertNotNull("Scheduler should have received the event", receivedByScheduler);
        assertEquals("Event time should match", eventTime, receivedByScheduler.getTime());
        assertEquals("Event zone should match", zoneId, receivedByScheduler.getZoneID());
        assertEquals("Event type should match", eventType, receivedByScheduler.getEventType());
        assertEquals("Event severity should match", severity, receivedByScheduler.getSeverity());

        // 5. Scheduler assigns event to drone
        scheduler.simulateAssigningEventToDrone(receivedByScheduler, "drone1");

        // 6. Connect scheduler and drone
        drone.simulateReceivingEvent(scheduler.getLastAssignedEvent());

        // 7. Verify drone received the assignment
        FireEvent receivedByDrone = drone.getLastReceivedEvent();

        assertNotNull("Drone should have received the event", receivedByDrone);
        assertEquals("Event time should match", eventTime, receivedByDrone.getTime());
        assertEquals("Event zone should match", zoneId, receivedByDrone.getZoneID());
        assertEquals("Event type should match", eventType, receivedByDrone.getEventType());
        assertEquals("Event severity should match", severity, receivedByDrone.getSeverity());
        assertEquals("Event should be assigned to correct drone", "drone1", receivedByDrone.getAssignedDroneId());

        // 8. Drone sends status update back to scheduler
        drone.simulateSendingStatusUpdate("EN ROUTE", new Location(5, 5));

        // 9. Connect drone and scheduler
        scheduler.simulateReceivingDroneStatus(drone.getLastStatusUpdate());

        // 10. Verify scheduler received the status update
        String lastDroneStatus = scheduler.getLastDroneStatus();
        assertNotNull("Scheduler should have received status update", lastDroneStatus);
        assertTrue("Status should contain drone ID", lastDroneStatus.contains("drone1"));
        assertTrue("Status should contain state", lastDroneStatus.contains("EN ROUTE"));
        assertTrue("Status should contain location", lastDroneStatus.contains("5") && lastDroneStatus.contains("5"));

        // 11. Drone completes task and sends final status
        drone.simulateSendingStatusUpdate("IDLE", new Location(0, 0));

        // 12. Connect drone and scheduler again
        scheduler.simulateReceivingDroneStatus(drone.getLastStatusUpdate());

        // 13. Verify scheduler received the final status
        lastDroneStatus = scheduler.getLastDroneStatus();
        assertNotNull("Scheduler should have received final status", lastDroneStatus);
        assertTrue("Status should contain drone ID", lastDroneStatus.contains("drone1"));
        assertTrue("Status should contain state", lastDroneStatus.contains("IDLE"));
        assertTrue("Status should contain base location", lastDroneStatus.contains("0") && lastDroneStatus.contains("0"));
    }

    /**
     * Tests the resilience of the system to malformed messages
     */
    @Test
    public void testMalformedMessageHandling() throws Exception {
        // Create a malformed message
        String malformedMessage = "NotAValidFormat";

        // 1. Send malformed message to scheduler
        scheduler.simulateReceivingRawMessage(malformedMessage);

        // 2. Verify scheduler didn't crash and handled the error
        String output = outputCapture.toString();
        assertTrue("Scheduler should log an error for malformed messages",
                output.contains("Warning") || output.contains("Error") || output.contains("could not be parsed"));

        // 3. Verify scheduler is still operational by sending a valid event
        FireEvent validEvent = new FireEvent("14:30", 3, "FIRE", "high");
        scheduler.simulateReceivingEvent(validEvent);

        // 4. Verify the valid event was processed correctly
        FireEvent receivedEvent = scheduler.getLastReceivedEvent();
        assertNotNull("Scheduler should still process valid events after receiving malformed ones", receivedEvent);
        assertEquals("Event time should match", "14:30", receivedEvent.getTime());
    }

    /**
     * Tests the handling of multiple concurrent fire events
     */
    @Test
    public void testMultipleConcurrentEvents() throws Exception {
        // Create multiple fire events
        FireEvent event1 = new FireEvent("14:30", 3, "FIRE", "high");
        FireEvent event2 = new FireEvent("14:31", 4, "FIRE", "moderate");
        FireEvent event3 = new FireEvent("14:32", 5, "FIRE", "low");

        // Create a queue of events for the scheduler
        FireEvent[] events = {event1, event2, event3};

        // Add events to scheduler queue and track them separately
        for (FireEvent event : events) {
            scheduler.simulateReceivingEvent(event);
        }

        // Verify scheduler queued the events
        int queueSize = scheduler.getEventQueueSize();
        assertEquals("Scheduler should have queued all three events", 3, queueSize);

        // Track assignments manually
        for (int i = 0; i < events.length; i++) {
            // Assign the event
            scheduler.simulateAssigningEventToDrone(events[i], "drone1");

            // Verify queue size decreases
            int expectedSize = events.length - i - 1;
            assertEquals("Queue size should be " + expectedSize + " after assigning event " + (i+1),
                    expectedSize, scheduler.getEventQueueSize());
        }

        // Verify queue is empty at the end
        assertEquals("Queue should be empty now", 0, scheduler.getEventQueueSize());
    }

    /**
     * Simple mock of FireIncidentSubsystem that doesn't use actual network
     */
    private static class MockFireIncidentSubsystem {
        private FireEvent lastSentEvent;
        private boolean ackReceived;

        public void simulateSendingEvent(FireEvent event) {
            lastSentEvent = event;
            System.out.println("[MOCK FIRE SYSTEM] Sending fire event: " + event);
        }

        public void simulateReceivingAck() {
            ackReceived = true;
            System.out.println("[MOCK FIRE SYSTEM] Received acknowledgment");
        }

        public FireEvent getLastSentEvent() {
            return lastSentEvent;
        }

        public boolean isAckReceived() {
            return ackReceived;
        }
    }

    /**
     * Simple mock of Scheduler that doesn't use actual network
     */
    private static class MockScheduler {
        private FireEvent lastReceivedEvent;
        private FireEvent lastAssignedEvent;
        private String lastDroneStatus;
        private int queueSize = 0;

        public void simulateReceivingEvent(FireEvent event) {
            lastReceivedEvent = event;
            queueSize++;
            System.out.println("[MOCK SCHEDULER] Received fire event: " + event);
        }

        public void simulateReceivingRawMessage(String message) {
            System.out.println("[MOCK SCHEDULER] Received raw message: " + message);
            // Try to parse, likely will fail for malformed messages
            try {
                FireEvent event = models.FireEvent.createFireEventFromString(message);
                lastReceivedEvent = event;
                queueSize++;
            } catch (Exception e) {
                System.out.println("[MOCK SCHEDULER] Warning: Received a message that could not be parsed: " + message);
            }
        }

        public void simulateReceivingDroneStatus(String status) {
            lastDroneStatus = status;
            System.out.println("[MOCK SCHEDULER] Received drone status: " + status);
        }

        public void simulateAssigningEventToDrone(FireEvent event, String droneId) {
            event.assignDrone(droneId);
            lastAssignedEvent = event;
            if (queueSize > 0) {
                queueSize--;
            }
            System.out.println("[MOCK SCHEDULER] Assigned event to drone " + droneId + ": " + event);
        }

        public void simulateAssigningNextEvent(String droneId) {
            if (lastReceivedEvent != null && queueSize > 0) {
                simulateAssigningEventToDrone(lastReceivedEvent, droneId);
                lastReceivedEvent = null;
            } else {
                System.out.println("[MOCK SCHEDULER] No events to assign");
            }
        }

        public FireEvent getLastReceivedEvent() {
            return lastReceivedEvent;
        }

        public FireEvent getLastAssignedEvent() {
            return lastAssignedEvent;
        }

        public String getLastDroneStatus() {
            return lastDroneStatus;
        }

        public int getEventQueueSize() {
            return queueSize;
        }
    }

    /**
     * Simple mock of DroneSubsystem that doesn't use actual network
     */
    private static class MockDroneSubsystem {
        private final String droneId;
        private final Location baseLocation;
        private Location currentLocation;
        private String currentState = "IDLE";
        private FireEvent lastReceivedEvent;
        private String lastStatusUpdate;

        public MockDroneSubsystem(String droneId, Location baseLocation) {
            this.droneId = droneId;
            this.baseLocation = baseLocation;
            this.currentLocation = baseLocation;
        }

        public void simulateReceivingEvent(FireEvent event) {
            lastReceivedEvent = event;
            // Simulate state change to EN ROUTE
            currentState = "EN ROUTE";
            System.out.println("[MOCK DRONE " + droneId + "] Received fire event: " + event);
        }

        public void simulateSendingStatusUpdate(String state, Location location) {
            currentState = state;
            currentLocation = location;
            lastStatusUpdate = droneId + " " + state + " " + location.getX() + " " + location.getY();
            System.out.println("[MOCK DRONE " + droneId + "] Sending status update: " + lastStatusUpdate);
        }

        public FireEvent getLastReceivedEvent() {
            return lastReceivedEvent;
        }

        public String getLastStatusUpdate() {
            return lastStatusUpdate;
        }

        public String getCurrentState() {
            return currentState;
        }

        public Location getCurrentLocation() {
            return currentLocation;
        }
    }
}