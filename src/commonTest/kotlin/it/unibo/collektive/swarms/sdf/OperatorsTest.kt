package it.unibo.collektive.swarms.sdf

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.euclideanDistanceTo
import it.unibo.collektive.swarms.sdf.primitive.Circle
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-9

class OperatorsTest {
    private val unit = Circle(Position.origin, 1.0)
    private val right = Circle(Position(1.5, 0.0), 1.0)

    @Test
    fun `the sign tells inside from outside`() {
        assertEquals(-1.0, unit(Position.origin), TOLERANCE)
        assertEquals(0.0, unit(Position(1.0, 0.0)), TOLERANCE)
        assertEquals(1.0, unit(Position(0.0, 2.0)), TOLERANCE)
        assertTrue(unit.isInside(Position(1.0, 0.0)), "The border is inside")
        assertTrue(unit.isOutside(Position(1.0, 1.0)))
    }

    @Test
    fun `union intersection complement and difference follow set theory`() {
        val inLeftOnly = Position(-0.5, 0.0)
        val inBoth = Position(0.75, 0.0)
        val inRightOnly = Position(2.0, 0.0)
        assertTrue(listOf(inLeftOnly, inBoth, inRightOnly).all { (unit or right).isInside(it) })
        assertTrue((unit and right).isInside(inBoth))
        assertTrue((unit and right).isOutside(inLeftOnly))
        assertTrue((!unit).isInside(inRightOnly))
        assertTrue((unit - right).isInside(inLeftOnly))
        assertTrue((unit - right).isOutside(inBoth))
        assertTrue(listOf(unit, right).union().isInside(inRightOnly))
    }

    @Test
    fun `the union of no shapes fails`() {
        assertFailsWith<IllegalStateException> { emptyList<SDF>().union() }
    }

    @Test
    fun `expand ring and outline offset the border`() {
        assertEquals(-1.5, (unit expand 0.5)(Position.origin), TOLERANCE)
        assertEquals(0.5, (unit expand -0.5)(Position(1.0, 0.0)), TOLERANCE)
        val band = unit ring 0.1
        assertTrue(band.isInside(Position(1.05, 0.0)))
        assertTrue(band.isOutside(Position.origin), "A ring is hollow")
        assertEquals(0.0, unit.outline()(Position(0.0, 1.0)), TOLERANCE)
        assertEquals(1.0, unit.outline()(Position.origin), TOLERANCE)
        assertFailsWith<IllegalArgumentException> { unit ring -1.0 }
    }

    @Test
    fun `rigid transformations and scaling keep distances exact`() {
        val moved = unit.scale(2.0).rotate(PI / 3).translate(5.0, 5.0)
        assertEquals(-2.0, moved(Position(5.0, 5.0)), TOLERANCE)
        assertEquals(1.0, moved(Position(8.0, 5.0)), TOLERANCE)
        val offCenter = Circle(Position(1.0, 0.0), 0.5)
        assertEquals(-0.5, offCenter.rotate(PI / 2)(Position(0.0, 1.0)), TOLERANCE, "Rotates around the origin")
        assertEquals(-0.5, offCenter.rotate(PI, pivot = Position(2.0, 0.0))(Position(3.0, 0.0)), TOLERANCE)
        assertFailsWith<IllegalArgumentException> { unit.scale(0.0) }
    }

    @Test
    fun `the gradient points away from the shape`() {
        val gradient = gradientToSDF(unit, Position(3.0, 0.0), 1e-3)
        assertTrue(gradient.x > 0.0)
        assertEquals(0.0, gradient.y, TOLERANCE)
    }

    @Test
    fun `the area is estimated by counting cells`() {
        assertEquals(PI * 100, Circle(Position.origin, 10.0).area(-20.0, 20.0, 0.1), PI * 100 * 0.01)
    }

    @Test
    fun `the deepest point of a convex shape is its center`() {
        val disk = Circle(Position(5.0, 5.0), 10.0)
        assertTrue(disk.deepestPointFrom(Position(100.0, -40.0)).euclideanDistanceTo(Position(5.0, 5.0)) < 0.01)
    }
}
