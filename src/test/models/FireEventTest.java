package models;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireEventTest {

    //delcare FireEvent object
    private FireEvent fireEvent;

    //initialize a fireEvent object for testing
    @BeforeEach
    void setUp() {
        fireEvent = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
    }

    //test the constructor using helper methods from FireEvent class
    @Test
    void testConstructor() {
        assertNotNull(fireEvent);
        assertEquals("14:03:15", fireEvent.getTime());
        assertEquals(3, fireEvent.getZoneID());
        assertEquals("FIRE_DETECTED", fireEvent.getEventType());
        assertEquals("High", fireEvent.getSeverity());
    }

    //test the toString method in FireEvent class
    @Test
    void testToString() {
        String expected = "Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High";
        assertEquals(expected, fireEvent.toString());
    }
}