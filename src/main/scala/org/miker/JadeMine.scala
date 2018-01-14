package org.miker

import java.io.IOException
import java.sql.Connection
import java.time.{LocalDateTime, ZonedDateTime}
import java.util.TimeZone

import anorm._
import anorm.SqlParser._
import anorm.{Macro, RowParser}
import org.flywaydb.core.Flyway
import org.knowm.xchange.bitstamp.service.BitstampMarketDataServiceRaw
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.service.marketdata.MarketDataService
import org.miker.Gdax.Ohlc
import org.miker.threshold.{Outlier, SmoothedZscore, SmoothedZscoreDebounce}
import scalikejdbc.ConnectionPool

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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
  //Gdax.history(latestData)

  val y = List(1d, 1d, 1.1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1d, 1.1d, 1d, 1d,
    1d, 1d, 1.1d, 0.9d, 1d, 1.1d, 1d, 1d, 0.9d, 1d, 1.1d, 1d, 1d, 1.1d, 1d, 0.8d, 0.9d, 1d, 1.2d, 0.9d, 1d,
    1d, 1.1d, 1.2d, 1d, 1.5d, 1d, 3d, 2d, 5d, 3d, 2d, 1d, 1d, 1d, 0.9d, 1d,
    1d, 3d, 2.6d, 4d, 3d, 3.2d, 2d, 1d, 1d, 0.8d, 4d, 4d, 2d, 2.5d, 1d, 1d, 1d).map(v => BigDecimal(v))

  val data = loadDataFromSql

  //Testbed.smoothedZScore(data.map(t => t.close.toDouble), 30, 5d, 0d)

  val lag = 5
  val threshold = BigDecimal(1.5)
  val influence = BigDecimal(0.62)
  val percent = BigDecimal(0.4)

  val algo = new SmoothedZscoreDebounce[ZonedDateTime](lag, BigDecimal(1.5), influence)
  var current: Outlier.EnumValue = Outlier.Valley
  var last = data.head.close
  var bitcoin = BigDecimal(1)
  var dollars = BigDecimal(0)
  var operations = 0

  data.foreach { t =>
    algo.smoothedZScore(t.time, t.close).foreach { o =>
      if (o != current && Math.abs((t.close - last).toDouble) / last > percent) {
        current = o
        last = t.close
        operations += 1
        if (o == Outlier.Peak) {
          // sell bitcoin at peaks
          dollars = bitcoin * t.close
          bitcoin = 0
        } else {
          // buy bitcoin at valleys
          bitcoin = dollars / t.close
          dollars = 0
        }
      }
    }
  }

  if (bitcoin == BigDecimal(0)) {
    bitcoin = dollars / data.last.close
  }
  println(lag.toString + "\t" + threshold.toString + "\t" + influence.toString + "\t" + percent.toString + "\t" + operations + "\t" + bitcoin.toString)

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

  private def loadDataFromSql: Seq[Ohlc] = {
    implicit val conn: Connection = ConnectionPool.borrow()
    val parser: RowParser[Ohlc] = Macro.namedParser[Ohlc]

    SQL("select time, low, high, open, close, volume from ohlc order by time asc").as(parser.*)
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

