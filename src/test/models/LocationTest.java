package models;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for the Location model
 */
public class LocationTest {
    private Location loc1;
    private Location loc2;
    private Location loc3;
    private Location origin;

    @Before
    public void setUp() {
        // Initialize test locations
        loc1 = new Location(10, 20);
        loc2 = new Location(15, 30);
        loc3 = new Location(10, 20); // Same as loc1
        origin = new Location(0, 0);
    }

    @Test
    public void testConstructorAndGetters() {
        // Test constructor and getters
        assertEquals("X coordinate should match constructor value", 10, loc1.getX());
        assertEquals("Y coordinate should match constructor value", 20, loc1.getY());
    }

    @Test
    public void testDistanceTo() {
        // Test Manhattan distance calculation
        // Manhattan distance = |x2-x1| + |y2-y1|
        assertEquals("Distance between loc1 and loc2 should be 15", 15, loc1.distanceTo(loc2)); // |15-10| + |30-20| = 5 + 10 = 15
        assertEquals("Distance between loc1 and loc3 should be 0", 0, loc1.distanceTo(loc3));   // Same location
        assertEquals("Distance between loc1 and origin should be 30", 30, loc1.distanceTo(origin)); // |0-10| + |0-20| = 10 + 20 = 30

        // Test symmetry property of distance
        assertEquals("Distance from A to B should equal distance from B to A",
                loc1.distanceTo(loc2), loc2.distanceTo(loc1));
    }


    @Test
    public void testIsOnPath_HorizontalPath() {
        Location start = new Location(0, 10);
        Location end = new Location(20, 10);

        // Points on horizontal path
        Location onPath1 = new Location(5, 10);
        Location onPath2 = new Location(15, 10);

        assertTrue("Point (5,10) should be on horizontal path", onPath1.isOnPath(start, end));
        assertTrue("Point (15,10) should be on horizontal path", onPath2.isOnPath(start, end));

        // Points not on horizontal path
        Location offPath1 = new Location(5, 11);
        Location offPath2 = new Location(15, 9);

        assertFalse("Point (5,11) should not be on horizontal path", offPath1.isOnPath(start, end));
        assertFalse("Point (15,9) should not be on horizontal path", offPath2.isOnPath(start, end));
    }

    @Test
    public void testIsOnPath_VerticalPath() {
        Location start = new Location(10, 0);
        Location end = new Location(10, 20);

        // Points on vertical path
        Location onPath1 = new Location(10, 5);
        Location onPath2 = new Location(10, 15);

        assertTrue("Point (10,5) should be on vertical path", onPath1.isOnPath(start, end));
        assertTrue("Point (10,15) should be on vertical path", onPath2.isOnPath(start, end));

        // Points not on vertical path
        Location offPath1 = new Location(11, 5);
        Location offPath2 = new Location(9, 15);

        assertFalse("Point (11,5) should not be on vertical path", offPath1.isOnPath(start, end));
        assertFalse("Point (9,15) should not be on vertical path", offPath2.isOnPath(start, end));
    }

    @Test
    public void testIsOnPath_EndpointsAreOnPath() {
        Location start = new Location(0, 0);
        Location end = new Location(10, 10);

        // Start and end points should be on the path
        assertTrue("Start point should be on the path", start.isOnPath(start, end));
        assertTrue("End point should be on the path", end.isOnPath(start, end));
    }

    @Test
    public void testIsOnPath_PointOutsidePathBounds() {
        Location start = new Location(0, 0);
        Location end = new Location(10, 10);

        // Points on the line but outside the segment
        Location beforeStart = new Location(-5, -5);
        Location afterEnd = new Location(15, 15);

        assertFalse("Point before start should not be on the path", beforeStart.isOnPath(start, end));
        assertFalse("Point after end should not be on the path", afterEnd.isOnPath(start, end));
    }
}