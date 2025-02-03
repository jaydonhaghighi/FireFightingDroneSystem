package controllers;

import models.FireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

public class FireIncidentSubsystemTest
{


    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException, InterruptedException, IOException {


        FireEvent event = getFireEvent();

        Field timeEvent = FireEvent.class.getDeclaredField("time");
        Field zoneEvent = FireEvent.class.getDeclaredField("zoneID");
        Field typeEvent = FireEvent.class.getDeclaredField("eventType");
        Field severityEvent = FireEvent.class.getDeclaredField("severity");

        timeEvent.setAccessible(true);
        zoneEvent.setAccessible(true);
        typeEvent.setAccessible(true);
        severityEvent.setAccessible(true);

        String time = (String) timeEvent.get(event);
        int zoneID = (int) zoneEvent.get(event);
        String eventType = (String) typeEvent.get(event);
        String severity = (String) severityEvent.get(event);

        // check to see if the attributes of the Event that was sent matches the expected values on the "fire_events.txt" text file
        assertEquals("11:00", time);
        assertEquals(1, zoneID);
        assertEquals("FIRE_DETECTED", eventType);
        assertEquals("High", severity);

    }

    private static FireEvent getFireEvent() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        String testTextFile = "src/main/resources/fire_events.txt";

        // make new text file to use as our Input File
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(testTextFile))) {
            writer.write("11:00 1 FIRE_DETECTED High\n");
        }

        Scheduler sched = new Scheduler();

        BlockingQueue<FireEvent> queueEvent = getFireEvents(sched, testTextFile);
        return queueEvent.poll();
    }

    private static BlockingQueue<FireEvent> getFireEvents(Scheduler sched, String testTextFile) throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        Thread fireSystem = new Thread(new FireIncidentSubsystem(sched, testTextFile), "FireIncidentSubsystem");

        Thread fireSystemThread = new Thread(fireSystem, "FireIncidentSubsystem");

        fireSystemThread.start();

        fireSystemThread.join(5000);

        Field queueField = Scheduler.class.getDeclaredField("fireIncidentQueue");
        queueField.setAccessible(true);

        // Get the event that was sent to the Scheduler
        return (BlockingQueue<FireEvent>) queueField.get(sched);
    }

}
