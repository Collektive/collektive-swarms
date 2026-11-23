package it.unibo.collektive.swarms.coverage

/**
 * The parameters of the lattice formation (see [latticeVelocity]).
 * The defaults are those of the experiments in the paper, with a communication range of 30 (and so a [repulsion]
 * reach of 15, half of it).
 *
 * @property repulsion how the neighbors push a device away.
 * @property maxSpeed the maximum speed of a device.
 * @property step how the step size adapts.
 */
data class LatticeParameters(
    val repulsion: RepulsionLaw = RepulsionLaw.Lloyd(gain = 1.0, reach = 15.0),
    val maxSpeed: Double = 1.0,
    val step: StepRule = StepRule(),
)
