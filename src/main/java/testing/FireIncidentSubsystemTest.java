package testing;

import controllers.FireIncidentSubsystem;
import controllers.Scheduler;
import models.FireEvent;
import org.junit.Test;
import org.w3c.dom.events.Event;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;

public class FireIncidentSubsystemTest
{


   @Test
   public void test() throws NoSuchFieldException, IllegalAccessException, InterruptedException, IOException {


       String testTextFile = "src/main/resources/test_fire_events.txt";

       // make new text file to use as our Input File
       try (BufferedWriter writer = new BufferedWriter(new FileWriter(testTextFile))) {
           writer.write("11:00 1 FIRE_DETECTED High\n");
       }

       Scheduler sched = new Scheduler();

       Thread fireSystem = new Thread(new FireIncidentSubsystem(sched,testTextFile), "FireIncidentSubsystem");

       Thread fireSystemThread = new Thread(fireSystem, "FireIncidentSubsystem");

       fireSystemThread.start();

       fireSystemThread.join(5000);

       Field queueField = Scheduler.class.getDeclaredField("fireIncidentQueue");
       queueField.setAccessible(true);

       // Get the event that was sent to the Scheduler
       BlockingQueue<FireEvent> queueEvent = (BlockingQueue<FireEvent>) queueField.get(sched);
       FireEvent event = queueEvent.poll();

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

       // Check to see if the attributes of the Event that was sent matches the expected values on the "test_fire_events.txt" text file
       assertEquals("11:00", time);
       assertEquals(1, zoneID);
       assertEquals("FIRE_DETECTED", eventType);
       assertEquals("High", severity);

   }

}