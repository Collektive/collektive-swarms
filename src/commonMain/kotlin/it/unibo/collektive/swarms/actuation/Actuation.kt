package it.unibo.collektive.swarms.actuation

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.swarms.geometry.SpeedControl2D
import it.unibo.collektive.swarms.geometry.limitedTo
import it.unibo.collektive.swarms.geometry.plus
import it.unibo.collektive.swarms.geometry.randomDirection
import it.unibo.collektive.swarms.geometry.times
import it.unibo.collektive.swarms.geometry.zeroSpeed
import it.unibo.collektive.swarms.localization.FrameAlignment
import it.unibo.collektive.swarms.localization.ReferenceFrame
import kotlin.random.Random

/**
 * Strategy that maps the desired control velocity in the [ReferenceFrame] into the velocity command in the body frame
 * of the device.
 */
fun interface Actuation {
    /** Computes the velocity command in the body frame from the [frame] and the desired [control] velocity. */
    fun Aggregate<Int>.actuate(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D

    /** Invokes [actuate] in the current [aggregate] context. */
    context(aggregate: Aggregate<Int>)
    operator fun invoke(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D =
        aggregate.actuate(frame, control)

    /** Built-in [Actuation] strategies. */
    companion object {
        /**
         * Direct actuation for devices whose local frame is aligned with the [ReferenceFrame]: reference devices fixing
         * the frame ([ReferenceFrame.isReference]) stay still, while all other devices apply [control] directly
         * (see [alignedActuation]).
         */
        val aligned: Actuation = Actuation { frame, control ->
            alignedActuation(frame, control)
        }

        /**
         * Self-aligned actuation for range-only devices operating in an unknown local body frame: each device learns
         * the orthogonal transformation between its body frame and the [ReferenceFrame] from how its own commands move
         * it ([FrameAlignment]), probing the environment with run-and-tumble steps until confident
         * (see [selfAlignedActuation]), as set by the [parameters]; the probes draw their directions from [random].
         */
        fun selfAligned(
            parameters: SelfAlignmentParameters = SelfAlignmentParameters(),
            random: Random = Random.Default,
        ): Actuation = Actuation { frame, control ->
            selfAlignedActuation(frame, control, parameters, random)
        }
    }
}

/**
 * Actuation when the local frame of the device is aligned with [frame]: reference devices
 * ([ReferenceFrame.isReference]) stay still (`zeroSpeed`), while all other devices track [control] directly.
 */
fun alignedActuation(frame: ReferenceFrame, control: SpeedControl2D?): SpeedControl2D =
    if (frame.isReference || control == null) zeroSpeed else control

/**
 * The parameters of [selfAlignedActuation]; the defaults are those of the experiments in the paper.
 *
 * @property forgettingFactor the past evidence kept at each round by the [FrameAlignment].
 * @property maxDisplacement the changes of the estimated position above this are frame jumps, not moves.
 * @property minMotionEnergy the motion needed before steering: it grows with the square of [explorationSpeed].
 * @property minConfidence the spread of directions needed before steering.
 * @property explorationSpeed the speed of the random moves, before localizing and while learning to steer.
 * @property maxFrameWait the rounds a device stays still waiting for the frame, before exploring.
 * @property maxSpeed the maximum speed of a device.
 */
data class SelfAlignmentParameters(
    val forgettingFactor: Double = 0.9,
    val maxDisplacement: Double = 5.0,
    val minMotionEnergy: Double = 5.0,
    val minConfidence: Double = 0.1,
    val explorationSpeed: Double = 1.0,
    val maxFrameWait: Int = 50,
    val maxSpeed: Double = 1.0,
)

/**
 * Actuation when the local body frame of the device has an unknown orientation w.r.t. [frame]: the device learns the
 * alignment from its own motion ([FrameAlignment]) and explores with a run-and-tumble probe until confident.
 * The returned command is in the body frame, and must be applied as it is: the device learns from the commands it
 * returned in the past rounds.
 */
fun Aggregate<Int>.selfAlignedActuation(
    frame: ReferenceFrame,
    control: SpeedControl2D?,
    parameters: SelfAlignmentParameters = SelfAlignmentParameters(),
    random: Random = Random.Default,
): SpeedControl2D = evolve(zeroSpeed) { lastCommand ->
    // Applied since the last round
    val commanded = evolve(zeroSpeed) { it + lastCommand }
    val alignment = evolve(FrameAlignment()) {
        it.learn(frame.position, commanded, parameters.forgettingFactor, parameters.maxDisplacement)
    }
    val steering = evolve(false) {
        it || alignment.isConfident(parameters.minMotionEnergy, parameters.minConfidence)
    }
    val probe = evolve(zeroSpeed to false) { (direction, out) ->
        if (out) direction * -1.0 to false else random.randomDirection() to true
    }.first * parameters.explorationSpeed
    val unlocalized = evolve(0) { if (control == null) it + 1 else 0 } // Rounds since the device lost the frame
    // Run and tumble: the direction is kept for one step and reversed on the next, else drawn anew.
    val command = when {
        control == null -> if (unlocalized > parameters.maxFrameWait) probe else zeroSpeed
        frame.isReference -> zeroSpeed // The anchors are the reference frame: they stay still.
        !steering -> probe
        else -> alignment.toBody(control)
    }
    val velocity = command.limitedTo(parameters.maxSpeed).takeIf { it.norm.isFinite() } ?: zeroSpeed
    velocity
}
