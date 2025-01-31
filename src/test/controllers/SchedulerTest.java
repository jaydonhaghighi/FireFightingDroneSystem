package controllers;

import models.FireEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    private Scheduler scheduler;
    private List<Thread> threads;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler();
        threads = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        // Stop all threads created during the test
        for (Thread thread : threads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt(); // Interrupt the thread
            }
        }

        // Clear any shared resources (e.g., queues)
        scheduler.clearQueues(); // Add a method in Scheduler to clear queues
    }

    @Test
    void testReceiveFireEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        scheduler.receiveFireEvent(event);

        // Verify that the event is added to the fireIncidentQueue
        assertEquals(1, scheduler.getFireIncidentQueueSize());
    }

    @Test
    void testProcessFireEvents() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        scheduler.receiveFireEvent(event);

        FireEvent task = scheduler.getDroneTask();

        // Verify that the task is correctly retrieved
        assertNotNull(task);
        assertEquals(event, task);

        assertEquals(0, scheduler.getFireIncidentQueueSize());
        assertEquals(0, scheduler.getDroneTaskQueueSize());
    }

    @Test
    void testReceiveDroneResponse() throws InterruptedException {
        FireEvent response = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        scheduler.receiveDroneResponse(response);

        // Verify that the response is added to the droneResponseQueue
        assertEquals(1, scheduler.getDroneResponseQueueSize());
    }

    @Test
    void testProcessDroneResponses() throws InterruptedException {
        FireEvent response = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        scheduler.receiveDroneResponse(response);

        // Simulate the FireIncidentSubsystem fetching the response
        FireEvent receivedResponse = scheduler.getResponseForFireIncidentSubsystem();

        // Verify the results
        assertNotNull(receivedResponse);
        assertEquals(response, receivedResponse);

        // Verify that the droneResponseQueue is empty
        assertEquals(0, scheduler.getDroneResponseQueueSize());

        // Verify that the fireIncidentResponseQueue is empty
        assertEquals(0, scheduler.getFireIncidentResponseQueueSize());
    }


    @Test
    void testGetDroneTask() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");

        //Simulate receiving a fire event
        scheduler.receiveFireEvent(event);

        // Simulate the DroneSubsystem fetching the task
        FireEvent task = scheduler.getDroneTask();


        assertNotNull(task, "The task should not be null");
        assertEquals(event, task, "The retrieved task should match the sent event");

        // Verify that the fireIncidentQueue is empty
        assertEquals(0, scheduler.getFireIncidentQueueSize(), "The fireIncidentQueue should be empty");

        // Verify that the droneTaskQueue is empty
        assertEquals(0, scheduler.getDroneTaskQueueSize(), "The droneTaskQueue should be empty");
    }

    @Test
    void testGetResponseForFireIncidentSubsystem() throws InterruptedException {
        FireEvent response = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");

        //Simulate receiving a drone response
        scheduler.receiveDroneResponse(response);

        // Simulate the scheduler processing the response
        // Simulate the FireIncidentSubsystem fetching the response
        FireEvent receivedResponse = scheduler.getResponseForFireIncidentSubsystem();

        assertNotNull(receivedResponse, "The response should not be null");
        assertEquals(response, receivedResponse, "The received response should match the sent response");

        // Verify that the droneResponseQueue is empty after processing
        assertEquals(0, scheduler.getDroneResponseQueueSize(), "The droneResponseQueue should be empty");

        // Verify that the fireIncidentResponseQueue is empty because the response was fetched
        assertEquals(0, scheduler.getFireIncidentResponseQueueSize(), "The fireIncidentResponseQueue should be empty");
    }

}