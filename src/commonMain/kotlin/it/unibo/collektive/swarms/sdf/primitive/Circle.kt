package it.unibo.collektive.swarms.sdf.primitive

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.euclideanDistanceTo
import it.unibo.collektive.swarms.sdf.SDF
/**
 * Represents a 2D Signed Distance Field (SDF) of a disk.
 *
 * @property center The (X, Y) coordinates of the circle's center.
 * @property radius The radius of the circle.
 */
class Circle(private val center: Position, private val radius: Double) : SDF {
    init {
        require(radius > 0.0) { "Circle radius must be positive, got $radius" }
    }

    override fun invoke(position: Position): Double = position.euclideanDistanceTo(center) - radius
}
