# Collektive Swarms

A [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library for **swarm shape formation**
with aggregate programming on [Collektive](https://github.com/Collektive/collektive).

A formation is split in three independent concerns, each one a strategy you can swap at runtime:

| Concern | Question | Built-in strategies |
|---|---|---|
| **Shape** (`ShapeTranslation`) | *what* shape to form | `global`, `centeredAtFrame`, `fromLeader` |
| **Positioning** (`Positioning`) | *where* each device is | `global` (GPS), `leaderRelative` (displacements), `anchorBased` (range-only) |
| **Control** (`FormationControl`) | *how* devices spread in the shape | `lattice` (Lloyd coverage clipped to the shape) |
| **Actuation** (`Actuation`) | how a velocity becomes a command | `aligned`, `selfAligned` (unknown heading) |

Shapes are **Signed Distance Functions** (SDF), built and composed with a small DSL: see [docs/SDF.md](docs/SDF.md).

## Installation

```kotlin
dependencies {
    implementation("it.unibo.collektive:collektive-swarms:<version>")
}
```

The project using it must apply the Collektive compiler plugin (`it.unibo.collektive.collektive-plugin`).

## Usage

The library reads the sensors of the device through `LocationSensor`, `RelativePositionSensor` and `DistanceSensor`,
and returns the velocity to apply: it never moves the device, so the same program runs on a simulator or on a robot.

```kotlin
val shapes = mapOf(
    "star" to Star(center = Position.origin, radius = 30.0, pointCount = 5),
    "crescent" to (Circle(Position.origin, 30.0) - Circle(Position(20.0, 0.0), 30.0)),
)

// Every device knows its global position: form the star where it is defined.
fun Aggregate<Int>.globalStar(gps: LocationSensor): FormationStep = swarmFormation(
    positioning = Positioning.global(gps),
    translateShape = ShapeTranslation.global(shapes.getValue("star")),
)

// Devices only sense the distances from their neighbors and do not know their heading:
// three elected anchors fix a frame, and the leader decides the shape (change `shape` to reshape the swarm).
fun Aggregate<Int>.rangeOnly(ranging: DistanceSensor, shape: String, random: Random): FormationStep = swarmFormation(
    positioning = Positioning.anchorBased(ranging),
    translateShape = ShapeTranslation.fromLeader(shapes, shape),
    control = FormationControl.lattice(LatticeParameters(repulsion = RepulsionLaw.Lloyd(gain = 1.0, reach = 10.0))),
    actuate = Actuation.selfAligned(random = random),
)
```

Each round returns a `FormationStep`: the `velocity` to apply, the reference `frame` of the device, and the `shape`
placed in that frame (e.g., `step.frame.position?.let(step.shape::invoke)` is the distance of the device from the
shape).
The parameters (`LatticeParameters`, `AnchorParameters`, `SelfAlignmentParameters`) default to the values of the
experiments in the paper; the reach of the Lloyd cells should be about half of the communication range.
