package models;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for the Zone model
 */
public class ZoneTest {
    private Zone zone1;
    private Zone zone2;
    private Zone zone3;
    private Zone singlePointZone;
    private Location insideZone1;
    private Location onZone1Border;
    private Location outsideZone1;
    private Location centerZone1;

    @Before
    public void setUp() {
        // Initialize test zones
        // Zone with boundaries (10,10) to (20,20)
        zone1 = new Zone(1, 10, 10, 20, 20);

        // Overlapping zone with boundaries (15,15) to (25,25)
        zone2 = new Zone(2, 15, 15, 25, 25);

        // Non-overlapping zone with boundaries (30,30) to (40,40)
        zone3 = new Zone(3, 30, 30, 40, 40);

        // Zone created with single location constructor
        Location singlePoint = new Location(50, 50);
        singlePointZone = new Zone(4, singlePoint);

        // Test locations
        centerZone1 = new Location(15, 15);  // Center of zone1
        insideZone1 = new Location(12, 18);  // Inside zone1
        onZone1Border = new Location(10, 15); // On zone1 border
        outsideZone1 = new Location(5, 5);   // Outside zone1
    }

    @Test
    public void testConstructorAndGetters_BoundariesConstructor() {
        // Test zone created with boundaries constructor
        assertEquals("Zone ID should match constructor value", 1, zone1.getId());
        assertEquals("Top-left X should match constructor value", 10, zone1.getTopLeft().getX());
        assertEquals("Top-left Y should match constructor value", 10, zone1.getTopLeft().getY());
        assertEquals("Bottom-right X should match constructor value", 20, zone1.getBottomRight().getX());
        assertEquals("Bottom-right Y should match constructor value", 20, zone1.getBottomRight().getY());
        assertEquals("Center X should be calculated correctly", 15, zone1.getLocation().getX());
        assertEquals("Center Y should be calculated correctly", 15, zone1.getLocation().getY());
        assertEquals("Width should be calculated correctly", 10, zone1.getWidth());
        assertEquals("Height should be calculated correctly", 10, zone1.getHeight());
    }

    @Test
    public void testConstructorAndGetters_SingleLocationConstructor() {
        // Test zone created with single location constructor
        assertEquals("Zone ID should match constructor value", 4, singlePointZone.getId());

        // Check that the center is the provided location
        assertEquals("Center X should match provided location", 50, singlePointZone.getLocation().getX());
        assertEquals("Center Y should match provided location", 50, singlePointZone.getLocation().getY());

        // Check that boundaries are created correctly around the center point (default +/- 5)
        assertEquals("Top-left X should be center X - 5", 45, singlePointZone.getTopLeft().getX());
        assertEquals("Top-left Y should be center Y - 5", 45, singlePointZone.getTopLeft().getY());
        assertEquals("Bottom-right X should be center X + 5", 55, singlePointZone.getBottomRight().getX());
        assertEquals("Bottom-right Y should be center Y + 5", 55, singlePointZone.getBottomRight().getY());

        // Check dimensions
        assertEquals("Width should be 10", 10, singlePointZone.getWidth());
        assertEquals("Height should be 10", 10, singlePointZone.getHeight());
    }

    @Test
    public void testContains() {
        // Center point should be inside
        assertTrue("Center point should be inside the zone", zone1.contains(centerZone1));

        // Point inside boundaries should be inside
        assertTrue("Point inside boundaries should be contained", zone1.contains(insideZone1));

        // Point on boundary should be inside (inclusive boundaries)
        assertTrue("Point on boundary should be contained", zone1.contains(onZone1Border));

        // Point outside boundaries should not be inside
        assertFalse("Point outside boundaries should not be contained", zone1.contains(outsideZone1));
    }

    @Test
    public void testOverlaps() {
        // Test overlapping zones
        assertTrue("Zones with overlapping areas should report overlap", zone1.overlaps(zone2));
        assertTrue("Overlap check should be symmetric", zone2.overlaps(zone1));

        // Test non-overlapping zones
        assertFalse("Zones without overlap should report no overlap", zone1.overlaps(zone3));
        assertFalse("No overlap check should be symmetric", zone3.overlaps(zone1));

        // Test a zone with itself (complete overlap)
        assertTrue("Zone should overlap with itself", zone1.overlaps(zone1));

        // Test adjacent zones (sharing an edge but not overlapping area)
        Zone adjacentZone = new Zone(5, 20, 10, 30, 20); // Right edge of zone1 = left edge of adjacentZone
        assertTrue("Adjacent zones should be detected as overlapping", zone1.overlaps(adjacentZone));
    }

    @Test
    public void testFireStatus() {
        // Test initial fire status
        assertFalse("Initial fire status should be false", zone1.hasFire());
        assertEquals("Initial severity should be 'NONE'", "NONE", zone1.getSeverity());

        // Test setting fire status
        zone1.setHasFire(true);
        assertTrue("Fire status should be updated to true", zone1.hasFire());

        // Test setting severity
        zone1.setSeverity("HIGH");
        assertEquals("Severity should be updated", "HIGH", zone1.getSeverity());

        // Test resetting fire status
        zone1.setHasFire(false);
        assertFalse("Fire status should be updated to false", zone1.hasFire());

        // Severity remains even when fire is out
        assertEquals("Severity should remain until explicitly changed", "HIGH", zone1.getSeverity());
    }
}