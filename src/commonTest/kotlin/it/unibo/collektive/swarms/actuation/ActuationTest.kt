package it.unibo.collektive.swarms.actuation

import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.SpeedControl2D
import it.unibo.collektive.swarms.geometry.zeroSpeed
import it.unibo.collektive.swarms.localization.ReferenceFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class ActuationTest {
    private val control = SpeedControl2D(1.0, 2.0)
    private val follower = ReferenceFrame(Position.origin, emptyList())

    @Test
    fun `aligned devices apply the control as it is`() {
        assertEquals(control, alignedActuation(follower, control))
    }

    @Test
    fun `the devices fixing the frame stay still`() {
        val leader = ReferenceFrame(Position.origin, emptyList(), isLeader = true)
        assertEquals(zeroSpeed, alignedActuation(leader, control))
        assertEquals(zeroSpeed, alignedActuation(follower.copy(isReference = true), control))
    }

    @Test
    fun `devices without a frame stay still`() {
        assertEquals(zeroSpeed, alignedActuation(follower, null))
    }
}
