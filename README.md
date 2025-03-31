# Fire Fighting Drone System (Iteration #4)

## Overview

The Fire Fighting Drone System is a sophisticated simulation of a multi-drone coordination system for detecting and responding to fire incidents across different zones. The system uses a distributed architecture with several subsystems communicating via UDP, where multiple drones can simultaneously respond to fire events based on their availability, location, and workload balance.

Fire events are detected by the FireIncidentSubsystem, coordinated by a central Scheduler, and handled by multiple DroneSubsystem instances. Each drone operates autonomously through a state machine, moving through states like Idle, TakingOff, EnRoute, AtLocation, Extinguishing, ReturningToBase, and ArrivedToBase. The system features spatial awareness with location tracking, zone definitions, and intelligent drone assignment.

---

## Key Features

- **Multi-Drone Support**: Manages and coordinates multiple drones simultaneously
- **Spatial Awareness**: Tracks drone locations and defines geographic zones
- **Intelligent Drone Assignment**: Selects optimal drone based on proximity, workload, and availability
- **UDP Communication**: Reliable message passing between subsystems
- **Zone Configuration**: Reads zone definitions from an external file
- **Realistic Delays**: Simulates actual drone operation timings
- **State Machine Logic**: Robust state transitions for each drone
- **Workload Balancing**: Ensures even distribution of tasks across the drone fleet
- **Enhanced Console Output**: Clear, color-coded status information with minimal redundancy
- **Fault Detection & Recovery**: Detects movement timeouts, nozzle jams, door stuck conditions and sensor failures
- **Hardware Fault Simulation**: Simulates critical drone hardware failures
- **Timing Diagrams**: Generates comprehensive timing reports for drone operations
- **Multi-Drone Fire Response**: Dispatches multiple drones for higher severity fires

---
Work Breakdown:

Brendan: Fault injecttion and class diagrams
Bola: Nozzle Jam
Leen: Packet Loss
Raiqah: Drone Stuck Midair
Zeena: Drone Stuck Midair
Jaydon: Fault Injection


## Class Descriptions

### Models
- **FireEvent**: Represents a fire incident with time, zone, severity, and assigned drone
- **Location**: Represents a 2D coordinate with distance calculation and path detection
- **Zone**: Defines a geographic area with boundaries and fire status tracking
- **DroneStatus**: Tracks a drone's current state, location, task, and mission history
- **DroneSpecifications**: Defines drone capabilities like speed, acceleration, carrying capacity, and flow rate

### Controllers
- **FireIncidentSubsystem**: Reads fire events from input file and sends to Scheduler
- **Scheduler**: Central coordinator that receives fire events and assigns them to drones
- **DroneManager**: Manages the drone fleet, zone mapping, and drone selection algorithms
- **DroneSubsystem**: Handles individual drone behavior including state transitions and movement

---

## Compiling and Running the Program

From the project's root directory:

### Compile:
```sh
javac -d bin -sourcepath src src/main/java/controllers/*.java src/main/java/models/*.java
```

### Run (execute each in a separate terminal):

```sh
# Start the Scheduler first
java -cp bin controllers.Scheduler

# Start multiple drone instances
java -cp bin controllers.DroneSubsystem drone1 0 0
java -cp bin controllers.DroneSubsystem drone2 10 10
java -cp bin controllers.DroneSubsystem drone3 20 20

# Start the FireIncidentSubsystem last
java -cp bin controllers.FireIncidentSubsystem
```

---

## Configuration Files

### Fire Events (`fire_events.txt`)

The system reads fire events from `src/main/resources/fire_events.txt` with the format:
```
# Regular fire events with no errors
14:03:15 1 FIRE_DETECTED High
14:12:00 2 FIRE_DETECTED Moderate
14:15:00 3 FIRE_DETECTED Low

# Fire events with specific errors
14:08:30 4 FIRE_DETECTED Moderate DRONE_STUCK
14:10:45 6 FIRE_DETECTED High NOZZLE_JAM

# Fire event with random error injection
14:03:15 5 FIRE_DETECTED High ERROR
```

Each line contains:
- Time (HH:MM:SS)
- Zone ID
- Event type
- Severity (High, Moderate, Low)
- Optional error type (DRONE_STUCK, NOZZLE_JAM, DOOR_STUCK, ARRIVAL_SENSOR_FAILED, or ERROR for random error)

### Zones (`zones.txt`)

Zone definitions are read from `src/main/resources/zones.txt` with the format:
```
# ZoneID x1 y1 x2 y2
1 0 0 10 10
2 10 0 20 10
3 20 0 30 10
```

Each line defines a zone with:
- Zone ID
- Top-left corner coordinates (x1, y1)
- Bottom-right corner coordinates (x2, y2)

---

## Example Console Output

### Scheduler:
```
[SCHEDULER] Registered drone drone1 on port 7101
[SCHEDULER] Registered drone drone2 on port 7201
[SCHEDULER] Registered drone drone3 on port 7301
[SCHEDULER] Initialized at (0,0)
[SCHEDULER] Waiting for drones to register...
[STANDBY] System monitoring
[SCHEDULER] Starting to process messages
[STATUS] No drones registered
[SYSTEM MAP] (10 zones, 0 drones)

[SCHEDULER] Received packet: drone1 Idle 0 0
[SCHEDULER] Identified drone status update from: drone1
[SCHEDULER] Registered new drone: drone1
[SCHEDULER] Updated drone status: drone1 at (0,0) in state Idle

[ALERT] High fire in Zone 1 at (5,5)
[ASSIGNED] drone1 to Zone 1 (5 units away, 0 previous missions)
[SCHEDULER] Sending fire assignment to Drone drone1: 14:03:15 1 FIRE_DETECTED High drone1
```

### DroneSubsystem:
```
[DRONE] drone1 initialized at (0,0)
[DRONE] Registered with scheduler
[DRONE] Current state: Idle

[DRONE drone1] Received packet: 14:03:15 1 FIRE_DETECTED High
[DRONE] Processing fire event: 14:03:15 1 FIRE_DETECTED High
[DRONE] State before: IDLE
[DRONE] State after: EN ROUTE

DRONE drone1: Mission start to Zone 1 - High fire
DRONE drone1: Flying to zone (1050 meters, 10.5s, normal speed, max speed: 35.0 km/h)
DRONE drone1: Flight 50% complete
DRONE drone1: Successfully dropped agent at Zone 1
DRONE drone1: Fighting fire in Zone 1 (8s, flow rate: 2.0 L/s)
DRONE drone1: Fire extinguished in Zone 1 (Drops: 1/3)
DRONE drone1: Returning to base after mission
DRONE drone1: Flying to base (1050 meters, 10.5s, normal speed, max speed: 35.0 km/h)
DRONE drone1: Flight 50% complete
DRONE drone1: Mission complete, ready for next assignment

# Example with fault:
[DRONE drone3] Received packet: 14:10:45 6 FIRE_DETECTED High NOZZLE_JAM
[DRONE drone3] Error injected from input: NOZZLE_JAM
[DRONE drone3] Hard fault detected, aborting mission
[DRONE drone3] ERROR DETECTED: NOZZLE_JAM
[DRONE drone3] HARD FAULT: Shutting down drone

# Timing diagram at end of program:
DRONE drone1 CYCLE: running 10.5s, running 8.0s, running 10.5s, idle 4.8s
DRONE drone2 CYCLE: running 12.3s, preempted (fault) 3.2s, idle 5.0s
DRONE drone3 CYCLE: idle 2.0s, preempted (fault) 10.5s
```


