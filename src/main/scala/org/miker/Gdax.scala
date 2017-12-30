package org.miker

import java.net.URI
import java.time.ZonedDateTime

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

  case class HistoryRequest(start: ZonedDateTime, end: ZonedDateTime, granularity: Int = 60)

  def history(since: ZonedDateTime) = {
    val path = "/products/BTC-USD/candles"
    val client = HttpClientBuilder.create().build()

    val uri = new URIBuilder()
      .setScheme("https")
      .setHost(url)
      .setPath(path)
      .setParameter("start", "2017-12-28T01:00:00Z")
      .setParameter("end", "2017-12-28T02:00:00Z")
      .setParameter("granularity", "60")
      .build()

    val get = new HttpGet(uri)
/*
    val request = HistoryRequest(since, since.plusHours(1))

    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule)
    mapper.enable(SerializationFeature.INDENT_OUTPUT)
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
*/
    //val entity = new ByteArrayEntity(mapper.writeValueAsString(request).getBytes("UTF-8"))
    //get.setEntity(entity)


    val response = client.execute(get)
    println(EntityUtils.toString(response.getEntity))
  }
}

