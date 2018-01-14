package org.miker.threshold

import org.apache.commons.math3.stat.descriptive.SummaryStatistics

import scala.collection.mutable

/**
  * Smoothed zero-score alogrithm shamelessly copied from https://stackoverflow.com/a/22640362/6029703
  * Uses a rolling mean and a rolling deviation (separate) to identify peaks in a vector
  *
  * @param lag - The lag of the moving window (i.e. how big the window is)
  * @param threshold - The z-score at which the algorithm signals (i.e. how many standard deviations away from the moving mean a peak (or signal) is)
  * @param influence - The influence (between 0 and 1) of new signals on the mean and standard deviation (how much a peak (or signal) should affect other values near it)
  * @return - The calculated averages (avgFilter) and deviations (stdFilter), and the signals (signals)
  */
class SmoothedZscoreDebounce[K](lag: Int, threshold: BigDecimal, influence: BigDecimal) {
  val y = new FixedSizeLinkedHashMap[K, BigDecimal](lag)
  val filteredY = new FixedSizeLinkedHashMap[K, BigDecimal](lag)
  val avgFilter = new FixedSizeLinkedHashMap[K, BigDecimal](lag)
  val stdFilter = new FixedSizeLinkedHashMap[K, BigDecimal](lag)
  val yStats = new SummaryStatistics()
  var lastTrend: Trend.EnumValue = Trend.Neutral

  def smoothedZScore(k: K, v: BigDecimal): Option[Outlier.EnumValue] = {
    yStats.addValue(v.toDouble)

    y.put(k, v)

    // pre-lag we don't know anything
    val currentTrend = if (y.size >= lag) {
      val prevAvgFilter = avgFilter.last._2
      val prevStdFilter = stdFilter.last._2
      val prevFilteredY = filteredY.last._2

      // if the distance between the current value and average is enough standard deviations (threshold) away
      val trend: Trend.EnumValue = if (Math.abs((v - prevAvgFilter).doubleValue()) > threshold * prevStdFilter) {
        // filter this signal out using influence
        filteredY(k) = (influence * v) + ((1 - influence) * prevFilteredY)

        // this is a signal (i.e. peak), determine if it is a positive or negative signal
        if (v > prevAvgFilter) Trend.Up else Trend.Down
      } else {
        // ensure this value is not filtered
        filteredY(k) = v

        // ensure this signal remains a zero
        Trend.Neutral
      }

      // update rolling average and deviation
      val stats = new SummaryStatistics()
      filteredY.values.foreach(v => stats.addValue(v.toDouble))
      avgFilter(k) = stats.getMean
      stdFilter(k) = Math.sqrt(stats.getPopulationVariance) // getStandardDeviation() uses sample variance (not what we want)
      trend
    } else {
      filteredY.put(k, v)
      avgFilter.put(k, yStats.getMean)
      stdFilter.put(k, Math.sqrt(yStats.getPopulationVariance))
      Trend.Neutral
    }

    // don't signal until we hit the end of a trend
    if (currentTrend == lastTrend) {
      None
    } else {
      lastTrend = currentTrend
      if (currentTrend == Trend.Neutral) {
        None
      } else {
        // these are reversed
        if (currentTrend == Trend.Down) {
          Some(Outlier.Peak)
        } else {
          Some(Outlier.Valley)
        }
      }
    }
  }
}

object Trend {
  sealed trait EnumValue
  case object Up extends EnumValue
  case object Down extends EnumValue
  case object Neutral extends EnumValue
}
