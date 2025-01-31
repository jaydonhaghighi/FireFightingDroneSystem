package testing;

import controllers.DroneSubsystem;
import controllers.Scheduler;
import models.FireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class DroneSubsystemTest extends junit.framework.TestCase{

    //declare objects
    private Scheduler testScheduler;
    private DroneSubsystem droneSubsystem;
    private Thread thread;


    @BeforeEach
    void setup() {

        //initialize all objects
        testScheduler = new Scheduler();
        droneSubsystem = new DroneSubsystem(testScheduler);

        //create scheduler thread
       Thread thread1 = new Thread(testScheduler);
       thread1.start();
    }

    @Test
    void testDroneRecieveTask() throws InterruptedException {

        //create a FireEvent object
        FireEvent fireEvent = new FireEvent("14:10:00",7,"DRONE_REQUEST","Moderate");

        //send FireEvent to scheduler
        testScheduler.receiveFireEvent(fireEvent);

        //create drone thread and start it
        thread = new Thread(droneSubsystem);
        thread.start();

        Thread.sleep(100);

        //check if task is not null
        assertNotNull(droneSubsystem.getLastTask());

        //check if created FireEvent is the task that drone recieved
        assertEquals(fireEvent, droneSubsystem.getLastTask());

        //FireEvent not sent to drone
        FireEvent f1 = new FireEvent("14:03:15",3,"FIRE_DETECTED","High");

        //check if drone task is not equal to the event that was not sent
        assertFalse(f1.toString().contentEquals(droneSubsystem.getLastTask().toString()));

    }

}
