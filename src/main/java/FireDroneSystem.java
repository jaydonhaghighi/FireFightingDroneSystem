import controllers.DroneSubsystem;
import controllers.FireIncidentSubsystem;
import controllers.Scheduler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The FireDroneSystem initializes and runs the fire incident response system.
 * It creates a scheduler, a fire incident subsystem, and a pool of drone subsystems
 * to process fire events efficiently.
 */
public class FireDroneSystem {
    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler();
        String inputFile = "src/main/resources/fire_events.txt";

        Thread schedulerThread = new Thread(scheduler);
        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(scheduler, inputFile));

        int numDrones = 1;
        ExecutorService dronePool = Executors.newFixedThreadPool(numDrones);

        schedulerThread.start();
        fireIncidentThread.start();
        for (int i = 0; i < numDrones; i++) {
            dronePool.execute(new DroneSubsystem(scheduler));
        }

        dronePool.shutdown();
    }
}