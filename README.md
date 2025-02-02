# Fire Drone System (Iteration #1)

## Overview
- `FireIncidentSubsystem`: Reads fire events from a file and sends them to the `Scheduler`.
- `Scheduler`: Manages event processing and assigns drone tasks.
- `DroneSubsystem`: Fetches tasks from the `Scheduler`, simulates response, and sends it back.
- `FireEvent`: Represents a fire incident.
- `FireDroneSystem`: The main entry point that starts all subsystems.

---

## Team Contributions
- `Brendan`: Scheduler
- `Abolarinwa`:
- `Zeena`: Tests for FireIncidentSubsystem, Sequence Diagrams
- `Jaydon`:
- `Raiqah`: Tests for FireEvent and DroneSubsystem 
- `Leen`: Fire Incident Subsystem

---

## Class Descriptions

### FireEvent
Represents a fire incident with details like time, location (zoneID), type, and severity.  
Used by `FireIncidentSubsystem`, `Scheduler`, and `DroneSubsystem` to process events.

---

### FireIncidentSubsystem
Reads fire events from a file and sends them to the `Scheduler`.

- Sends events to `Scheduler`
- Receives responses from `Scheduler`

Methods:
- `run()`: Reads `fire_events.txt`, creates `FireEvent` objects, and sends them to the `Scheduler`.
- Continuously waits for a response from the `Scheduler`.

---

### DroneSubsystem
Simulates drone operations that process fire events.

- Fetches tasks from `Scheduler`
- Sends responses to `Scheduler`

Methods:
- `run()`: Fetches tasks from `Scheduler`, simulates drone response, and sends it back.

---

### Scheduler
Manages fire event processing and assigns tasks to drones.

- Receives fire events from `FireIncidentSubsystem`
- Dispatches tasks to `DroneSubsystem`
- Receives drone responses from `DroneSubsystem`
- Sends responses to `FireIncidentSubsystem`

Methods:
- `receiveFireEvent(event)`: Stores events in a queue.
- `getDroneTask()`: Drones fetch tasks from this queue.
- `receiveDroneResponse(response)`: Collects responses from drones.
- `getResponseForFireIncidentSubsystem()`: Sends responses back to `FireIncidentSubsystem`.

---

### FireDroneSystem (Main Class)
The entry point that initializes and starts all subsystems.

- Starts `Scheduler`, `FireIncidentSubsystem`, and multiple `DroneSubsystem` instances.

Methods:
- `main()`: Creates and starts threads for the `Scheduler`, `FireIncidentSubsystem`, and drones.

---

### Compile the program

Navigate to the project's root directory and run:

```sh
javac -d bin -sourcepath src src/controllers/*.java src/models/*.java src/FireDroneSystem.java
```

This will compile all `.java` files into the `bin/` directory.

---

### Run the program

```sh
java -cp bin FireDroneSystem
```

This starts the simulation, where:
- Fire events are read from a file and processed
- Drones receive and handle fire tasks
- Responses are sent back to the `FireIncidentSubsystem`

---

## Example Fire Events File (fire_events.txt)

```txt
14:03:15 3 FIRE_DETECTED High
14:10:00 7 DRONE_REQUEST Moderate
```

Each line represents:
- Time
- Zone ID
- Event Type
- Severity

---

## Expected Console Output

```
[FireIncidentSubsystem] Sent event: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[Scheduler] Received fire event: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[FireIncidentSubsystem] Sent event: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[Scheduler] Received fire event: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[Scheduler] Dispatching task to DroneSubsystem: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[Scheduler] Dispatching task to DroneSubsystem: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[DroneSubsystem] Received task: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[DroneSubsystem] Completed task: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[Scheduler] Received response from DroneSubsystem: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[DroneSubsystem] Received task: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[Scheduler] Sending response to FireIncidentSubsystem: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[FireIncidentSubsystem] Received response: Time: 14:03:15, Zone: 3, Event: FIRE_DETECTED, Severity: High
[DroneSubsystem] Completed task: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[Scheduler] Received response from DroneSubsystem: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[Scheduler] Sending response to FireIncidentSubsystem: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate
[FireIncidentSubsystem] Received response: Time: 14:10:00, Zone: 7, Event: DRONE_REQUEST, Severity: Moderate  
```
