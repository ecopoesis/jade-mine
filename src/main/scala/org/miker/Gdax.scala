package org.miker

import java.net.URI
import java.sql.Connection
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.concurrent.TimeUnit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.{CloseableHttpClient, DefaultHttpClient, HttpClientBuilder}
import org.apache.http.entity.ByteArrayEntity
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.http.client.utils.URIBuilder
import org.apache.http.util.EntityUtils
import anorm._
import scalikejdbc.ConnectionPool

object Gdax {
  val Url = "api.gdax.com"
  val ChunkSize = 200

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.registerModule(new JavaTimeModule)
  mapper.enable(SerializationFeature.INDENT_OUTPUT)
  mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

  case class Ohlc(time: ZonedDateTime, open: BigDecimal, high: BigDecimal, low: BigDecimal, close: BigDecimal, volume: BigDecimal)

  def history(since: ZonedDateTime): Unit = {
    var start = since

    implicit val conn: Connection = ConnectionPool.borrow()

    while (start.isBefore(ZonedDateTime.of(LocalDateTime.now, TimeZone.getDefault.toZoneId))) {
      val end = start.plusMinutes(ChunkSize)
      println("Getting " + start + " data from GDAX")
      history(start, end).foreach(ohlc => {
        SQL("insert into ohlc(exchange, time, open, high, low, close, volume) values ('GDAX', {time}, {open}, {high}, {low}, {close}, {volume}) ON CONFLICT ON CONSTRAINT ohlc_pkey DO NOTHING")
          .on(
            "time" -> ohlc.time,
            "open" -> ohlc.open,
            "high" -> ohlc.high,
            "low" -> ohlc.low,
            "close" -> ohlc.close,
            "volume" -> ohlc.volume
          ).execute
      })
      start = end
      TimeUnit.MILLISECONDS.sleep(500)
    }
  }

  private def history(start: ZonedDateTime, end: ZonedDateTime) = {
    val path = "/products/BTC-USD/candles"
    val client = HttpClientBuilder.create().build()
    val uri = new URIBuilder()
      .setScheme("https")
      .setHost(Url)
      .setPath(path)
      .setParameter("start", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      .setParameter("end", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      .setParameter("granularity", "60")
      .build()

    val get = new HttpGet(uri)
    val response = client.execute(get)
    val data = mapper.readValue[Seq[Seq[BigDecimal]]](EntityUtils.toString(response.getEntity))

    data.map(r => {
      Ohlc(
        time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(r.head.longValue()), ZoneId.of("UTC")),
        low = r(1),
        high = r(2),
        open = r(3),
        close = r(4),
        volume = r(5)
      )
    })
  }
}



