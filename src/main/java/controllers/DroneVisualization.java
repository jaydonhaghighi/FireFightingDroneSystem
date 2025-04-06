package controllers;

import models.DroneSpecifications;
import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

/**
 * A visualization component for the drone system
 * Shows drones, zones, and fire events on a map
 */
public class DroneVisualization extends JFrame {
    
    /**
     * Helper method to create a bold label
     */
    private JLabel createBoldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 11));
        return label;
    }
    private final DroneManager droneManager;
    private Map<String, Color> droneColors = new HashMap<>();
    private final int GRID_SIZE = 5; // Units per grid cell
    private final int PANEL_WIDTH = 800;
    private final int PANEL_HEIGHT = 600;
    private final int MARGIN = 70; // Increased margin around the map
    private MapPanel mapPanel;
    private JPanel infoPanel;
    private DroneVisualizationThread visualizationThread;
    private boolean isRunning = true;

    /**
     * Creates a new drone visualization window
     * 
     * @param droneManager the drone manager containing drone and zone data
     */
    public DroneVisualization(DroneManager droneManager) {
        super("Fire Fighting Drone System Visualization");
        this.droneManager = droneManager;
        initUI();
        
        // Initialize a thread to update the visualization
        visualizationThread = new DroneVisualizationThread(this);
        visualizationThread.start();
        
        // Handle window close event to stop the visualization thread
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                isRunning = false;
                dispose();
            }
        });
    }

    /**
     * Initialize the UI components
     */
    private void initUI() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(PANEL_WIDTH + 300, PANEL_HEIGHT + 50);
        setLayout(new BorderLayout());
        
        // Create the map panel
        mapPanel = new MapPanel();
        add(mapPanel, BorderLayout.CENTER);
        
        // Create the info panel for drone status with scrolling capability
        infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        
        // Wrap the info panel in a scroll pane
        JScrollPane scrollPane = new JScrollPane(infoPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(300, PANEL_HEIGHT));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Drone Status"));
        
        // Add the scroll pane instead of the panel directly
        add(scrollPane, BorderLayout.EAST);
        
        // Show the frame
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Updates the visualization
     */
    public void updateVisualization() {
        if (!isRunning) return;
        
        javax.swing.SwingUtilities.invokeLater(() -> {
            mapPanel.repaint();
            updateInfoPanel();
        });
    }

    /**
     * Updates the information panel with current drone status
     */
    private void updateInfoPanel() {
        infoPanel.removeAll();
        
        // Get all drones
        Collection<DroneStatus> drones = droneManager.getAllDrones();
        
        if (drones.isEmpty()) {
            JLabel emptyLabel = new JLabel("No drones registered");
            infoPanel.add(emptyLabel);
        } else {
            // Sort drones by ID for consistent display
            List<DroneStatus> sortedDrones = new ArrayList<>(drones);
            sortedDrones.sort(Comparator.comparing(DroneStatus::getDroneId));
            
            // Add a header for the drone status section
            JPanel headerPanel = new JPanel();
            headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
            headerPanel.setBackground(new Color(240, 240, 255));
            headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel headerLabel = new JLabel("Drone Fleet Status");
            headerLabel.setFont(new Font("Arial", Font.BOLD, 16));
            headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerPanel.add(headerLabel);
            
            JLabel subHeaderLabel = new JLabel("Active drones: " + sortedDrones.size());
            subHeaderLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            subHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerPanel.add(subHeaderLabel);
            
            infoPanel.add(headerPanel);
            infoPanel.add(Box.createVerticalStrut(10));
            
            
            // Display info for each drone
            for (DroneStatus drone : sortedDrones) {
                // Ensure this drone has a consistent color
                if (!droneColors.containsKey(drone.getDroneId())) {
                    droneColors.put(drone.getDroneId(), generateRandomColor());
                }
                Color droneColor = droneColors.get(drone.getDroneId());
                
                // Create a more detailed panel for this drone with specifications
                JPanel dronePanel = new JPanel();
                dronePanel.setLayout(new BoxLayout(dronePanel, BoxLayout.Y_AXIS));
                dronePanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(droneColor, 2), 
                    drone.getDroneId().toUpperCase(), 
                    javax.swing.border.TitledBorder.LEFT, 
                    javax.swing.border.TitledBorder.TOP,
                    new Font("Arial", Font.BOLD, 12)
                ));
                dronePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                // Create grid layout for drone details
                JPanel detailsPanel = new JPanel(new GridLayout(0, 2, 5, 2));
                detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                // Current location
                detailsPanel.add(createBoldLabel("Location:"));
                detailsPanel.add(new JLabel(drone.getCurrentLocation().toString()));
                
                // State info with appropriate color
                detailsPanel.add(createBoldLabel("State:"));
                JLabel stateLabel = new JLabel(drone.getState());
                stateLabel.setForeground(getStateColor(drone.getState()));
                stateLabel.setFont(new Font("Arial", Font.BOLD, 12));
                detailsPanel.add(stateLabel);
                
                // Add specifications
                DroneSpecifications specs = drone.getSpecifications();
                if (specs != null) {
                    detailsPanel.add(createBoldLabel("Max Speed:"));
                    detailsPanel.add(new JLabel(specs.getMaxSpeed() + " km/h"));
                    
                    detailsPanel.add(createBoldLabel("Max Capacity:"));
                    detailsPanel.add(new JLabel(specs.getCarryCapacity() + " L"));
                    
                    detailsPanel.add(createBoldLabel("Current Capacity:"));
                    JLabel capacityLabel = new JLabel(specs.getCurrentCapacity() + " L");
                    if (specs.getCurrentCapacity() <= 0) {
                        capacityLabel.setForeground(Color.RED);
                    }
                    detailsPanel.add(capacityLabel);
                    
                    detailsPanel.add(createBoldLabel("Flow Rate:"));
                    detailsPanel.add(new JLabel(specs.getFlowRate() + " L/s"));
                }
                
                // Task info if applicable
                FireEvent currentTask = drone.getCurrentTask();
                if (currentTask != null) {
                    detailsPanel.add(createBoldLabel("Task:"));
                    JLabel taskLabel = new JLabel("Zone " + currentTask.getZoneID() + 
                                               " (" + currentTask.getSeverity() + ")");
                    taskLabel.setForeground(getSeverityColor(currentTask.getSeverity()));
                    detailsPanel.add(taskLabel);
                    
                    detailsPanel.add(createBoldLabel("Target:"));
                    detailsPanel.add(new JLabel(drone.getTargetLocation().toString()));
                }
                
                // Error info if applicable
                if (drone.getErrorType() != FireEvent.ErrorType.NONE) {
                    detailsPanel.add(createBoldLabel("Error:"));
                    JLabel errorLabel = new JLabel(drone.getErrorType().toString());
                    errorLabel.setForeground(Color.RED);
                    errorLabel.setFont(new Font("Arial", Font.BOLD, 12));
                    detailsPanel.add(errorLabel);
                }
                
                // Add missions completed info
                detailsPanel.add(createBoldLabel("Missions:"));
                detailsPanel.add(new JLabel(String.valueOf(drone.getZonesServiced())));
                
                dronePanel.add(detailsPanel);
                
                infoPanel.add(dronePanel);
                infoPanel.add(Box.createVerticalStrut(10));
            }
            
            // Add zone fire status section with a divider
            infoPanel.add(new JSeparator());
            infoPanel.add(Box.createVerticalStrut(10));
            
            JPanel fireHeaderPanel = new JPanel();
            fireHeaderPanel.setLayout(new BoxLayout(fireHeaderPanel, BoxLayout.Y_AXIS));
            fireHeaderPanel.setBackground(new Color(255, 240, 240));
            fireHeaderPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            fireHeaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel zoneHeader = new JLabel("Active Fires");
            zoneHeader.setFont(new Font("Arial", Font.BOLD, 16));
            zoneHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            fireHeaderPanel.add(zoneHeader);
            
            infoPanel.add(fireHeaderPanel);
            infoPanel.add(Box.createVerticalStrut(10));
            
            // Display active fire zones
            Map<Integer, Zone> zones = droneManager.getAllZones();
            List<Zone> activeFireZones = zones.values().stream()
                .filter(Zone::hasFire)
                .sorted(Comparator.comparing(Zone::getId))
                .toList();
            
            if (activeFireZones.isEmpty()) {
                JLabel noFiresLabel = new JLabel("No active fires");
                infoPanel.add(noFiresLabel);
            } else {
                for (Zone zone : activeFireZones) {
                    JPanel zonePanel = new JPanel(new GridLayout(0, 2, 5, 2));
                    zonePanel.setBorder(BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(getSeverityColor(zone.getSeverity()), 2),
                        "Zone " + zone.getId(),
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("Arial", Font.BOLD, 12),
                        getSeverityColor(zone.getSeverity())
                    ));
                    zonePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    
                    // Zone information
                    zonePanel.add(createBoldLabel("Severity:"));
                    JLabel severityLabel = new JLabel(zone.getSeverity());
                    severityLabel.setForeground(getSeverityColor(zone.getSeverity()));
                    severityLabel.setFont(new Font("Arial", Font.BOLD, 12));
                    zonePanel.add(severityLabel);
                    
                    zonePanel.add(createBoldLabel("Location:"));
                    zonePanel.add(new JLabel(zone.getLocation().toString()));
                    
                    zonePanel.add(createBoldLabel("Size:"));
                    zonePanel.add(new JLabel(zone.getWidth() + "m × " + zone.getHeight() + "m"));
                    
                    // Add drones needed information
                    int dronesNeeded = getDronesNeededForSeverity(zone.getSeverity());
                    
                    // Find assigned drones for this zone
                    int assignedDrones = 0;
                    for (DroneStatus drone : droneManager.getAllDrones()) {
                        FireEvent event = drone.getCurrentTask();
                        if (event != null && event.getZoneID() == zone.getId()) {
                            assignedDrones++;
                        }
                    }
                    
                    int moreDronesNeeded = Math.max(0, dronesNeeded - assignedDrones);
                    
                    zonePanel.add(createBoldLabel("Drones:"));
                    JLabel dronesLabel = new JLabel(assignedDrones + "/" + dronesNeeded + 
                        (moreDronesNeeded > 0 ? " (" + moreDronesNeeded + " more needed)" : " (sufficient)"));
                    
                    if (moreDronesNeeded > 0) {
                        dronesLabel.setForeground(Color.RED);
                        dronesLabel.setFont(new Font("Arial", Font.BOLD, 12));
                    } else {
                        dronesLabel.setForeground(new Color(0, 128, 0)); // Green
                    }
                    zonePanel.add(dronesLabel);
                    
                    infoPanel.add(zonePanel);
                    infoPanel.add(Box.createVerticalStrut(5));
                }
            }
        }
        
        infoPanel.revalidate();
        infoPanel.repaint();
    }

    /**
     * Gets an appropriate color for a drone state
     * 
     * @param state the drone state
     * @return a color representing the state
     */
    private Color getStateColor(String state) {
        if (state == null) return Color.BLACK;
        
        state = state.toUpperCase();
        switch (state) {
            case "IDLE":
                return new Color(0, 128, 0);
            case "ENROUTE":
                return new Color(0, 0, 192);
            case "DROPPINGAGENT":
                return new Color(255, 165, 0);
            case "ARRIVEDTOBASE":
                return new Color(0, 128, 128);
            case "FAULT":
                return new Color(192, 0, 0);
            default:
                return Color.BLACK;
        }
    }

    /**
     * Gets an appropriate color for a fire severity
     * 
     * @param severity the fire severity
     * @return a color representing the severity
     */
    private Color getSeverityColor(String severity) {
        if (severity == null) return Color.BLACK;
        
        severity = severity.toUpperCase();
        switch (severity) {
            case "LOW":
                return new Color(255, 210, 0);
            case "MODERATE":
                return new Color(255, 130, 0);
            case "HIGH":
                return new Color(255, 0, 0);
            default:
                return Color.BLACK;
        }
    }
    
    /**
     * Determines how many drones are needed based on fire severity
     * 
     * @param severity the fire severity
     * @return the number of drones needed
     */
    private int getDronesNeededForSeverity(String severity) {
        if (severity == null) return 0;
        
        severity = severity.toUpperCase();
        switch (severity) {
            case "HIGH":
                return 3;
            case "MODERATE":
                return 2;
            case "LOW":
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Generates a random color for a drone
     * 
     * @return a random color
     */
    private Color generateRandomColor() {
        // Generate colors that are visually distinct
        Color[] presetColors = {
            new Color(192, 0, 0),
            new Color(0, 0, 192),
            new Color(0, 128, 0),
            new Color(128, 0, 128),
            new Color(128, 128, 0),
            new Color(0, 128, 128),
            new Color(128, 64, 0),
            new Color(64, 0, 128),
            new Color(128, 0, 64),
            new Color(0, 64, 128)
        };
        
        // Use preset colors first, then fall back to random colors if needed
        if (droneColors.size() < presetColors.length) {
            return presetColors[droneColors.size()];
        } else {
            // Generate a random color if we run out of presets
            Random random = new Random();
            return new Color(
                random.nextInt(128) + 64,
                random.nextInt(128) + 64,
                random.nextInt(128) + 64
            );
        }
    }

    /**
     * The map panel that visualizes drones and zones
     */
    private class MapPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Get the bounds of all zones to determine the scale
            int maxX = 0, maxY = 0;
            Map<Integer, Zone> zones = droneManager.getAllZones();
            Collection<DroneStatus> drones = droneManager.getAllDrones();
            
            // Get the actual boundaries of all zones
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (Zone zone : zones.values()) {
                // Check zone corners for max/min coordinates
                minX = Math.min(minX, zone.getTopLeft().getX());
                minY = Math.min(minY, zone.getTopLeft().getY());
                maxX = Math.max(maxX, zone.getBottomRight().getX());
                maxY = Math.max(maxY, zone.getBottomRight().getY());
            }
            
            // Also include drone positions
            for (DroneStatus drone : drones) {
                Location loc = drone.getCurrentLocation();
                minX = Math.min(minX, loc.getX());
                minY = Math.min(minY, loc.getY());
                maxX = Math.max(maxX, loc.getX());
                maxY = Math.max(maxY, loc.getY());
                
                Location targetLoc = drone.getTargetLocation();
                if (targetLoc != null) {
                    minX = Math.min(minX, targetLoc.getX());
                    minY = Math.min(minY, targetLoc.getY());
                    maxX = Math.max(maxX, targetLoc.getX());
                    maxY = Math.max(maxY, targetLoc.getY());
                }
            }
            
            // Set minimum bounds to include origin (0,0)
            minX = Math.min(minX, 0);
            minY = Math.min(minY, 0);
            
            // Add padding to ensure all elements are visible
            int padding = 100;
            minX = Math.max(0, minX - padding);
            minY = Math.max(0, minY - padding);
            maxX = Math.max(100, maxX + padding);
            maxY = Math.max(100, maxY + padding);
            
            // Calculate scale factors based on the range of coordinates
            double rangeX = maxX - minX;
            double rangeY = maxY - minY;
            double scaleX = (double)(getWidth() - 2 * MARGIN) / rangeX;
            double scaleY = (double)(getHeight() - 2 * MARGIN) / rangeY;
            
            // Use the smaller scale to maintain aspect ratio
            double scale = Math.min(scaleX, scaleY);
            
            // Draw grid
            g2d.setColor(new Color(240, 240, 240));
            // Draw vertical grid lines
            for (int x = (minX / GRID_SIZE) * GRID_SIZE; x <= maxX; x += GRID_SIZE) {
                int screenX = MARGIN + (int)((x - minX) * scale);
                g2d.drawLine(screenX, MARGIN, screenX, MARGIN + (int)(rangeY * scale));
            }
            
            // Draw horizontal grid lines
            for (int y = (minY / GRID_SIZE) * GRID_SIZE; y <= maxY; y += GRID_SIZE) {
                int screenY = MARGIN + (int)((y - minY) * scale);
                g2d.drawLine(MARGIN, screenY, MARGIN + (int)(rangeX * scale), screenY);
            }
            
            // Draw zone labels
            g2d.setColor(Color.DARK_GRAY);
            for (Zone zone : zones.values()) {
                Location loc = zone.getLocation();
                int x = MARGIN + (int)((loc.getX() - minX) * scale);
                int y = MARGIN + (int)((loc.getY() - minY) * scale);
                
                // Draw zone as a rectangle using actual dimensions
                int zoneWidth = (int)(zone.getWidth() * scale);
                int zoneHeight = (int)(zone.getHeight() * scale);
                int zoneX = MARGIN + (int)((zone.getTopLeft().getX() - minX) * scale);
                int zoneY = MARGIN + (int)((zone.getTopLeft().getY() - minY) * scale);
                
                // Draw zone outline
                g2d.setColor(new Color(100, 100, 100, 80)); // Semi-transparent gray
                g2d.fillRect(zoneX, zoneY, zoneWidth, zoneHeight);
                g2d.setColor(Color.DARK_GRAY);
                g2d.drawRect(zoneX, zoneY, zoneWidth, zoneHeight);
                
                // Draw zone circle
                int circleSize = 30;
                if (zone.hasFire()) {
                    // For zones with fire, use severity color
                    g2d.setColor(getSeverityColor(zone.getSeverity()));
                    
                    // Find assigned drones for this zone
                    int assignedDrones = 0;
                    int dronesNeeded = getDronesNeededForSeverity(zone.getSeverity());
                    
                    for (DroneStatus drone : drones) {
                        FireEvent event = drone.getCurrentTask();
                        if (event != null && event.getZoneID() == zone.getId()) {
                            assignedDrones++;
                        }
                    }
                    
                    int moreDronesNeeded = Math.max(0, dronesNeeded - assignedDrones);
                    
                    // Fill the circle with the severity color
                    g2d.fillOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                    g2d.setColor(Color.BLACK);
                    g2d.drawOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                    
                    // Add zone ID inside the circle
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 12));
                    FontMetrics fm = g2d.getFontMetrics();
                    String zoneText = "Z" + zone.getId();
                    int textWidth = fm.stringWidth(zoneText);
                    g2d.drawString(zoneText, x - textWidth/2, y + 4);
                } else {
                    // For zones without fire, use a neutral gray
                    g2d.setColor(new Color(200, 200, 200));
                    g2d.fillOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                    g2d.setColor(Color.BLACK);
                    g2d.drawOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                    
                    // Add zone ID inside the circle
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 12));
                    FontMetrics fm = g2d.getFontMetrics();
                    String zoneText = "Z" + zone.getId();
                    int textWidth = fm.stringWidth(zoneText);
                    g2d.drawString(zoneText, x - textWidth/2, y + 4);
                }
                g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            }
            
            // Draw base location
            Location baseLocation = new Location(0, 0);
            int baseX = MARGIN + (int)((baseLocation.getX() - minX) * scale);
            int baseY = MARGIN + (int)((baseLocation.getY() - minY) * scale);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(baseX - 10, baseY - 10, 20, 20);
            g2d.drawString("BASE", baseX - 15, baseY + 25);
            
            // Draw drones
            for (DroneStatus drone : drones) {
                Location loc = drone.getCurrentLocation();
                int x = MARGIN + (int)((loc.getX() - minX) * scale);
                int y = MARGIN + (int)((loc.getY() - minY) * scale);
                
                // Get color for this drone
                Color droneColor = droneColors.getOrDefault(drone.getDroneId(), Color.BLUE);
                
                // Draw path line if drone is en route
                if (("ENROUTE".equalsIgnoreCase(drone.getState()) || "EnRoute".equalsIgnoreCase(drone.getState())) 
                        && drone.getTargetLocation() != null) {
                    Location targetLoc = drone.getTargetLocation();
                    int targetX = MARGIN + (int)((targetLoc.getX() - minX) * scale);
                    int targetY = MARGIN + (int)((targetLoc.getY() - minY) * scale);
                    
                    g2d.setColor(new Color(droneColor.getRed(), droneColor.getGreen(), droneColor.getBlue(), 128));
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5}, 0));
                    g2d.drawLine(x, y, targetX, targetY);
                    g2d.setStroke(new BasicStroke(1));
                }
                
                // Determine direction if drone is moving (for motion indicator)
                double angle = 0; // Default pointing up
                
                if (drone.getTargetLocation() != null && !drone.getCurrentLocation().equals(drone.getTargetLocation())) {
                    Location target = drone.getTargetLocation();
                    // Calculate angle between current and target
                    angle = Math.atan2(target.getY() - loc.getY(), target.getX() - loc.getX());
                }
                
                // Draw drone body as a circle
                int circleSize = 12;
                
                // Draw drone body
                g2d.setColor(droneColor);
                g2d.fillOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x - circleSize/2, y - circleSize/2, circleSize, circleSize);
                
                // Add direction indicator (small line showing movement direction)
                if (drone.getTargetLocation() != null && !drone.getCurrentLocation().equals(drone.getTargetLocation())) {
                    int dirLength = 8;
                    g2d.setColor(Color.BLACK);
                    g2d.drawLine(x, y, 
                               x + (int)(dirLength * Math.cos(angle)), 
                               y + (int)(dirLength * Math.sin(angle)));
                }
                
                // Draw drone state indicator
                Color stateColor = getStateColor(drone.getState());
                g2d.setColor(stateColor);
                g2d.fillOval(x - 3, y - 3, 6, 6);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x - 3, y - 3, 6, 6);
                
                // Highlight the drone if it has an error
                if (drone.getErrorType() != FireEvent.ErrorType.NONE) {
                    g2d.setColor(Color.RED);
                    g2d.drawOval(x - 15, y - 15, 30, 30);
                    g2d.setColor(new Color(255, 0, 0, 100)); // Semi-transparent red
                    g2d.fillOval(x - 15, y - 15, 30, 30);
                }
                
                // Draw drone ID only
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 11));
                g2d.drawString(drone.getDroneId(), x - 15, y + 20);
            }
            
            // Draw scale bar
            int scaleBarLength = 100;
            int scaleBarX = getWidth() - 150;
            int scaleBarY = getHeight() - 30;
            g2d.setColor(Color.BLACK);
            g2d.drawLine(scaleBarX, scaleBarY, scaleBarX + scaleBarLength, scaleBarY);
            g2d.drawLine(scaleBarX, scaleBarY - 5, scaleBarX, scaleBarY + 5);
            g2d.drawLine(scaleBarX + scaleBarLength, scaleBarY - 5, scaleBarX + scaleBarLength, scaleBarY + 5);
            int distanceRepresented = (int)(scaleBarLength / scale);
            g2d.drawString(distanceRepresented + " meters", scaleBarX, scaleBarY - 10);
        }
    }

    /**
     * A thread to update the visualization at regular intervals
     */
    private class DroneVisualizationThread extends Thread {
        private final DroneVisualization visualization;
        private static final int UPDATE_INTERVAL = 100; // Update more frequently for smoother animation
        
        /**
         * Creates a new visualization thread
         * 
         * @param visualization the visualization to update
         */
        public DroneVisualizationThread(DroneVisualization visualization) {
            super("DroneVisualizationThread");
            this.visualization = visualization;
        }
        
        @Override
        public void run() {
            while (isRunning) {
                visualization.updateVisualization();
                try {
                    Thread.sleep(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}