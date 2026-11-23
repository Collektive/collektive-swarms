package it.unibo.collektive.swarms.sdf.shape

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.sdf.SDF
import it.unibo.collektive.swarms.sdf.primitive.Rectangle

/**
 * Represents a 2D Signed Distance Field (SDF) of an axis-aligned square.
 *
 * @param center The (X, Y) coordinates of the square's center.
 * @param side The side length.
 */
class Square(center: Position, side: Double) : SDF by Rectangle(center, side, side)
