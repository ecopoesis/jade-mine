package org.miker

import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import vegas._

import scala.collection.mutable

object SmoothedZscore extends App {
    val y = List(1d, 1d, 1.1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 1d,
      1d, 1d, 1.1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 1.1d, 1d, 0.8d, 0.9d, 1d, 1.2d, 0.9d, 1d,
      1d, 1.1d, 1.2d, 1d, 1.5d, 1d, 3d, 2d, 5d, 3d, 2d, 1d, 1d, 1d, 0.9d, 1d,
      1d, 3d, 2.6d, 4d, 3d, 3.2d, 2d, 1d, 1d, 0.8d, 4d, 4d, 2d, 2.5d, 1d, 1d, 1d)

    val lag = 30
    val threshold = 5d
    val influence = 0d

    smoothedZScore(y, lag, threshold, influence)

  /**
    * Smoothed zero-score alogrithm shamelessly copied from https://stackoverflow.com/a/22640362/6029703
    * Uses a rolling mean and a rolling deviation (separate) to identify peaks in a vector
    *
    * @param y - The input vector to analyze
    * @param lag - The lag of the moving window (i.e. how big the window is)
    * @param threshold - The z-score at which the algorithm signals (i.e. how many standard deviations away from the moving mean a peak (or signal) is)
    * @param influence - The influence (between 0 and 1) of new signals on the mean and standard deviation (how much a peak (or signal) should affect other values near it)
    * @return - The calculated averages (avgFilter) and deviations (stdFilter), and the signals (signals)
    */
  private def smoothedZScore(y: Seq[Double], lag: Int, threshold: Double, influence: Double): Seq[Int] = {
    val stats = new SummaryStatistics()

    // the results (peaks, 1 or -1) of our algorithm
    val signals = mutable.ArrayBuffer.fill(y.length)(0)

    // filter out the signals (peaks) from our original list (using influence arg)
    val filteredY = y.to[mutable.ArrayBuffer]

    // the current average of the rolling window
    val avgFilter = mutable.ArrayBuffer.fill(y.length)(0d)

    // the current standard deviation of the rolling window
    val stdFilter = mutable.ArrayBuffer.fill(y.length)(0d)

    // init avgFilter and stdFilter
    y.take(lag).foreach(s => stats.addValue(s))

    avgFilter(lag - 1) = stats.getMean
    stdFilter(lag - 1) = Math.sqrt(stats.getPopulationVariance) // getStandardDeviation() uses sample variance (not what we want)

    // loop input starting at end of rolling window
    y.zipWithIndex.slice(lag, y.length - 1).foreach {
      case (s: Double, i: Int) =>
        // if the distance between the current value and average is enough standard deviations (threshold) away
        if (Math.abs(s - avgFilter(i - 1)) > threshold * stdFilter(i - 1)) {
          // this is a signal (i.e. peak), determine if it is a positive or negative signal
          signals(i) = if (s > avgFilter(i - 1)) 1 else -1
          // filter this signal out using influence
          filteredY(i) = (influence * s) + ((1 - influence) * filteredY(i - 1))
        } else {
          // ensure this signal remains a zero
          signals(i) = 0
          // ensure this value is not filtered
          filteredY(i) = s
        }

        // update rolling average and deviation
        stats.clear()
        filteredY.slice(i - lag, i).foreach(s => stats.addValue(s))
        avgFilter(i) = stats.getMean
        stdFilter(i) = Math.sqrt(stats.getPopulationVariance) // getStandardDeviation() uses sample variance (not what we want)
    }

    println(y.length)
    println(signals.length)
    println(signals)

    signals.zipWithIndex.foreach {
      case(x: Int, idx: Int) =>
        if (x == 1) {
          println(idx + " " + y(idx))
        }
    }

    val data =
      y.zipWithIndex.map { case (s: Double, i: Int) => Map("x" -> i, "y" -> s, "name" -> "y", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: Double, i: Int) => Map("x" -> i, "y" -> s, "name" -> "avgFilter", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: Double, i: Int) => Map("x" -> i, "y" -> (s - threshold * stdFilter(i)), "name" -> "lower", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: Double, i: Int) => Map("x" -> i, "y" -> (s + threshold * stdFilter(i)), "name" -> "upper", "row" -> "data") } ++
      signals.zipWithIndex.map { case (s: Int, i: Int) => Map("x" -> i, "y" -> s, "name" -> "signal", "row" -> "signal") }

    Vegas("Smoothed Z")
      .withData(data)
      .mark(Line)
      .encodeX("x", Quant)
      .encodeY("y", Quant)
      .encodeColor(
        field="name",
        dataType=Nominal
      )
      .encodeRow("row", Ordinal)
      .show

    return signals
  }
}

