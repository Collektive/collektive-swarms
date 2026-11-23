package it.unibo.collektive.swarms.geometry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOLERANCE = 1e-9

class VectorTest {
    @Test
    fun `vector algebra`() {
        val a = SpeedControl2D(3.0, 4.0)
        assertEquals(5.0, a.norm, TOLERANCE)
        assertEquals(SpeedControl2D(4.0, 5.0), a + SpeedControl2D(1.0, 1.0))
        assertEquals(SpeedControl2D(6.0, 8.0), a * 2.0)
        assertEquals(11.0, a dot SpeedControl2D(1.0, 2.0), TOLERANCE)
        assertEquals(1.0, SpeedControl2D(1.0, 0.0) cross SpeedControl2D(0.0, 1.0), TOLERANCE, "Counterclockwise")
        assertEquals(SpeedControl2D(2.0, 2.0), Position(3.0, 3.0) - Position(1.0, 1.0))
        assertEquals(Position(4.0, 3.0), Position(3.0, 3.0) + SpeedControl2D(1.0, 0.0))
    }

    @Test
    fun `limiting keeps the direction`() {
        val limited = SpeedControl2D(3.0, 4.0).limitedTo(1.0)
        assertEquals(0.6, limited.x, TOLERANCE)
        assertEquals(0.8, limited.y, TOLERANCE)
        assertEquals(SpeedControl2D(0.3, 0.4), SpeedControl2D(0.3, 0.4).limitedTo(1.0))
    }

    @Test
    fun `random directions are unit vectors`() {
        val random = Random(42)
        repeat(100) { assertEquals(1.0, random.randomDirection().norm, TOLERANCE) }
    }

    @Test
    fun `polar coordinates`() {
        val point = Position(1.0, 1.0).polar(2.0, kotlin.math.PI / 2)
        assertEquals(1.0, point.x, TOLERANCE)
        assertEquals(3.0, point.y, TOLERANCE)
        assertEquals(2.0, Position(1.0, 1.0).euclideanDistanceTo(point), TOLERANCE)
    }
}
