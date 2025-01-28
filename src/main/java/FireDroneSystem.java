import controllers.DroneSubsystem;
import controllers.FireIncidentSubsystem;
import controllers.Scheduler;

public class FireDroneSystem {
    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler();
        String inputFile = "src/main/resources/fire_events.txt";

        // create threads
        Thread schedulerThread = new Thread(scheduler);
        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(scheduler, inputFile));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler));

        // start threads
        schedulerThread.start();
        fireIncidentThread.start();
        droneThread.start();

        try {
            // wait for threads to finish
            schedulerThread.join();
            fireIncidentThread.join();
            droneThread.join();
        } catch (InterruptedException e) {
            System.err.println("[Main] Error: " + e.getMessage());
        }
    }
}