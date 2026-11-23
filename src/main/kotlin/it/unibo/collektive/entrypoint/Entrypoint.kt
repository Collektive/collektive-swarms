package it.unibo.collektive.entrypoint

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.actuation.Actuation
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.BODY_FRAME_ACTIVE
import it.unibo.collektive.alchemist.device.applyVelocity
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.RelativePositionSensor
import it.unibo.collektive.alchemist.device.sensors.impl.ShapeProperty
import it.unibo.collektive.coverage.FormationControl
import it.unibo.collektive.library.ShapeTranslation
import it.unibo.collektive.localization.Positioning

/**
 * Modular aggregate entrypoint for swarm shape formation.
 *
 * Separates the concerns of adaptive formation:
 * 1. [positioning] establishes *where* each device is, yielding its local view of the reference frame;
 * 2. [translateShape] defines *what* shape to form by placing the target SDF into that reference frame;
 * 3. [control] defines *how* devices coordinate to form and cover the shape;
 * 4. [actuate] maps the resulting control velocity into the body frame of the device and applies it.
 */
context(device: CollektiveDevice<*>)
fun Aggregate<Int>.swarmFormation(
    positioning: Positioning,
    translateShape: ShapeTranslation,
    control: FormationControl = FormationControl.lattice(),
    actuate: Actuation = Actuation.aligned,
) {
    val frame = positioning()
    val shape = translateShape(frame)
    val velocity = frame.position?.let { control(shape, it, frame.offsets) }
    applyVelocity(actuate(frame, velocity))
}

/**
 * Shape formation with global positions (`positionBased.yml`): devices sense their global coordinates via
 * [locationSensor], form the [formation] shape where `shapes.yml` places it, and actuate velocities directly.
 */
fun Aggregate<Int>.positionBasedEntrypoint(
    device: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    swarmFormation(
        positioning = Positioning.global(locationSensor),
        translateShape = ShapeTranslation.global(formation),
        actuate = Actuation.aligned,
    )
}

/**
 * GPS-free shape formation with aligned local frames (`displacementBased.yml`): devices localize relative to an
 * elected leader via [sensor], translate the [formation] shape onto the leader, and actuate velocities in the aligned
 * frame while the leader stays still.
 */
fun Aggregate<Int>.displacementBasedEntrypoint(
    device: CollektiveDevice<*>,
    sensor: RelativePositionSensor,
    formation: ShapeProperty<*>,
) = with(device) {
    swarmFormation(
        positioning = Positioning.leaderRelative(sensor),
        translateShape = ShapeTranslation.fromLeader(formation),
        actuate = Actuation.aligned,
    )
}

/**
 * Range-only shape formation (`distanceBased.yml`): three elected anchors fix a reference frame from inter-device
 * distances alone, devices trilaterate in that frame, translate the [formation] shape onto the anchors' centroid, and
 * learn to steer in their own body frame from how their commands move them.
 */
fun Aggregate<Int>.distanceBasedEntrypoint(device: CollektiveDevice<*>, formation: ShapeProperty<*>) = with(device) {
    swarmFormation(
        positioning = Positioning.anchorBased(),
        translateShape = ShapeTranslation.fromLeader(formation),
        actuate = Actuation.selfAligned(),
    )
}

/**
 * Shape formation that switches positioning at runtime: displacement-based up to time 600, distance-based up to
 * 1200, position-based afterwards. The body frame (unknown heading) is active only in the distance-based phase, the
 * only one that learns to steer without knowing it. Each round the device writes its positioning in the `placement`
 * molecule (read by the exporters with placement `runtime`); in the position-based phase it also drops the roles of
 * leader and anchor, which are meaningless there.
 */
fun Aggregate<Int>.runtimePositioningEntrypoint(
    device: CollektiveDevice<*>,
    formation: ShapeProperty<*>,
    leaderRelative: RelativePositionSensor,
    locationSensor: LocationSensor,
) = with(device) {
    val time = device.environment.simulation.time.toDouble()
    val shape = if ((time / 300.0).toInt() % 2 == 0) "circle" else "star" // The shape alternates every 300
    device["shape"] = shape // Read by the leader (fromLeader) and by the exporters
    val phase = when {
        time < 600.0 -> Phase(Positioning.leaderRelative(leaderRelative), ShapeTranslation.fromLeader(formation), Actuation.aligned, bodyFrame = false, placement = "leader")
        time < 1200.0 -> Phase(Positioning.anchorBased(), ShapeTranslation.fromLeader(formation), Actuation.selfAligned(), bodyFrame = true, placement = "anchors")
        else -> Phase(Positioning.global(locationSensor), ShapeTranslation.global(formation.target(shape).sdf), Actuation.aligned, bodyFrame = false, placement = "global")
    }
    device[BODY_FRAME_ACTIVE.name] = phase.bodyFrame
    device["placement"] = phase.placement
    if (phase.placement == "global") { // No leader nor anchors with global positions: clear the roles of the past phases
        device["leader"] = false
        device["anchor"] = "NONE"
    }
    swarmFormation(phase.positioning, phase.shape, actuate = phase.actuation)
}

private data class Phase(
    val positioning: Positioning,
    val shape: ShapeTranslation,
    val actuation: Actuation,
    val bodyFrame: Boolean,
    val placement: String,
)
