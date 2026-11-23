package it.unibo.collektive.swarms.sdf

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.sdf.primitive.Arc
import it.unibo.collektive.swarms.sdf.primitive.Circle
import it.unibo.collektive.swarms.sdf.primitive.HalfPlane
import it.unibo.collektive.swarms.sdf.primitive.Polygon
import it.unibo.collektive.swarms.sdf.primitive.Rectangle
import it.unibo.collektive.swarms.sdf.primitive.Segment
import it.unibo.collektive.swarms.sdf.shape.Star
import it.unibo.collektive.swarms.sdf.text.TextBlock
import it.unibo.collektive.swarms.sdf.text.toSdf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

private const val TOLERANCE = 1e-9
private const val GRID_STEP = 2.5

class ShapesTest {
    private val grid = (-10..50).flatMap { i -> (-10..50).map { j -> Position(i * GRID_STEP, j * GRID_STEP) } }

    @Test
    fun `primitives give the exact distance`() {
        assertEquals(1.0, Segment(Position.origin, Position(2.0, 0.0))(Position(1.0, 1.0)), TOLERANCE)
        assertEquals(sqrt(2.0), Segment(Position.origin, Position(2.0, 0.0))(Position(3.0, 1.0)), TOLERANCE)
        assertEquals(-1.0, Rectangle(Position.origin, 4.0, 2.0)(Position(0.0, 0.0)), TOLERANCE)
        assertEquals(1.0, Rectangle(Position.origin, 4.0, 2.0)(Position(3.0, 0.0)), TOLERANCE)
        assertEquals(-2.0, HalfPlane(Position.origin, Position(1.0, 0.0))(Position(5.0, 2.0)), TOLERANCE, "Left side")
        assertEquals(0.5, Arc(Position.origin, 1.0, 0.0, PI / 2)(Position(0.0, 1.5)), TOLERANCE)
        val concave = Polygon(listOf(Position(0.0, 0.0), Position(4.0, 0.0), Position(2.0, 1.0), Position(2.0, 4.0)))
        assertTrue(concave.isInside(Position(1.0, 0.5)))
        assertTrue(concave.isOutside(Position(3.0, 2.0)), "The notch of a concave polygon is outside")
    }

    @Test
    fun `every shape of the paper is a distance bound`() {
        // |f(p) - f(q)| <= |p - q|: a device never overestimates how far it may move before crossing the border
        paperShapes.forEach { (name, shape) ->
            grid.zipWithNext().filter { (p, q) -> p.y == q.y }.forEach { (p, q) ->
                val variation = abs(shape(p) - shape(q))
                if (variation > GRID_STEP + 1e-6) fail("'$name' changes by $variation over $GRID_STEP at $p")
            }
        }
    }

    @Test
    fun `every shape of the paper has an inside reachable from anywhere`() {
        paperShapes.forEach { (name, shape) ->
            assertTrue(shape.isOutside(Position(1000.0, 1000.0)), "'$name' is unbounded")
            listOf(Position(-100.0, -100.0), Position(50.0, 50.0), Position(200.0, 0.0)).forEach { start ->
                assertTrue(shape.isInside(shape.deepestPointFrom(start)), "'$name' from $start")
            }
        }
    }

    @Test
    fun `every letter has an inside`() {
        TextBlock.supportedLetters.forEach { letter ->
            val glyph = letter.toSdf(Position.origin, height = 10.0, thickness = 1.0)
            assertTrue(glyph.area(-5.0, 15.0, 0.25) > 0.0, "'$letter' is empty")
        }
        val text = "HI\nSEAMS".toSdf(Position.origin, height = 10.0)
        assertEquals(2, text.lineCount)
        assertTrue(text.width > 0.0 && text.height > 10.0)
    }

    @Test
    fun `invalid parameters fail fast`() {
        assertFailsWith<IllegalArgumentException> { Circle(Position.origin, -1.0) }
        assertFailsWith<IllegalArgumentException> { Polygon(listOf(Position.origin, Position(1.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { HalfPlane(Position.origin, Position.origin) }
        assertFailsWith<IllegalArgumentException> { Star(Position.origin, 10.0, 5, spikiness = 1.0) }
        assertFailsWith<IllegalArgumentException> { TextBlock("ciao!", Position.origin, 10.0) }
    }
}
