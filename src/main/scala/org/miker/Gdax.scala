package org.miker

import java.net.URI
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.{DefaultHttpClient, HttpClientBuilder}
import org.apache.http.entity.ByteArrayEntity
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.http.client.utils.URIBuilder
import org.apache.http.util.EntityUtils

object Gdax {
  val url = "api.gdax.com"

  case class Ohlc(time: ZonedDateTime, open: BigDecimal, high: BigDecimal, low: BigDecimal, close: BigDecimal, volume: BigDecimal)

  def history(start: ZonedDateTime) = {
    val path = "/products/BTC-USD/candles"
    val client = HttpClientBuilder.create().build()

    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule)
    mapper.enable(SerializationFeature.INDENT_OUTPUT)
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)



    val uri = new URIBuilder()
      .setScheme("https")
      .setHost(url)
      .setPath(path)
      .setParameter("start", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      .setParameter("end", start.plusMinutes(200).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      .setParameter("granularity", "60")
      .build()

    val get = new HttpGet(uri)
    val response = client.execute(get)

    val typeFactory = mapper.getTypeFactory
    val data = mapper.readValue[Seq[Seq[BigDecimal]]](EntityUtils.toString(response.getEntity))

    data.foreach(r => {
      val ohlc = Ohlc(
        time = ZonedDateTime.ofInstant(Instant.ofEpochSecond(r.head.longValue()), ZoneId.of("UTC")),
        low = r(1),
        high = r(2),
        open = r(3),
        close = r(4),
        volume = r(5)
      )
      println(ohlc)
    })
  }
}



