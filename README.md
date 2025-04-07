# Fire Fighting Drone System (Iteration #5)

## Overview

The Fire Fighting Drone System is a sophisticated simulation of a multi-drone coordination system for detecting and responding to fire incidents across different zones. The system uses a distributed architecture with several subsystems communicating via UDP, where multiple drones can simultaneously respond to fire events based on their availability, location, and workload balance.

Fire events are detected by the FireIncidentSubsystem, coordinated by a central Scheduler, and handled by multiple DroneSubsystem instances. Each drone operates autonomously through a state machine, moving through states like Idle, TakingOff, EnRoute, AtLocation, Extinguishing, ReturningToBase, and ArrivedToBase. The system features spatial awareness with location tracking, zone definitions, and intelligent drone assignment.

---

## Key Features

- **User Interface**: Simple user interface that allows an operator to monitor the system remotely
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
## Team Contributions

- `Brendan:` Updated State Machine diagram to include fault states, Re-implemented error injection, Debugging
- `Abolarinwa:` Metrics implementation
- `Zeena:`Updated sequence diagram and wrote out testing instructions
- `Jaydon:` UI and metrics implementation
- `Raiqah:` Updated UML Class diagram with new changes
- `Leen:` Updated sequence diagram and wrote out testing instructions


---
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
- **DronVisualization**: Responsible for all of the user-interface control/interaction in the system

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

### FireIncidentSystem:
```
[SYSTEM] Ready to send fire alerts
FIRE ALERT: 14:03:33 12 FIRE_DETECTED High NONE
Next fire in 1s
FIRE ALERT: 14:15:00 3 FIRE_DETECTED Low NOZZLE_JAM
Next fire in 1s
FIRE ALERT: 14:14:03 2 FIRE_DETECTED Moderate NONE
Next fire in 1s
FIRE ALERT: 14:03:06 1 FIRE_DETECTED High NONE
Next fire in 1s
FIRE ALERT: 14:03:09 8 FIRE_DETECTED High NONE
```
