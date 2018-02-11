package org.miker

import java.io.IOException
import java.sql.Connection
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.{Date, TimeZone}

import anorm.SqlParser._
import anorm.{Macro, RowParser, _}
import info.bitrich.xchangestream.core.{ProductSubscription, StreamingExchangeFactory}
import info.bitrich.xchangestream.gdax.GDAXStreamingExchange
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import org.flywaydb.core.Flyway
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.bitstamp.service.BitstampMarketDataServiceRaw
import org.knowm.xchange.currency.{Currency, CurrencyPair}
import org.knowm.xchange.gdax.GDAXExchange
import org.knowm.xchange.service.marketdata.MarketDataService
import org.miker.Gdax.Ohlc
import org.miker.threshold.{Outlier, SmoothedZscoreDebounce}
import scalikejdbc.ConnectionPool

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
  // Gdax.history(latestData)

  val streamingExchange = StreamingExchangeFactory.INSTANCE.createExchange(classOf[GDAXStreamingExchange].getName)
  streamingExchange.connect(ProductSubscription.create().addAll(CurrencyPair.BTC_USD).build()).blockingAwait()

  val exSpec = new GDAXExchange().getDefaultExchangeSpecification
  exSpec.setApiKey(gdax_key)
  exSpec.setSecretKey(gdax_secret)
  exSpec.setExchangeSpecificParametersItem("passphrase", gdax_pass)
  val exchange = ExchangeFactory.INSTANCE.createExchange(exSpec)

  val orderBook = new OrderBook(streamingExchange)

  val lag = 5                           // seconds lookback
  val threshold = BigDecimal(0.5)       // std deviations
  val influence = BigDecimal(1)         // influence?
  val percent = BigDecimal(0)           // pct change
  val algo = new SmoothedZscoreDebounce[ZonedDateTime](lag, threshold, influence)

  var current: Outlier.EnumValue = _
  var last: BigDecimal = _
  var balances: Balances = _

  var lastTime = new Date()
  val tickerStream = streamingExchange.getStreamingMarketDataService.getTicker(CurrencyPair.BTC_USD).observeOn(Schedulers.newThread()).subscribe(t => {
    if (last == null) {
      // first run only
      last = t.getLast
      balances = getBalances
      if (balances.btc * last > balances.usd) {
        current = Outlier.Valley
      } else {
        current = Outlier.Peak
      }
      println(s"${t.getTimestamp}\tStarting\tPrice: $last\tBTC: ${balances.btc}\tUSD: ${balances.usd}\t$current")
    } else if (t.getTimestamp.toInstant.minusSeconds(1).isAfter(lastTime.toInstant)) {
      lastTime = t.getTimestamp
      processOhlc(Gdax.Ohlc(
        time = ZonedDateTime.ofInstant(t.getTimestamp.toInstant, ZoneId.systemDefault()),
        open = t.getLast,
        close = t.getLast,
        high = t.getHigh,
        low = t.getLow,
        volume = t.getVolume
      ))
    }
  })

  // Unsubscribe from data order book.
  //subscription.dispose

  // Disconnect from exchange (non-blocking)
  //exchange.disconnect.subscribe(() => LOG.info("Disconnected from the Exchange"))

  private def processOhlc(t: Ohlc) = {
    algo.smoothedZScore(t.time, t.close).foreach { outlier =>
      if (outlier != current && Math.abs((t.close - last).toDouble) / last > percent) {
        current = outlier
        last = t.close
        if (outlier == Outlier.Peak) {
          // sell bitcoin at peaks
          println(s"${t.time} selling bitcoin at ${t.close}")
          val book = exchange.getMarketDataService.getOrderBook(CurrencyPair.BTC_USD)
          balances = Balances(btc = 0, usd = balances.btc * t.close)
        } else {
          // buy bitcoin at valleys
          println(s"${t.time} buying bitcoin at ${t.close}")
          balances = Balances(btc = balances.usd / t.close, usd = 0)
        }
      }
    }
  }

  // make trade
  // - mark trading bit
  // - find gap
  // - create post only trade, limited time frame (30 sec?)
  // - if success, goto wait for fills
  // - if failure, goto make trade
  //
  // wait for fills
  // -

  private def getBalances: Balances = {
    val accountInfo = exchange.getAccountService.getAccountInfo
    Balances(usd = accountInfo.getWallet.getBalance(Currency.USD).getAvailable, btc = accountInfo.getWallet.getBalance(Currency.BTC).getAvailable)
  }

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
  private def gdax_key = System.getenv("GDAX_KEY")
  private def gdax_secret = System.getenv("GDAX_SECRET")
  private def gdax_pass = System.getenv("GDAX_PASS")
}

case class Balances(usd: BigDecimal, btc: BigDecimal)

