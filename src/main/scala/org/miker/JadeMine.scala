package org.miker

import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.bitstamp.BitstampExchange
import org.knowm.xchange.bitstamp.service.{BitstampMarketDataService, BitstampMarketDataServiceRaw}
import org.knowm.xchange.service.marketdata.MarketDataService
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.marketdata.Ticker
import org.knowm.xchange.service.marketdata.MarketDataService
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

object JadeMine extends App {
  println("let's make some money")

  // Use the factory to get Bitstamp exchange API using default settings
  val bitstamp = ExchangeFactory.INSTANCE.createExchange(classOf[BitstampExchange].getName)

  // Interested in the public market data feed (no authentication)
  val marketDataService = bitstamp.getMarketDataService

  generic(marketDataService)
  raw(marketDataService.asInstanceOf[BitstampMarketDataServiceRaw])

  Gdax.history(ZonedDateTime.of(LocalDateTime.now, TimeZone.getDefault.toZoneId).minusDays(30))

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

}

