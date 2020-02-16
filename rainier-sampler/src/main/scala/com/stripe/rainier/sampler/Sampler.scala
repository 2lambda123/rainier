package com.stripe.rainier.sampler

import scala.collection.mutable.ListBuffer
import Log._

case class Sampler(iterations: Int, warmups: List[Warmup] = Nil) {
  def findInitialStepSize: Sampler =
    warmup(InitialStepSize)

  def fixedPathLength(n: Int): Sampler =
    warmup(FixedPathLength(n))

  def dualAverageStepSize(iterations: Int, delta: Double = 0.65): Sampler =
    warmup(DualAvgStepSize(iterations, delta))

  def empiricalPathLengths(iterations: Int): Sampler =
    warmup(EmpiricalPathLength(iterations))

  def warmup(w: Warmup): Sampler =
    Sampler(iterations, warmups :+ w)

  def sample(density: DensityFunction, progress: ProgressState)(
      implicit rng: RNG) = {
    val state = new SamplerState(density, progress)
    warmups.foreach { w =>
      if (state.isValid)
        w.update(state)
    }

    val samples =
      if (state.isValid) {
        run(state)
      } else {
        WARNING.log("Warmup failed, aborting!")
        List(state.variables)
      }

    state.finish()
    samples
  }

  private def run(state: SamplerState): List[Array[Double]] = {
    val buf = new ListBuffer[Array[Double]]
    var i = 0

    state.startPhase("Sampling", iterations)
    while (i < iterations) {
      state.step()
      buf += state.variables
      i += 1
    }
    buf.toList
  }
}

object Sampler {
  val default =
    EHMC(warmupIterations = 10000, iterations = 10000, l0 = 10, k = 1000)
}

trait Warmup {
  def update(state: SamplerState)(implicit rng: RNG): Unit
}

//backwards compatibility
object HMC {
  def apply(warmupIterations: Int, iterations: Int, nSteps: Int): Sampler =
    Sampler(iterations)
      .fixedPathLength(nSteps)
      .findInitialStepSize
      .dualAverageStepSize(warmupIterations)
}

object EHMC {
  def apply(warmupIterations: Int, iterations: Int, l0: Int, k: Int): Sampler =
    Sampler(iterations)
      .fixedPathLength(l0)
      .findInitialStepSize
      .dualAverageStepSize(warmupIterations)
      .empiricalPathLengths(k)
}
