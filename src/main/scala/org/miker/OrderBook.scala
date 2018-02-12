package org.miker

import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors

import info.bitrich.xchangestream.core.StreamingExchange
import io.reactivex.schedulers.Schedulers
import it.unimi.dsi.fastutil.objects.{ObjectAVLTreeSet, ObjectLinkedOpenHashSet, ObjectSortedSets}
import org.knowm.xchange.currency.CurrencyPair

class OrderBook(streamingExchange: StreamingExchange) {
  var asks = new AtomicReference(new ObjectAVLTreeSet[BigDecimal])
  var bids = new AtomicReference(new ObjectAVLTreeSet[BigDecimal])

  val bookStream = streamingExchange.getStreamingMarketDataService.getOrderBook(CurrencyPair.BTC_USD, 10.asInstanceOf[Object]).observeOn(Schedulers.newThread()).subscribe(b => {
     val asks = new ObjectAVLTreeSet[BigDecimal]
    val newAsks = b.getAsks.stream().map[BigDecimal](order => order.getLimitPrice).collect(Collectors.toList())
    asks.addAll(newAsks)

    val bids = new ObjectAVLTreeSet[BigDecimal]
    val newBids = b.getBids.stream().map[BigDecimal](order => order.getLimitPrice).collect(Collectors.toList())
    bids.addAll(newBids)

    this.asks.set(asks)
    this.bids.set(bids)
  })

  def findBid(): BigDecimal = {
    this.bids.get.last
  }

  def findAsk(): BigDecimal = {
    this.asks.get.first
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns")
    result
  }
}
