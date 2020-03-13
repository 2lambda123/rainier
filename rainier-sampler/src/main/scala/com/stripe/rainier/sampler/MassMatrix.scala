package com.stripe.rainier.sampler

sealed trait MassMatrix

object StandardMassMatrix extends MassMatrix

case class FullMassMatrix(elements: Array[Double]) extends MassMatrix {
  require(!elements.contains(0.0))
}

case class DiagonalMassMatrix(elements: Array[Double]) extends MassMatrix {
  require(!elements.contains(0.0))
}

class StandardMassMatrixTuner extends MassMatrixTuner {
  def initialize(lf: LeapFrog, iterations: Int): MassMatrix = StandardMassMatrix
  def update(sample: Array[Double]): Option[MassMatrix] = None
  def massMatrix: MassMatrix = StandardMassMatrix
}

trait WindowedMassMatrixTuner extends MassMatrixTuner {
  def initialWindowSize: Int
  def windowExpansion: Double
  def skipFirst: Int
  def skipLast: Int

  var prevMassMatrix: MassMatrix = StandardMassMatrix
  var estimator: MassMatrixEstimator = _
  var windowSize = initialWindowSize
  var i = 0
  var j = 0
  var totalIterations = 0

  def initialize(lf: LeapFrog, iterations: Int): MassMatrix = {
    estimator = initializeEstimator(lf.nVars)
    totalIterations = iterations
    StandardMassMatrix
  }

  def initializeEstimator(size: Int): MassMatrixEstimator

  def update(sample: Array[Double]): Option[MassMatrix] = {
    j += 1
    if (j < skipFirst || (totalIterations - j) < skipLast)
      None
    else {
      i += 1
      estimator.update(sample)
      if (i == windowSize) {
        i = 0
        windowSize = (windowSize * windowExpansion).toInt
        prevMassMatrix = estimator.massMatrix
        estimator.reset()
        Some(prevMassMatrix)
      } else {
        None
      }
    }
  }
}

case class DiagonalMassMatrixTuner(initialWindowSize: Int,
                                   windowExpansion: Double,
                                   skipFirst: Int,
                                   skipLast: Int)
    extends WindowedMassMatrixTuner {
  def initializeEstimator(size: Int) = new VarianceEstimator(size)
}

case class FullMassMatrixTuner(initialWindowSize: Int,
                               windowExpansion: Double,
                               skipFirst: Int,
                               skipLast: Int)
    extends WindowedMassMatrixTuner {
  def initializeEstimator(size: Int) = new CovarianceEstimator(size)
}