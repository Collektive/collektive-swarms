package it.unibo.collektive.swarms.coverage

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.SpeedControl2D
import it.unibo.collektive.swarms.geometry.times
import it.unibo.collektive.swarms.geometry.zeroSpeed
import it.unibo.collektive.swarms.sdf.primitive.Circle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-6

class CoverageTest {
    private val disk = Circle(Position.origin, 10.0)

    @Test
    fun `the border pulls in from outside only`() {
        val outside = LocalBorder.of(disk, Position(15.0, 0.0))
        assertEquals(5.0, outside.distance, TOLERANCE)
        assertEquals(-1.0, outside.towardsShape.x, TOLERANCE)
        assertEquals(1.0, outside.outward.x, TOLERANCE)
        val inside = LocalBorder.of(disk, Position(5.0, 0.0))
        assertTrue(inside.isInside)
        assertEquals(zeroSpeed, inside.towardsShape)
    }

    @Test
    fun `the step shrinks on overshoots and grows back`() {
        val rule = StepRule(decrease = 0.5, increase = 1.2, minGain = 0.1)
        val forward = SpeedControl2D(1.0, 0.0)
        val backward = SpeedControl2D(-1.0, 0.0)
        val first = AdaptiveStep().adapt(forward, rule)
        assertEquals(AdaptiveStep.FULL, first.gain, TOLERANCE, "Never above a full step")
        val overshot = first.adapt(backward, rule)
        assertEquals(0.5, overshot.gain, TOLERANCE)
        assertEquals(0.6, overshot.adapt(backward, rule).gain, TOLERANCE)
        val oscillating = generateSequence(overshot) { it.adapt(it.lastControl * -1.0, rule) }.take(20).last()
        assertEquals(rule.minGain, oscillating.gain, TOLERANCE, "Never below the min gain")
    }

    @Test
    fun `an isolated device inside a large shape stays still`() {
        assertTrue(voronoiCentroid(disk, Position.origin, emptyList(), reach = 3.0).norm < 0.1)
    }

    @Test
    fun `a device moves away from a close neighbor`() {
        val centroid = voronoiCentroid(disk, Position.origin, listOf(SpeedControl2D(1.0, 0.0)), reach = 3.0)
        assertTrue(centroid.x < 0.0)
        assertEquals(0.0, centroid.y, 0.05)
    }

    @Test
    fun `the cell is clipped to the shape near its border`() {
        val centroid = voronoiCentroid(disk, Position(9.0, 0.0), emptyList(), reach = 3.0)
        assertTrue(centroid.x < 0.0, "The part of the cell out of the shape does not count")
    }

    @Test
    fun `the Lloyd law scales the step to the centroid by its gain`() {
        val neighbors = listOf(SpeedControl2D(1.0, 0.0))
        val full = RepulsionLaw.Lloyd(gain = 1.0, reach = 3.0).control(neighbors, disk, Position.origin)
        val half = RepulsionLaw.Lloyd(gain = 0.5, reach = 3.0).control(neighbors, disk, Position.origin)
        assertEquals(full.x / 2, half.x, TOLERANCE)
    }
}
