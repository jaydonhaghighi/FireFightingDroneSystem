package controllers;

import models.FireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroneStateMachinesTest {
    private DroneStateMachines drone;
    private FireEvent fireEvent1;
    private FireEvent fireEvent2;

    @BeforeEach
    void setUp() {
        drone = new DroneStateMachines();
        fireEvent1 = new FireEvent("12:30", 5, "Wildfire", "High");
        fireEvent2 = new FireEvent("14:30", 3, "Forest Fire", "Medium");
    }

    @Test
    void testInitialStateIdle() {
        // test that the drone starts in idle state
        assertEquals("Idle", drone.getCurrentStateName());
    }

    @Test
    void testStateTransitionToEnRoute() {
        // test that drone transitions to enroute when handling a fire event
        drone.handleFireEvent(fireEvent1);
        assertEquals("EnRoute", drone.getCurrentStateName());
    }

    @Test
    void testTaskCompletion() {
        // test complete fire response cycle leading back to idle state
        drone.handleFireEvent(fireEvent1);
        drone.dropAgent();
        drone.returningBack();
        drone.taskCompleted();
        assertEquals("Idle", drone.getCurrentStateName());
    }

    @Test
    void testDroneFaultedTransition() {
        // test that a drone fault does not interrupt fire handling in iteration 2
        drone.handleFireEvent(fireEvent1);
        drone.droneFaulted();
        assertEquals("EnRoute", drone.getCurrentStateName());
    }

    @Test
    void testMultipleFireEventQueue() {
        // test if fire event queue works correctly and drone processes them in order
        drone.scheduleFireEvent(fireEvent1);
        drone.scheduleFireEvent(fireEvent2);

        assertEquals("EnRoute", drone.getCurrentStateName());

        drone.dropAgent();
        drone.returningBack();
        drone.taskCompleted();

        assertEquals("EnRoute", drone.getCurrentStateName());
    }

    @Test
    void testDroneOperationCycle() {
        // test the full operation cycle from scheduling to task completion
        drone.scheduleFireEvent(fireEvent1);
        assertEquals("EnRoute", drone.getCurrentStateName());

        drone.dropAgent();
        assertEquals("droppingAgent", drone.getCurrentStateName());

        drone.returningBack();
        assertEquals("ArrivedToBase", drone.getCurrentStateName());

        drone.taskCompleted();
        assertEquals("Idle", drone.getCurrentStateName());
    }
}
