package it.unibo.collektive.swarms.localization

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.SpeedControl2D
import it.unibo.collektive.swarms.geometry.Vector2D
import it.unibo.collektive.swarms.geometry.plus
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-9

class LocalizationTest {
    // Anchors at (0, 0), (4, 0) and (4, 3)
    private val frame = AnchorFrame(anchor1ToAnchor2 = 4.0, anchor1ToAnchor3 = 5.0, anchor2ToAnchor3 = 3.0)

    @Test
    fun `the anchors fix the frame`() {
        assertEquals(4.0, frame.anchor3.x, TOLERANCE)
        assertEquals(3.0, frame.anchor3.y, TOLERANCE)
        assertEquals(8.0 / 3, frame.centroid.x, TOLERANCE)
        assertEquals(1.0, frame.centroid.y, TOLERANCE)
    }

    @Test
    fun `devices trilaterate their position`() {
        val position = checkNotNull(frame.trilaterate(5.0, 3.0, 0.0))
        assertEquals(4.0, position.x, TOLERANCE)
        assertEquals(3.0, position.y, TOLERANCE)
    }

    @Test
    fun `unknown or collinear anchors give no position`() {
        assertNull(AnchorFrame(Double.NaN, Double.NaN, Double.NaN).trilaterate(1.0, 1.0, 1.0))
        assertNull(AnchorFrame(1.0, 2.0, 1.0).trilaterate(1.0, 1.0, 1.0), "Anchor 3 on the line of 1 and 2")
    }

    /** Learns from [moves] in the body frame, perceived in the anchor frame through [bodyToAnchor]. */
    private fun learn(moves: List<Vector2D>, bodyToAnchor: (Vector2D) -> Vector2D): FrameAlignment {
        var position = Position.origin
        var travelled: Vector2D = SpeedControl2D(0.0, 0.0)
        var alignment = FrameAlignment().learn(position, travelled, forgettingFactor = 1.0, maxDisplacement = 5.0)
        moves.forEach { move ->
            position += bodyToAnchor(move)
            travelled += move
            alignment = alignment.learn(position, travelled, forgettingFactor = 1.0, maxDisplacement = 5.0)
        }
        return alignment
    }

    private val moves = listOf(SpeedControl2D(1.0, 0.0), SpeedControl2D(0.0, 1.0), SpeedControl2D(-1.0, 1.0))

    @Test
    fun `devices learn a rotated body frame from their own moves`() {
        val heading = 1.0
        val alignment = learn(moves) {
            SpeedControl2D(cos(heading) * it.x - sin(heading) * it.y, sin(heading) * it.x + cos(heading) * it.y)
        }
        assertEquals(0.5, alignment.confidence, TOLERANCE) // The moves span the plane, but unevenly
        assertTrue(alignment.isConfident(minMotionEnergy = 1.0, minConfidence = 0.4))
        // To go along +x in the anchor frame, the body must go along -heading
        val command = alignment.toBody(SpeedControl2D(1.0, 0.0))
        assertEquals(cos(heading), command.x, TOLERANCE)
        assertEquals(-sin(heading), command.y, TOLERANCE)
    }

    @Test
    fun `devices learn a mirrored body frame`() {
        val alignment = learn(moves) { SpeedControl2D(it.x, -it.y) }
        val command = alignment.toBody(SpeedControl2D(0.0, 1.0))
        assertEquals(0.0, command.x, TOLERANCE)
        assertEquals(-1.0, command.y, TOLERANCE)
    }

    @Test
    fun `collinear moves do not tell a rotation from a reflection`() {
        val alignment = learn(List(5) { SpeedControl2D(1.0, 0.0) }) { it }
        assertEquals(0.0, alignment.confidence, TOLERANCE)
        assertFalse(alignment.isConfident(minMotionEnergy = 1.0, minConfidence = 0.1))
    }

    @Test
    fun `frame jumps are not taken as moves`() {
        val alignment = learn(listOf(SpeedControl2D(10.0, 0.0))) { it }
        assertEquals(0.0, alignment.motionEnergy, TOLERANCE)
    }
}
