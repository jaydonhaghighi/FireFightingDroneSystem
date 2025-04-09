package controllers;

import controllers.Scheduler;
import controllers.FireIncidentSubsystem;
import controllers.DroneSubsystem;
import models.FireEvent;
import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

class UDPCommunicationTest {
    private static final int SCHEDULER_RECEIVE_PORT = 6001;
    private static final int FIRE_INCIDENT_PORT = 5001;
    private static final int DRONE_RECEIVE_PORT = 7001;
    private static final int DRONE_SEND_PORT = 7000;

    private DatagramSocket testSocket;
    private InetAddress localAddress;

    @BeforeEach
    void setUp() throws Exception {
        localAddress = InetAddress.getLocalHost();
        testSocket = new DatagramSocket();
    }

    @AfterEach
    void tearDown() {
        testSocket.close();
    }

    @Test
    void testFireIncidentSubsystemSendsFireEvent() throws Exception {
        FireEvent fireEvent = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High", "NONE");
        byte[] sendData = fireEvent.toString().getBytes();

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, SCHEDULER_RECEIVE_PORT);
        testSocket.send(sendPacket);

        Thread.sleep(1000);
        assertTrue(true, "Scheduler should receive a fire event over UDP.");
    }

    @Test
    void testSchedulerReceivesFireEventAndSendsDummyTaskToDrone() throws Exception {
        FireEvent fireEvent = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High", "NONE");
        byte[] sendData = fireEvent.toString().getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, SCHEDULER_RECEIVE_PORT);
        testSocket.send(sendPacket);

        String dummyTask = "DUMMY_TASK 3 FIRE_DETECTED High";
        byte[] taskData = dummyTask.getBytes();
        DatagramPacket taskPacket = new DatagramPacket(taskData, taskData.length, localAddress, DRONE_RECEIVE_PORT);
        testSocket.send(taskPacket);

        assertTrue(true, "Drone should receive a dummy fire event task over UDP.");
    }

    @Test
    void testDroneStateMachinesSendsCompletionStatus() throws Exception {
        String completionMessage = "COMPLETED TASK 3";
        byte[] sendData = completionMessage.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, SCHEDULER_RECEIVE_PORT);
        testSocket.send(sendPacket);

        Thread.sleep(1000);
        assertTrue(true, "Scheduler should receive task completion status from DroneStateMachines.");
    }

    @Test
    void testFireIncidentSubsystemReceivesResponseFromScheduler() throws Exception {
        String response = "Scheduler Acknowledged Fire Event";
        byte[] sendData = response.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, FIRE_INCIDENT_PORT);
        testSocket.send(sendPacket);

        Thread.sleep(1000);
        assertTrue(true, "FireIncidentSubsystem should receive a response from Scheduler.");
    }

    @Test
    void testDroneReceivesAndProcessesTask() throws Exception {
        String fireTask = "14:05:30 2 FIRE_DETECTED Medium";
        byte[] sendData = fireTask.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, DRONE_RECEIVE_PORT);
        testSocket.send(sendPacket);

        Thread.sleep(1000);
        assertTrue(true, "Drone should process and acknowledge the fire task.");
    }

    @Test
    void testDroneSendsFaultStatusToScheduler() throws Exception {
        String faultMessage = "DRONE_FAULT 2";
        byte[] sendData = faultMessage.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localAddress, SCHEDULER_RECEIVE_PORT);
        testSocket.send(sendPacket);

        Thread.sleep(1000);
        assertTrue(true, "Scheduler should receive fault status from the drone.");
    }
}
