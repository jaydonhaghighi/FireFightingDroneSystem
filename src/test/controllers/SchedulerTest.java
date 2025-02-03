package controllers;

import models.FireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler();
    }

    @Test
    void testReceiveFireEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        scheduler.receiveFireEvent(event);

        assertEquals(1, scheduler.getFireIncidentQueueSize());
    }

    @Test
    void testProcessFireEvents() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        FireEvent event2 = new FireEvent("14:10:00", 7, "DRONE_REQUEST", "Moderate");
        FireEvent event3 = new FireEvent("14:15:00", 8, "FIRE_DETECTED", "Low");

        scheduler.receiveFireEvent(event1);
        scheduler.receiveFireEvent(event2);
        scheduler.receiveFireEvent(event3);

        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(1000);
        schedulerThread.interrupt();
        schedulerThread.join();

        assertEquals(0, scheduler.getFireIncidentQueueSize());
        assertEquals(3, scheduler.getDroneTaskQueueSize());
    }

    @Test
    void testReceiveDroneResponse() throws InterruptedException {
        FireEvent response = new FireEvent("14:10:00", 7, "DRONE_REQUEST", "Moderate");
        scheduler.receiveDroneResponse(response);

        assertEquals(1, scheduler.getDroneResponseQueueSize());
    }
}
