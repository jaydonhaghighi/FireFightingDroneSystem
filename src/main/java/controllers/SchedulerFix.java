package controllers;

import models.DroneStatus;
import models.FireEvent;
import models.Location;
import models.Zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A utility class to fix scheduling issues
 */
public class SchedulerFix {
    
    /**
     * Applies fixes to the scheduler
     * 
     * @param scheduler The scheduler to fix
     */
    public static void applyFix(Scheduler scheduler) {
        // First, run diagnostics to show the current state
        System.out.println("\n\n" + SchedulerColors.BOLD_WHITE + "==== APPLYING SCHEDULER FIX ====" + SchedulerColors.RESET);
        
        // Check for active fires
        Map<Integer, Zone> zones = scheduler.getDroneManager().getAllZones();
        List<Zone> activeFireZones = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.hasFire()) {
                activeFireZones.add(zone);
                System.out.println(SchedulerColors.BOLD_RED + "[FIX] Active fire in Zone " + zone.getId() +
                                 " with severity " + zone.getSeverity() + SchedulerColors.RESET);
            }
        }
        
        // Check for available drones
        Collection<DroneStatus> drones = scheduler.getDroneManager().getAllDrones();
        List<DroneStatus> availableDrones = new ArrayList<>();
        for (DroneStatus drone : drones) {
            if (isReallyAvailable(drone)) {
                availableDrones.add(drone);
                System.out.println(SchedulerColors.BOLD_LIME + "[FIX] Available drone: " + drone.getDroneId() +
                                 " at " + drone.getCurrentLocation() + " in state " + drone.getState() + SchedulerColors.RESET);
            }
        }
        
        // If there are both active fires and available drones, create events
        if (!activeFireZones.isEmpty() && !availableDrones.isEmpty()) {
            System.out.println(SchedulerColors.BOLD_YELLOW + "[FIX] Found " + activeFireZones.size() + 
                             " active fires and " + availableDrones.size() + " available drones - creating events" + 
                             SchedulerColors.RESET);
            
            // Create fire events for each active zone
            for (Zone zone : activeFireZones) {
                // Create a fire event for this active zone
                FireEvent event = new FireEvent(
                    String.format("%02d:%02d:%02d", new Date().getHours(), new Date().getMinutes(), new Date().getSeconds()),
                    zone.getId(),
                    "FIRE",
                    zone.getSeverity(),
                    "NONE"
                );
                
                // Add this event to the scheduler queue
                scheduler.addEmergencyEvent(event);
                
                System.out.println(SchedulerColors.BOLD_YELLOW + "[FIX] Added emergency fire event for Zone " + 
                                 zone.getId() + " with severity " + zone.getSeverity() + SchedulerColors.RESET);
            }
        }
        
        System.out.println(SchedulerColors.BOLD_WHITE + "==== FIX APPLIED ====" + SchedulerColors.RESET + "\n\n");
    }
    
    /**
     * Checks if a drone is really available (double-checking the logic)
     * 
     * @param drone The drone to check
     * @return true if the drone is available
     */
    private static boolean isReallyAvailable(DroneStatus drone) {
        // A drone is available if:
        // 1. It's in the Idle state
        // 2. It doesn't have a hard fault
        // 3. It doesn't have a current task
        boolean isIdleState = "IDLE".equalsIgnoreCase(drone.getState()) || "Idle".equalsIgnoreCase(drone.getState());
        boolean isNotFaulted = drone.getErrorType() == FireEvent.ErrorType.NONE;
        boolean hasNoTask = drone.getCurrentTask() == null;
        
        return isIdleState && isNotFaulted && hasNoTask;
    }
}