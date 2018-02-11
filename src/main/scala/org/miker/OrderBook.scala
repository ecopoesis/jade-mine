package org.miker

import java.util.stream.Collectors

import info.bitrich.xchangestream.core.StreamingExchange
import io.reactivex.schedulers.Schedulers
import it.unimi.dsi.fastutil.objects.{ObjectLinkedOpenHashSet, ObjectSortedSets}
import org.knowm.xchange.currency.CurrencyPair

class OrderBook(streamingExchange: StreamingExchange) {
  var asks = new ObjectLinkedOpenHashSet[BigDecimal]
  var bids = new ObjectLinkedOpenHashSet[BigDecimal]

  val bookStream = streamingExchange.getStreamingMarketDataService.getOrderBook(CurrencyPair.BTC_USD).observeOn(Schedulers.newThread()).subscribe(b => {
    val asks = new ObjectLinkedOpenHashSet[BigDecimal]
    val newAsks = b.getAsks.stream().map[BigDecimal](order => order.getLimitPrice).collect(Collectors.toList())
    asks.addAll(newAsks)

    val bids = new ObjectLinkedOpenHashSet[BigDecimal]
    val newBids = b.getBids.stream().map[BigDecimal](order => order.getLimitPrice).collect(Collectors.toList())
    bids.addAll(newBids)

    this.asks = asks
    this.bids = bids
  })

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns")
    result
  }
}
