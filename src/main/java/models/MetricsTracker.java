package models;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The MetricsTracker class provides a way to track and calculate various performance metrics
 * for the drone fire fighting system, such as overall time to extinguish all fires and
 * average response time per fire.
 */
public class MetricsTracker {
    // System-wide metrics
    private Instant systemStartTime = null; // Will be set on first fire
    private Instant systemEndTime = null;
    private final AtomicLong totalFiresDetected = new AtomicLong(0);
    private final AtomicLong totalFiresExtinguished = new AtomicLong(0);
    
    // Fire-specific metrics with thread-safe maps
    private final Map<Integer, Instant> fireStartTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Instant> fireEndTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Instant> firstResponseTimes = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static MetricsTracker instance;
    
    /**
     * Private constructor for singleton pattern
     */
    private MetricsTracker() {
        resetMetrics();
    }
    
    /**
     * Gets the singleton instance of the MetricsTracker
     * @return the MetricsTracker instance
     */
    public static synchronized MetricsTracker getInstance() {
        if (instance == null) {
            instance = new MetricsTracker();
        }
        return instance;
    }
    
    /**
     * Resets all metrics to their initial state
     */
    public void resetMetrics() {
        systemStartTime = null; // Will be set when first fire is detected
        systemEndTime = null;
        totalFiresDetected.set(0);
        totalFiresExtinguished.set(0);
        fireStartTimes.clear();
        fireEndTimes.clear();
        firstResponseTimes.clear();
    }
    
    /**
     * Records a new fire detection event
     * @param zoneId the zone ID where the fire was detected
     */
    public void recordFireDetected(int zoneId) {
        // If this is the first fire, start the system timer
        if (totalFiresDetected.incrementAndGet() == 1) {
            systemStartTime = Instant.now();
        }
        
        // Record this specific fire's start time
        fireStartTimes.putIfAbsent(zoneId, Instant.now());
    }
    
    /**
     * Records a drone response to a fire
     * @param zoneId the zone ID where the response occurred
     */
    public void recordDroneResponse(int zoneId) {
        if (fireStartTimes.containsKey(zoneId) && !firstResponseTimes.containsKey(zoneId)) {
            firstResponseTimes.put(zoneId, Instant.now());
        }
    }
    
    /**
     * Records a fire extinguished event
     * @param zoneId the zone ID where the fire was extinguished
     */
    public void recordFireExtinguished(int zoneId) {
        if (fireStartTimes.containsKey(zoneId) && !fireEndTimes.containsKey(zoneId)) {
            fireEndTimes.put(zoneId, Instant.now());
            totalFiresExtinguished.incrementAndGet();
            
            // Check if all fires are extinguished
            if (totalFiresExtinguished.get() >= totalFiresDetected.get() && totalFiresDetected.get() > 0) {
                systemEndTime = Instant.now();
            }
        }
    }
    
    /**
     * Gets the total duration between system start and end time
     * @return duration in milliseconds, or 0 if no fires started yet, or current duration if still running
     */
    public long getTotalSystemDuration() {
        // If no fires have been detected yet, timer hasn't started
        if (systemStartTime == null) {
            return 0;
        }
        
        if (systemEndTime == null) {
            // System still running, calculate current duration
            return Duration.between(systemStartTime, Instant.now()).toMillis();
        }
        
        // System completed, return final duration
        return Duration.between(systemStartTime, systemEndTime).toMillis();
    }
    
    /**
     * Gets the average response time for all fires
     * @return average response time in milliseconds, or -1 if no responses
     */
    public double getAverageResponseTime() {
        if (firstResponseTimes.isEmpty()) {
            return -1;
        }
        
        long totalResponseTime = 0;
        int count = 0;
        
        for (Map.Entry<Integer, Instant> entry : firstResponseTimes.entrySet()) {
            int zoneId = entry.getKey();
            Instant responseTime = entry.getValue();
            Instant startTime = fireStartTimes.get(zoneId);
            
            if (startTime != null) {
                totalResponseTime += Duration.between(startTime, responseTime).toMillis();
                count++;
            }
        }
        
        return count > 0 ? (double) totalResponseTime / count : -1;
    }
    
    /**
     * Gets the average time to extinguish fires
     * @return average time in milliseconds, or -1 if no extinguished fires
     */
    public double getAverageExtinguishTime() {
        if (fireEndTimes.isEmpty()) {
            return -1;
        }
        
        long totalExtinguishTime = 0;
        int count = 0;
        
        for (Map.Entry<Integer, Instant> entry : fireEndTimes.entrySet()) {
            int zoneId = entry.getKey();
            Instant endTime = entry.getValue();
            Instant startTime = fireStartTimes.get(zoneId);
            
            if (startTime != null) {
                totalExtinguishTime += Duration.between(startTime, endTime).toMillis();
                count++;
            }
        }
        
        return count > 0 ? (double) totalExtinguishTime / count : -1;
    }
    
    /**
     * Gets the total number of fires detected
     * @return count of fires detected
     */
    public long getTotalFiresDetected() {
        return totalFiresDetected.get();
    }
    
    /**
     * Gets the total number of fires extinguished
     * @return count of fires extinguished
     */
    public long getTotalFiresExtinguished() {
        return totalFiresExtinguished.get();
    }
    
    /**
     * Gets the percentage of fires that have been extinguished
     * @return percentage as a value between 0 and 100
     */
    public double getFiresExtinguishedPercentage() {
        long detected = totalFiresDetected.get();
        return detected > 0 ? (totalFiresExtinguished.get() * 100.0) / detected : 0;
    }
    
    /**
     * Gets a formatted string with key metrics
     * @return a formatted string with metrics data
     */
    public String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== System Metrics =====\n");
        
        // Total fires info
        sb.append("Fires: ").append(totalFiresExtinguished.get())
          .append("/").append(totalFiresDetected.get())
          .append(" (").append(String.format("%.1f%%", getFiresExtinguishedPercentage())).append(")\n");
        
        // Time metrics
        long totalDuration = getTotalSystemDuration();
        sb.append("Total time: ").append(formatDuration(totalDuration)).append("\n");
        
        double avgResponse = getAverageResponseTime();
        if (avgResponse >= 0) {
            sb.append("Avg response: ").append(formatDuration((long)avgResponse)).append("\n");
        }
        
        double avgExtinguish = getAverageExtinguishTime();
        if (avgExtinguish >= 0) {
            sb.append("Avg extinguish: ").append(formatDuration((long)avgExtinguish)).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets a formatted HTML string with key metrics for UI display
     * @return a formatted HTML string with metrics data
     */
    public String getMetricsHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<html><div style='width: 100%;'>");
        
        // Total fires info
        html.append("<div><b>Fires:</b> ").append(totalFiresExtinguished.get())
            .append("/").append(totalFiresDetected.get())
            .append(" (").append(String.format("%.1f%%", getFiresExtinguishedPercentage())).append(")</div>");
        
        // Time metrics
        long totalDuration = getTotalSystemDuration();
        html.append("<div><b>Total time:</b> ").append(formatDuration(totalDuration)).append("</div>");
        
        double avgResponse = getAverageResponseTime();
        if (avgResponse >= 0) {
            html.append("<div><b>Avg response:</b> ").append(formatDuration((long)avgResponse)).append("</div>");
        }
        
        double avgExtinguish = getAverageExtinguishTime();
        if (avgExtinguish >= 0) {
            html.append("<div><b>Avg extinguish:</b> ").append(formatDuration((long)avgExtinguish)).append("</div>");
        }
        
        html.append("</div></html>");
        return html.toString();
    }
    
    /**
     * Formats a duration in milliseconds to a human-readable string
     * @param millis duration in milliseconds
     * @return formatted string (e.g., "1m 30s")
     */
    private String formatDuration(long millis) {
        if (millis < 0) return "N/A";
        
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}