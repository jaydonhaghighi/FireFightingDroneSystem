# Fire Fighting Drone System (Iteration #3)

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

---

## Class Descriptions

### Models
- **FireEvent**: Represents a fire incident with time, zone, severity, and assigned drone
- **Location**: Represents a 2D coordinate with distance calculation and path detection
- **Zone**: Defines a geographic area with boundaries and fire status tracking
- **DroneStatus**: Tracks a drone's current state, location, task, and mission history

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
14:03:15 1 FIRE_DETECTED High
14:12:00 2 FIRE_DETECTED Moderate
14:15:00 3 FIRE_DETECTED Low
```

Each line contains:
- Time (HH:MM:SS)
- Zone ID
- Event type
- Severity (High, Moderate, Low)

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

[DRONE] Received packet: 14:03:15 1 FIRE_DETECTED High drone1
[DRONE] Processing fire event: 14:03:15 1 FIRE_DETECTED High
[DRONE] Transitioning from Idle to TakingOff
[DRONE] Taking off...

[DRONE] Transitioning from TakingOff to EnRoute
[DRONE] En route to fire at zone 1
[DRONE] Moving to location (5,5)...
[DRONE] Current position: (3,3)
[DRONE] Current position: (5,5)

[DRONE] Transitioning from EnRoute to AtLocation
[DRONE] Arrived at fire location
[DRONE] Starting fire assessment...

[DRONE] Transitioning from AtLocation to Extinguishing
[DRONE] Extinguishing fire...
[DRONE] High severity fire - operation will take 8 seconds

[DRONE] Transitioning from Extinguishing to ReturningToBase
[DRONE] Fire extinguished, returning to base
[DRONE] Moving to base at (0,0)...
[DRONE] Current position: (3,3)
[DRONE] Current position: (0,0)

[DRONE] Transitioning from ReturningToBase to ArrivedToBase
[DRONE] Arrived at base
[DRONE] Preparing for next mission...

[DRONE] Transitioning from ArrivedToBase to Idle
[DRONE] Ready for next mission
```


