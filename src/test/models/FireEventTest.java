package models;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;


/**
 * Test class for the FireEvent model
 */
public class FireEventTest {
    private FireEvent basicEvent;
    private FireEvent assignedEvent;

    @Before
    public void setUp() {
        // Initialize test events
        basicEvent = new FireEvent("10:30", 5, "FIRE", "high");

        assignedEvent = new FireEvent("11:45", 8, "SMOKE", "low");
        assignedEvent.assignDrone("drone1");
    }

    @Test
    public void testConstructorAndGetters() {
        // Test constructor values and getters
        assertEquals("Time should match constructor value", "10:30", basicEvent.getTime());
        assertEquals("Zone ID should match constructor value", 5, basicEvent.getZoneID());
        assertEquals("Event type should match constructor value", "FIRE", basicEvent.getEventType());
        assertEquals("Severity should match constructor value", "high", basicEvent.getSeverity());
        assertNull("Initial assigned drone ID should be null", basicEvent.getAssignedDroneId());

        // Test assigned event
        assertEquals("Time should match constructor value", "11:45", assignedEvent.getTime());
        assertEquals("Zone ID should match constructor value", 8, assignedEvent.getZoneID());
        assertEquals("Event type should match constructor value", "SMOKE", assignedEvent.getEventType());
        assertEquals("Severity should match constructor value", "low", assignedEvent.getSeverity());
        assertEquals("Assigned drone ID should match", "drone1", assignedEvent.getAssignedDroneId());
    }

    @Test
    public void testSetters() {
        // Test setters
        basicEvent.setTime("12:15");
        assertEquals("Time should be updated", "12:15", basicEvent.getTime());

        basicEvent.setZoneID(10);
        assertEquals("Zone ID should be updated", 10, basicEvent.getZoneID());

        basicEvent.setEventType("EXPLOSION");
        assertEquals("Event type should be updated", "EXPLOSION", basicEvent.getEventType());

        basicEvent.setSeverity("moderate");
        assertEquals("Severity should be updated", "moderate", basicEvent.getSeverity());

        basicEvent.assignDrone("drone2");
        assertEquals("Assigned drone ID should be updated", "drone2", basicEvent.getAssignedDroneId());
    }

    @Test
    public void testCreateFireEventFromString_BasicFormat() {
        // Test creating event from basic string format
        String input = "10:30 5 FIRE high";
        FireEvent event = FireEvent.createFireEventFromString(input);

        assertEquals("Time should be parsed correctly", "10:30", event.getTime());
        assertEquals("Zone ID should be parsed correctly", 5, event.getZoneID());
        assertEquals("Event type should be parsed correctly", "FIRE", event.getEventType());
        assertEquals("Severity should be parsed correctly", "high", event.getSeverity());
        assertNull("Assigned drone ID should be null", event.getAssignedDroneId());
    }

    @Test
    public void testCreateFireEventFromString_WithDroneAssignment() {
        // Test creating event from string with drone assignment
        String input = "11:45 8 SMOKE low drone3";
        FireEvent event = FireEvent.createFireEventFromString(input);

        assertEquals("Time should be parsed correctly", "11:45", event.getTime());
        assertEquals("Zone ID should be parsed correctly", 8, event.getZoneID());
        assertEquals("Event type should be parsed correctly", "SMOKE", event.getEventType());
        assertEquals("Severity should be parsed correctly", "low", event.getSeverity());
        assertEquals("Assigned drone ID should be parsed correctly", "drone3", event.getAssignedDroneId());
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateFireEventFromString_InvalidZoneID() {
        // Test with invalid zone ID format
        String input = "10:30 notAnInteger FIRE high";
        FireEvent.createFireEventFromString(input);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testCreateFireEventFromString_InsufficientData() {
        // Test with insufficient data
        String input = "10:30 5 FIRE";  // Missing severity
        FireEvent.createFireEventFromString(input);
    }

    @Test
    public void testToString_BasicEvent() {
        // Test toString for basic event
        String expected = "10:30 5 FIRE high";
        assertEquals("String representation should match format", expected, basicEvent.toString());
    }

    @Test
    public void testToString_AssignedEvent() {
        // Test toString for event with drone assignment
        String expected = "11:45 8 SMOKE low drone1";
        assertEquals("String representation should include drone ID", expected, assignedEvent.toString());
    }

    @Test
    public void testToString_AssignmentModification() {
        // Test that toString updates when assignment changes
        String initialString = basicEvent.toString();
        assertEquals("Initial string should match basic format", "10:30 5 FIRE high", initialString);

        basicEvent.assignDrone("drone4");
        String updatedString = basicEvent.toString();
        assertEquals("Updated string should include drone ID", "10:30 5 FIRE high drone4", updatedString);
    }
}
