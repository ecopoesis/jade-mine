package org.miker

import java.io.IOException
import java.sql.Connection
import java.time.ZonedDateTime
import java.util
import java.util.Collections

import org.flywaydb.core.Flyway
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.bitstamp.BitstampExchange
import org.knowm.xchange.bitstamp.service.BitstampMarketDataServiceRaw
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.service.marketdata.MarketDataService
import scalikejdbc.ConnectionPool
import anorm._
import anorm.SqlParser._
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import vegas._
import vegas.render.WindowRenderer._
import vegas.data.External._

import scala.collection.mutable

object JadeMine extends App {
  println("let's make some money")

  // setup DB
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton(jdbc_url, jdbc_user, jdbc_pass)

  // migrate DB
  val flyway: Flyway = new Flyway
  flyway.setDataSource(jdbc_url, jdbc_user, jdbc_pass)
  flyway.migrate()

  // catchup the database
  Gdax.history(latestData)

  val y = List(1d, 1d, 1.1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 1d,
    1d, 1d, 1.1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 1.1d, 1d, 0.8d, 0.9d, 1d, 1.2d, 0.9d, 1d,
    1d, 1.1d, 1.2d, 1d, 1.5d, 1d, 3d, 2d, 5d, 3d, 2d, 1d, 1d, 1d, 0.9d, 1d,
    1d, 3d, 2.6d, 4d, 3d, 3.2d, 2d, 1d, 1d, 0.8d, 4d, 4d, 2d, 2.5d, 1d, 1d, 1d).map(v => BigDecimal(v))

  smoothedZScore(y, 30, BigDecimal(5), BigDecimal(0))

  // Use the factory to get Bitstamp exchange API using default settings
  val bitstamp = ExchangeFactory.INSTANCE.createExchange(classOf[BitstampExchange].getName)

  // Interested in the public market data feed (no authentication)
  val marketDataService = bitstamp.getMarketDataService

  //generic(marketDataService)
  //raw(marketDataService.asInstanceOf[BitstampMarketDataServiceRaw])

  /**
    * Smoothed zero-score alogrithm" shamelessly copied from https://stackoverflow.com/a/22640362/6029703
    * Uses a rolling mean and a rolling deviation (separate) to identify peaks in a vector
    *
    * @param y - The input vector to analyze
    * @param lag - The lag of the moving window (i.e. how big the window is)
    * @param threshold - The z-score at which the algorithm signals (i.e. how many standard deviations away from the moving mean a peak (or signal) is)
    * @param influence - The influence (between 0 and 1) of new signals on the mean and standard deviation (how much a peak (or signal) should affect other values near it)
    * @return - The calculated averages (avgFilter) and deviations (stdFilter), and the signals (signals)
    */
  private def smoothedZScore(y: Seq[BigDecimal], lag: Int, threshold: BigDecimal, influence: BigDecimal) {
    val stats = new SummaryStatistics()

    // the results (peaks, 1 or -1) of our algorithm
    val signals = mutable.ArrayBuffer.fill(y.length)(0)

    // filter out the signals (peaks) from our original list (using influence arg)
    val filteredY = y.to[mutable.ArrayBuffer]

    // the current average of the rolling window
    val avgFilter = mutable.ArrayBuffer.fill(y.length)(BigDecimal(0))

    // the current standard deviation of the rolling window
    val stdFilter = mutable.ArrayBuffer.fill(y.length)(BigDecimal(0))

    // init avgFilter and stdFilter
    y.take(lag).foreach(s => stats.addValue(s.toDouble))

    avgFilter(lag - 1) = stats.getMean
    stdFilter(lag - 1) = Math.sqrt(stats.getPopulationVariance) // getStandardDeviation() uses sample variance (not what we want)

    // loop input starting at end of rolling window
    y.zipWithIndex.slice(lag, y.length - 1).foreach {
      case (s: BigDecimal, i: Int) =>
        // if the distance between the current value and average is enough standard deviations (threshold) away
        if (Math.abs((s - avgFilter(i-1)).doubleValue()) > threshold * stdFilter(i - 1)) {
          // this is a signal (i.e. peak), determine if it is a positive or negative signal
          signals(i) = if (y(i) > avgFilter(i - 1)) 1 else -1
          // filter this signal out using influence
          filteredY(i) = (influence * y(i)) + ((1 - influence) * filteredY(i - 1))
        } else {
          // ensure this signal remains a zero
          signals(i) = 0
          // ensure this value is not filtered
          filteredY(i) = y(i)
        }

        // update rolling average and deviation
        stats.clear()
        filteredY.slice(i - lag, i).foreach(s => stats.addValue(s.toDouble))
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
      y.zipWithIndex.map { case (s: BigDecimal, i: Int) => Map("x" -> i, "y" -> s, "name" -> "y", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: BigDecimal, i: Int) => Map("x" -> i, "y" -> s, "name" -> "avgFilter", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: BigDecimal, i: Int) => Map("x" -> i, "y" -> (s - threshold * stdFilter(i)), "name" -> "lower", "row" -> "data") } ++
      avgFilter.zipWithIndex.map { case (s: BigDecimal, i: Int) => Map("x" -> i, "y" -> (s + threshold * stdFilter(i)), "name" -> "upper", "row" -> "data") } ++
      signals.zipWithIndex.map { case (s: Int, i: Int) => Map("x" -> i, "y" -> s, "name" -> "signal", "row" -> "signal") }

    Vegas("Smoothed Z", width=600d, height=600d)
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
  }

  private def latestData: ZonedDateTime = {
    implicit val conn: Connection = ConnectionPool.borrow()
    SQL("select time from ohlc order by time desc limit 1").as(scalar[ZonedDateTime].single)
  }

  @throws[IOException]
  private def generic(marketDataService: MarketDataService): Unit = {
    val ticker = marketDataService.getTicker(CurrencyPair.BTC_USD)
    println(ticker.toString)
  }

  @throws[IOException]
  private def raw(marketDataService: BitstampMarketDataServiceRaw): Unit = {
    val bitstampTicker = marketDataService.getBitstampTicker(CurrencyPair.BTC_USD)
    println(bitstampTicker.toString)
  }

  private def jdbc_url = System.getenv("JDBC_DATABASE_URL")
  private def jdbc_user = System.getenv("JDBC_DATABASE_USERNAME")
  private def jdbc_pass = System.getenv("JDBC_DATABASE_PASSWORD")
}

