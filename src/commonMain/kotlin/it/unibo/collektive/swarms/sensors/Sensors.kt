package it.unibo.collektive.swarms.sensors

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.Vector2D

// What a device perceives of space: the library reads its sensors only through these interfaces, so that the same
// program runs on a simulator or on a real robot, whichever implements them.

/** A sensor of the global coordinates of the device (e.g., GPS or a motion-capture system). */
fun interface LocationSensor {
    /** The coordinates of the device in the global frame. */
    fun coordinates(): Position
}

/** A sensor of the displacement (dx, dy) of the neighbors, in a frame aligned with the global one, without positions. */
fun interface RelativePositionSensor {
    /** The vector going from [neighbor] to this device (`self - neighbor`). */
    fun relativeTo(neighbor: Int): Vector2D
}

/** A range-only sensor: the distance from each neighbor, without bearing (e.g., UWB ranging). */
fun interface DistanceSensor {
    /** The distance from [neighbor]. */
    fun distanceTo(neighbor: Int): Double
}
