package org.miker

import java.math.{MathContext, RoundingMode}
import java.sql.Connection
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}
import java.util.{Date, TimeZone}

import anorm.SqlParser._
import anorm.{Macro, RowParser, _}
import info.bitrich.xchangestream.core.{ProductSubscription, StreamingExchangeFactory}
import info.bitrich.xchangestream.gdax.GDAXStreamingExchange
import io.reactivex.schedulers.Schedulers
import org.flywaydb.core.Flyway
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.currency.{Currency, CurrencyPair}
import org.knowm.xchange.dto.Order.OrderType
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.gdax.GDAXExchange
import org.knowm.xchange.gdax.dto.trade.GDAXOrderFlags
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

  val trading = new AtomicBoolean(false)
  val orderId = new AtomicReference[String](null)
  val orderDate = new AtomicReference[Instant](null)
  val last = new AtomicReference[BigDecimal](null)
  val balances = new AtomicReference[Balances](null)
  val orderPrice = new AtomicReference[BigDecimal](null)

  // check for trades to complete
  val ex = new ScheduledThreadPoolExecutor(1)
  val f = ex.scheduleAtFixedRate(() => {
    try {
      if (trading.get()) {
        val openOrdersParams = exchange.getTradeService.createOpenOrdersParams()
        val openOrders = exchange.getTradeService.getOpenOrders(openOrdersParams)
        // getOrder never seems to think orders are filled, so check the number of open orders
        if (openOrders.getOpenOrders.size() == 0) {
          println(s"Order ${orderId.get()} filled")
          orderId.set(null)
          orderDate.set(null)
          updateBalancesAndOutlier()
          last.set(orderPrice.get())
          trading.set(false)
        }

        if (orderId.get != null && new Date().toInstant.minusSeconds(30).isAfter(orderDate.get()) && orderPrice.get() != orderBook.findBid() && orderPrice.get() != orderBook.findAsk() ) {
          println(s"Canceling order ${orderId.get} because it timed out")
          exchange.getTradeService.cancelOrder(orderId.get())
          orderId.set(null)
          orderDate.set(null)
          updateBalancesAndOutlier()
          trading.set(false)
        }
      }
    } catch {
      case e: Throwable => println(e.getMessage)
    }
  }, 1, 5, TimeUnit.SECONDS)

  exchange.getTradeService.getOrder()

  val lag = 5                           // seconds lookback
  val threshold = BigDecimal(0.5)       // std deviations
  val influence = BigDecimal(1)         // influence?
  val percent = BigDecimal(0)           // pct change
  val algo = new SmoothedZscoreDebounce[ZonedDateTime](lag, threshold, influence)

  var current = new AtomicReference[Outlier.EnumValue](null)

  var lastTime = new Date()
  val tickerStream = streamingExchange.getStreamingMarketDataService.getTicker(CurrencyPair.BTC_USD).observeOn(Schedulers.newThread()).subscribe(t => {
    if (last.get() == null) {
      // first run only
      last.set(t.getLast)
      lastTime = t.getTimestamp
      updateBalancesAndOutlier()
      println(s"${t.getTimestamp}\tStarting\tPrice: ${last.get}\tBTC: ${balances.get().btc}\tUSD: ${balances.get().usd}\t${current.get}")
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

  val gdaxMathContext = new MathContext(5, RoundingMode.DOWN)

  private def processOhlc(t: Ohlc): Unit = {
    algo.smoothedZScore(t.time, t.close).foreach { outlier =>
      println(s"${t.time}\t${t.close}\t$outlier\tTrading: ${trading.get()}")
      if (outlier != current.get() && !trading.get()) {
        current.set(outlier)
        println(s"${t.time}\t${t.close}\t$outlier Detected")
        if (outlier == Outlier.Peak) {
          // sell bitcoin at peaks
          orderPrice.set(orderBook.findAsk())
          trade(OrderType.ASK, balances.get().btc.round(gdaxMathContext), orderPrice.get)
        } else {
          // buy bitcoin at valleys
          orderPrice.set(orderBook.findBid())
          trade(OrderType.BID, (balances.get().usd / orderPrice.get).round(gdaxMathContext), orderPrice.get)
        }
        println(s"${t.time}\t${t.close}")
      }
    }
  }

  private def trade(orderType: OrderType, amount: BigDecimal, price: BigDecimal): Unit = {
    trading.set(true)
    println(s"${new Date()}\tCreate Trade\tLast: ${last.get}\tPrice: $price\t$amount\t$orderType\tBTC: ${balances.get().btc}\tUSD: ${balances.get().usd}")
    val limitOrder = new LimitOrder(orderType, amount.bigDecimal, CurrencyPair.BTC_USD, null, null, price.bigDecimal)
    limitOrder.addOrderFlag(GDAXOrderFlags.POST_ONLY)
    orderId.set(exchange.getTradeService.placeLimitOrder(limitOrder))
    orderDate.set(new Date().toInstant)
    // GDAXException
  }

  private def updateBalancesAndOutlier(): Unit = {
    val accountInfo = exchange.getAccountService.getAccountInfo
    balances.set(Balances(usd = accountInfo.getWallet.getBalance(Currency.USD).getAvailable, btc = accountInfo.getWallet.getBalance(Currency.BTC).getAvailable))
    // set a "current" state so the next transition will work properly
    if (balances.get().btc * last.get() > balances.get().usd) {
      current.set(Outlier.Valley)
    } else {
      current.set(Outlier.Peak)
    }
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

  private def jdbc_url = System.getenv("JDBC_DATABASE_URL")
  private def jdbc_user = System.getenv("JDBC_DATABASE_USERNAME")
  private def jdbc_pass = System.getenv("JDBC_DATABASE_PASSWORD")
  private def gdax_key = System.getenv("GDAX_KEY")
  private def gdax_secret = System.getenv("GDAX_SECRET")
  private def gdax_pass = System.getenv("GDAX_PASS")
}

case class Balances(usd: BigDecimal, btc: BigDecimal)

