package it.unibo.collektive.swarms.formation

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.swarms.actuation.Actuation
import it.unibo.collektive.swarms.coverage.FormationControl
import it.unibo.collektive.swarms.geometry.SpeedControl2D
import it.unibo.collektive.swarms.localization.Positioning
import it.unibo.collektive.swarms.localization.ReferenceFrame
import it.unibo.collektive.swarms.sdf.SDF

/**
 * The outcome of one round of [swarmFormation] on a device.
 *
 * @property frame where the device is, in the frame the shape is evaluated in.
 * @property shape the target shape, placed in the [frame].
 * @property velocity the velocity command to apply, in the body frame of the device.
 */
data class FormationStep(val frame: ReferenceFrame, val shape: SDF, val velocity: SpeedControl2D)

/**
 * One round of swarm shape formation, separating its concerns:
 * 1. [positioning] establishes *where* each device is, yielding its local view of the reference frame;
 * 2. [translateShape] defines *what* shape to form, placing the target SDF into that reference frame;
 * 3. [control] defines *how* devices coordinate to form and cover the shape;
 * 4. [actuate] maps the resulting control velocity into the body frame of the device.
 *
 * The device applies the returned [FormationStep.velocity]: the library never moves it.
 */
fun Aggregate<Int>.swarmFormation(
    positioning: Positioning,
    translateShape: ShapeTranslation,
    control: FormationControl = FormationControl.lattice(),
    actuate: Actuation = Actuation.aligned,
): FormationStep {
    val frame = positioning()
    val shape = translateShape(frame)
    val velocity = frame.position?.let { control(shape, it, frame.offsets) }
    return FormationStep(frame, shape, actuate(frame, velocity))
}
