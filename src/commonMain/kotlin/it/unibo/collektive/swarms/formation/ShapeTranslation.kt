package it.unibo.collektive.swarms.formation

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.stdlib.spreading.hopGradientCast
import it.unibo.collektive.swarms.geometry.Position
import it.unibo.collektive.swarms.localization.ReferenceFrame
import it.unibo.collektive.swarms.sdf.SDF
import it.unibo.collektive.swarms.sdf.deepestPointFrom
import it.unibo.collektive.swarms.sdf.translate

/**
 * The shape held by a device: its [name] in the catalog, and the point [inside] it that the devices place where the
 * shape belongs (the leader, or the centroid of the anchors).
 */
private data class HeldShape(val name: String, val inside: Position)

/**
 * Strategy that places (translates) a target [SDF] into the [ReferenceFrame] established by positioning.
 */
fun interface ShapeTranslation {
    /** Returns the target [SDF] placed in the given [frame]. */
    fun Aggregate<Int>.translate(frame: ReferenceFrame): SDF

    /** Invokes [translate] in the current [aggregate] context. */
    context(aggregate: Aggregate<Int>)
    operator fun invoke(frame: ReferenceFrame): SDF = aggregate.translate(frame)

    /** Built-in [ShapeTranslation] strategies. */
    companion object {
        /** Keeps [shape] fixed in global coordinates, without translating it. */
        fun global(shape: SDF): ShapeTranslation = ShapeTranslation { shape }

        /** Translates [shape] so that its reference [center] lies on [ReferenceFrame.center]. */
        fun centeredAtFrame(shape: SDF, center: Position = Position.origin): ShapeTranslation =
            ShapeTranslation { frame ->
                shape.translate(frame.center.x - center.x, frame.center.y - center.y)
            }

        /**
         * Spreads the name of the [shape] held by the leader ([ReferenceFrame.isLeader]) across the swarm, and places
         * the shape of the [catalog] with that name on [ReferenceFrame.center] (see [leaderShape]).
         * Changing [shape] on the leader at runtime reshapes the swarm.
         */
        fun fromLeader(catalog: Map<String, SDF>, shape: String): ShapeTranslation = ShapeTranslation { frame ->
            leaderShape(catalog, shape, frame.isLeader, frame.center)
        }
    }
}

/**
 * The shape held by the [leader], spread to every device by its nearest leader, moved so that the point inside it
 * that the leader found lies on [point] (the leader, or the centroid of the anchors, in the frame of the device).
 * The leader takes the shape called [shape] from the [catalog], and finds the point deep inside it nearest to its own
 * [point] (see [deepestPointFrom]): down to the border, then inward along the gradient of the SDF.
 * Only the name of the shape travels across the network, so all the devices must share the same [catalog].
 */
fun Aggregate<Int>.leaderShape(catalog: Map<String, SDF>, shape: String, leader: Boolean, point: Position): SDF {
    fun named(name: String) = requireNotNull(catalog[name]) { "Unknown shape '$name', expected one of ${catalog.keys}" }
    // Only the leader looks for the point inside, the others take the one it spreads.
    val local = HeldShape(shape, if (leader) named(shape).deepestPointFrom(point) else point)
    val (held, inside) = hopGradientCast(leader, local)
    return named(held).translate(point.x - inside.x, point.y - inside.y)
}
