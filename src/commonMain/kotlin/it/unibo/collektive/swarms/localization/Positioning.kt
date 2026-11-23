package it.unibo.collektive.swarms.localization

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.values
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.geometry.Vector2D
import it.unibo.collektive.swarms.geometry.minus
import it.unibo.collektive.swarms.geometry.times
import it.unibo.collektive.swarms.sensors.DistanceSensor
import it.unibo.collektive.swarms.sensors.LocationSensor
import it.unibo.collektive.swarms.sensors.RelativePositionSensor

private const val DEFAULT_LEADER_BOUND = 50

/**
 * The parameters of the election of the anchors (see [electAnchors]); the defaults are those of the experiments in the
 * paper.
 *
 * @property leaderElectionBound the hops within which anchor 1 is elected.
 * @property minAnchorsHeight the min distance of anchor 3 from the line of anchors 1-2 (below the height of the
 * triangle, about `0.87 * anchorsSize`).
 * @property anchorsSize the side of the triangle of the anchors (path distances): small enough to fit in the shape.
 * @property anchorPatience the rounds without the report of an anchor before anchor 1 replaces it.
 */
data class AnchorParameters(
    val leaderElectionBound: Int = DEFAULT_LEADER_BOUND,
    val minAnchorsHeight: Double = 8.0,
    val anchorsSize: Double = 8.0,
    val anchorPatience: Int = 10,
)

/**
 * The local view of the reference frame in which a device evaluates the target shape and coordinates with its
 * neighbors.
 *
 * @property position the estimated position of the device in the frame, or `null` until the frame is known.
 * @property offsets the offsets of the neighbors (`neighbor - self`) in the frame.
 * @property center the reference point of the frame on which a translated shape is placed.
 * @property isLeader whether this device is the leader that holds and spreads the shape.
 * @property isReference whether this device fixes the frame (e.g., the leader or an anchor) and must stay still.
 * @property anchorRole the anchor this device is, with anchor-based positioning ([AnchorRole.NONE] otherwise).
 */
data class ReferenceFrame(
    val position: Position?,
    val offsets: List<Vector2D>,
    val center: Position = Position.origin,
    val isLeader: Boolean = false,
    val isReference: Boolean = isLeader,
    val anchorRole: AnchorRole = AnchorRole.NONE,
)

/**
 * Strategy that establishes the [ReferenceFrame] in which a device evaluates the shape and perceives its neighbors.
 */
fun interface Positioning {
    /** Computes the [ReferenceFrame] of the device for the current round. */
    fun Aggregate<Int>.locate(): ReferenceFrame

    /** Invokes [locate] in the current [aggregate] context. */
    context(aggregate: Aggregate<Int>)
    operator fun invoke(): ReferenceFrame = aggregate.locate()

    /** Built-in [Positioning] strategies for the three spatial perception regimes. */
    companion object {
        /**
         * Position-based (global) perception: each device directly senses its global coordinates via [locationSensor]
         * and exchanges them with its neighbors (see [globalPositioning]).
         */
        fun global(locationSensor: LocationSensor): Positioning = Positioning {
            globalPositioning(locationSensor)
        }

        /**
         * Displacement-based (leader-relative) perception: a leader is elected within [leaderElectionBound] hops, and
         * every device estimates its position relative to the leader via [sensor] (see [leaderPositioning]).
         */
        fun leaderRelative(
            sensor: RelativePositionSensor,
            leaderElectionBound: Int = DEFAULT_LEADER_BOUND,
        ): Positioning = Positioning {
            leaderPositioning(sensor, leaderElectionBound)
        }

        /**
         * Distance-based (range-only) perception: three elected anchors fix a coordinate frame from inter-device
         * distances alone, measured by [sensor], and every device trilaterates its position in that frame
         * (see [anchorPositioning]).
         */
        fun anchorBased(sensor: DistanceSensor, parameters: AnchorParameters = AnchorParameters()): Positioning =
            Positioning {
                anchorPositioning(sensor, parameters)
            }
    }
}

/**
 * Global positioning: the device reads its absolute coordinates from [locationSensor] and exchanges them with its
 * neighbors to compute their relative offsets.
 */
fun Aggregate<Int>.globalPositioning(locationSensor: LocationSensor): ReferenceFrame {
    val position = locationSensor.coordinates()
    val offsets = neighboring(position).neighbors.values.list.map { it - position }
    return ReferenceFrame(position, offsets)
}

/**
 * Leader-relative positioning: elects a system-wide leader within [leaderElectionBound] hops and computes the position
 * of every device in the frame centered on the leader by accumulating the displacements sensed by [sensor].
 */
fun Aggregate<Int>.leaderPositioning(
    sensor: RelativePositionSensor,
    leaderElectionBound: Int = DEFAULT_LEADER_BOUND,
): ReferenceFrame {
    val isLeader = boundedElection(-localId, leaderElectionBound) == localId
    val position = positionRelativeTo(isLeader, sensor)
    val offsets = mapNeighborhood { sensor.relativeTo(it) * -1.0 }.neighbors.values.list
    return ReferenceFrame(position, offsets, isLeader = isLeader)
}

/**
 * Range-only anchor-based positioning: elects three anchors ([electAnchors]) from the neighbor distances measured by
 * [sensor] alone, trilaterates the device position in the frame they fix ([localize]), and estimates neighbor offsets
 * ([offsetsFromEstimates]).
 */
fun Aggregate<Int>.anchorPositioning(
    sensor: DistanceSensor,
    parameters: AnchorParameters = AnchorParameters(),
): ReferenceFrame {
    val neighborDistances = mapNeighborhood { if (it == localId) 0.0 else sensor.distanceTo(it) }
    val role = electAnchors(
        neighborDistances,
        leaderElectionBound = parameters.leaderElectionBound,
        minAnchorsHeight = parameters.minAnchorsHeight,
        anchorsSize = parameters.anchorsSize,
        anchorPatience = parameters.anchorPatience,
    )
    val isAnchor = role != AnchorRole.NONE
    val (frame, position) = localize(role, neighborDistances)
    val offsets = offsetsFromEstimates(position, neighborDistances)
    return ReferenceFrame(
        position = position,
        offsets = offsets,
        center = frame.centroid,
        isLeader = role == AnchorRole.ANCHOR_1,
        isReference = isAnchor,
        anchorRole = role,
    )
}
