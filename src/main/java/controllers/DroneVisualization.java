package controllers;

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
    private final DroneManager droneManager;
    private Map<String, Color> droneColors = new HashMap<>();
    private final int GRID_SIZE = 5; // Units per grid cell
    private final int PANEL_WIDTH = 800;
    private final int PANEL_HEIGHT = 600;
    private final int MARGIN = 50;
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
        
        // Create the info panel for drone status
        infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setPreferredSize(new Dimension(300, PANEL_HEIGHT));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Drone Status"));
        add(infoPanel, BorderLayout.EAST);
        
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
            // Add a header for the drone status section
            JLabel headerLabel = new JLabel("Drone Status Information");
            headerLabel.setFont(new Font("Arial", Font.BOLD, 14));
            headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(headerLabel);
            infoPanel.add(Box.createVerticalStrut(10));
            
            // Sort drones by ID for consistent display
            List<DroneStatus> sortedDrones = new ArrayList<>(drones);
            sortedDrones.sort(Comparator.comparing(DroneStatus::getDroneId));
            
            // Display info for each drone
            for (DroneStatus drone : sortedDrones) {
                // Ensure this drone has a consistent color
                if (!droneColors.containsKey(drone.getDroneId())) {
                    droneColors.put(drone.getDroneId(), generateRandomColor());
                }
                Color droneColor = droneColors.get(drone.getDroneId());
                
                // Create a panel for this drone
                JPanel dronePanel = new JPanel();
                dronePanel.setLayout(new BoxLayout(dronePanel, BoxLayout.Y_AXIS));
                dronePanel.setBorder(BorderFactory.createLineBorder(droneColor, 2));
                dronePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                
                // Drone ID with color indicator
                JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JLabel colorBox = new JLabel(" ");
                colorBox.setOpaque(true);
                colorBox.setBackground(droneColor);
                colorBox.setPreferredSize(new Dimension(15, 15));
                idPanel.add(colorBox);
                idPanel.add(new JLabel(drone.getDroneId().toUpperCase()));
                dronePanel.add(idPanel);
                
                // Location info
                dronePanel.add(new JLabel("Location: " + drone.getCurrentLocation()));
                
                // State info with appropriate color
                JLabel stateLabel = new JLabel("State: " + drone.getState());
                stateLabel.setForeground(getStateColor(drone.getState()));
                dronePanel.add(stateLabel);
                
                // Task info if applicable
                FireEvent currentTask = drone.getCurrentTask();
                if (currentTask != null) {
                    dronePanel.add(new JLabel("Task: Zone " + currentTask.getZoneID() + 
                                             " (" + currentTask.getSeverity() + ")"));
                }
                
                // Error info if applicable
                if (drone.getErrorType() != FireEvent.ErrorType.NONE) {
                    JLabel errorLabel = new JLabel("Error: " + drone.getErrorType());
                    errorLabel.setForeground(Color.RED);
                    dronePanel.add(errorLabel);
                }
                
                infoPanel.add(dronePanel);
                infoPanel.add(Box.createVerticalStrut(10));
            }
            
            // Add zone fire status section
            JLabel zoneHeader = new JLabel("Active Fires");
            zoneHeader.setFont(new Font("Arial", Font.BOLD, 14));
            zoneHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(zoneHeader);
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
                    JPanel zonePanel = new JPanel();
                    zonePanel.setLayout(new BoxLayout(zonePanel, BoxLayout.Y_AXIS));
                    zonePanel.setBorder(BorderFactory.createLineBorder(getSeverityColor(zone.getSeverity()), 2));
                    zonePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    
                    JLabel zoneLabel = new JLabel("Zone " + zone.getId() + ": " + zone.getSeverity() + 
                                               " at " + zone.getLocation());
                    zoneLabel.setForeground(getSeverityColor(zone.getSeverity()));
                    zonePanel.add(zoneLabel);
                    
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
                return new Color(0, 128, 0); // Green
            case "ENROUTE":
                return new Color(0, 0, 192); // Blue
            case "DROPPINGAGENT":
                return new Color(255, 165, 0); // Orange
            case "ARRIVEDTOBASE":
                return new Color(0, 128, 128); // Teal
            case "FAULT":
                return new Color(192, 0, 0); // Dark Red
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
                return new Color(255, 255, 0); // Yellow
            case "MODERATE":
                return new Color(255, 165, 0); // Orange
            case "HIGH":
                return new Color(255, 0, 0); // Red
            default:
                return Color.BLACK;
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
            new Color(192, 0, 0),     // Dark Red
            new Color(0, 0, 192),     // Dark Blue
            new Color(0, 128, 0),     // Dark Green
            new Color(128, 0, 128),   // Purple
            new Color(128, 128, 0),   // Olive
            new Color(0, 128, 128),   // Teal
            new Color(128, 64, 0),    // Brown
            new Color(64, 0, 128),    // Indigo
            new Color(128, 0, 64),    // Burgundy
            new Color(0, 64, 128)     // Steel Blue
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
            
            // Determine the maximum x and y coordinates
            for (Zone zone : zones.values()) {
                Location loc = zone.getLocation();
                maxX = Math.max(maxX, loc.getX());
                maxY = Math.max(maxY, loc.getY());
            }
            
            for (DroneStatus drone : drones) {
                Location loc = drone.getCurrentLocation();
                maxX = Math.max(maxX, loc.getX());
                maxY = Math.max(maxY, loc.getY());
                
                Location targetLoc = drone.getTargetLocation();
                if (targetLoc != null) {
                    maxX = Math.max(maxX, targetLoc.getX());
                    maxY = Math.max(maxY, targetLoc.getY());
                }
            }
            
            // Add margin to maximum coordinates
            maxX = Math.max(100, maxX + 10);
            maxY = Math.max(100, maxY + 10);
            
            // Calculate scale factors
            double scaleX = (double)(getWidth() - 2 * MARGIN) / maxX;
            double scaleY = (double)(getHeight() - 2 * MARGIN) / maxY;
            double scale = Math.min(scaleX, scaleY);
            
            // Draw grid
            g2d.setColor(new Color(240, 240, 240));
            for (int x = 0; x <= maxX; x += GRID_SIZE) {
                int screenX = MARGIN + (int)(x * scale);
                g2d.drawLine(screenX, MARGIN, screenX, MARGIN + (int)(maxY * scale));
            }
            
            for (int y = 0; y <= maxY; y += GRID_SIZE) {
                int screenY = MARGIN + (int)(y * scale);
                g2d.drawLine(MARGIN, screenY, MARGIN + (int)(maxX * scale), screenY);
            }
            
            // Draw zone labels
            g2d.setColor(Color.DARK_GRAY);
            for (Zone zone : zones.values()) {
                Location loc = zone.getLocation();
                int x = MARGIN + (int)(loc.getX() * scale);
                int y = MARGIN + (int)(loc.getY() * scale);
                
                // Draw zone outline
                g2d.setColor(Color.DARK_GRAY);
                g2d.drawOval(x - 10, y - 10, 20, 20);
                
                // Draw zone label
                g2d.drawString("Z" + zone.getId(), x - 5, y + 5);
                
                // If zone has a fire, draw it
                if (zone.hasFire()) {
                    // Draw fire symbol
                    g2d.setColor(getSeverityColor(zone.getSeverity()));
                    g2d.fillOval(x - 8, y - 8, 16, 16);
                    g2d.setColor(Color.BLACK);
                    g2d.drawOval(x - 8, y - 8, 16, 16);
                }
            }
            
            // Draw base location
            Location baseLocation = new Location(0, 0);
            int baseX = MARGIN + (int)(baseLocation.getX() * scale);
            int baseY = MARGIN + (int)(baseLocation.getY() * scale);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(baseX - 10, baseY - 10, 20, 20);
            g2d.drawString("BASE", baseX - 15, baseY + 25);
            
            // Draw drones
            for (DroneStatus drone : drones) {
                Location loc = drone.getCurrentLocation();
                int x = MARGIN + (int)(loc.getX() * scale);
                int y = MARGIN + (int)(loc.getY() * scale);
                
                // Get color for this drone
                Color droneColor = droneColors.getOrDefault(drone.getDroneId(), Color.BLUE);
                
                // Draw path line if drone is en route
                if (("ENROUTE".equalsIgnoreCase(drone.getState()) || "EnRoute".equalsIgnoreCase(drone.getState())) 
                        && drone.getTargetLocation() != null) {
                    Location targetLoc = drone.getTargetLocation();
                    int targetX = MARGIN + (int)(targetLoc.getX() * scale);
                    int targetY = MARGIN + (int)(targetLoc.getY() * scale);
                    
                    g2d.setColor(new Color(droneColor.getRed(), droneColor.getGreen(), droneColor.getBlue(), 128));
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5}, 0));
                    g2d.drawLine(x, y, targetX, targetY);
                    g2d.setStroke(new BasicStroke(1));
                }
                
                // Draw drone
                g2d.setColor(droneColor);
                // Use a triangle to represent the drone
                int[] xPoints = {x, x - 5, x + 5};
                int[] yPoints = {y - 8, y + 8, y + 8};
                g2d.fillPolygon(xPoints, yPoints, 3);
                g2d.setColor(Color.BLACK);
                g2d.drawPolygon(xPoints, yPoints, 3);
                
                // Highlight the drone if it has an error
                if (drone.getErrorType() != FireEvent.ErrorType.NONE) {
                    g2d.setColor(Color.RED);
                    g2d.drawOval(x - 15, y - 15, 30, 30);
                }
                
                // Draw drone ID
                g2d.setColor(Color.BLACK);
                g2d.drawString(drone.getDroneId(), x - 10, y + 20);
            }
            
            // Draw scale bar
            int scaleBarLength = 50;
            int scaleBarX = getWidth() - 100;
            int scaleBarY = getHeight() - 30;
            g2d.setColor(Color.BLACK);
            g2d.drawLine(scaleBarX, scaleBarY, scaleBarX + scaleBarLength, scaleBarY);
            g2d.drawLine(scaleBarX, scaleBarY - 5, scaleBarX, scaleBarY + 5);
            g2d.drawLine(scaleBarX + scaleBarLength, scaleBarY - 5, scaleBarX + scaleBarLength, scaleBarY + 5);
            int distanceRepresented = (int)(scaleBarLength / scale);
            g2d.drawString(distanceRepresented + " units", scaleBarX, scaleBarY - 10);
        }
    }

    /**
     * A thread to update the visualization at regular intervals
     */
    private class DroneVisualizationThread extends Thread {
        private final DroneVisualization visualization;
        private static final int UPDATE_INTERVAL = 500; // Update every 500ms
        
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