package org.miker

import java.io.IOException
import java.sql.Connection
import java.time.{LocalDateTime, ZonedDateTime}
import java.util
import java.util.{Collections, TimeZone}

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
import org.miker.threshold.SmoothedZscore
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

  val threshold = new SmoothedZscore[Int](30, BigDecimal(5), BigDecimal(0))
  y.zipWithIndex.foreach {
    case(v: BigDecimal, k: Int) =>
      threshold.smoothedZScore(k, v).foreach(o => println(k.toString + " " + v.toString + " " + o.toString))
  }

  // Use the factory to get Bitstamp exchange API using default settings
  //val bitstamp = ExchangeFactory.INSTANCE.createExchange(classOf[BitstampExchange].getName)

  // Interested in the public market data feed (no authentication)
  //val marketDataService = bitstamp.getMarketDataService

  //generic(marketDataService)
  //raw(marketDataService.asInstanceOf[BitstampMarketDataServiceRaw])

  private def latestData: ZonedDateTime = {
    implicit val conn: Connection = ConnectionPool.borrow()
    SQL("select time from ohlc order by time desc limit 1").as(scalar[ZonedDateTime].singleOpt).getOrElse(ZonedDateTime.of(LocalDateTime.now, TimeZone.getDefault.toZoneId).minusDays(60))
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

