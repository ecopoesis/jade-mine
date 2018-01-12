import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val xchange = Seq(
    "org.knowm.xchange" % "xchange-core" % "4.3.1",
    "org.knowm.xchange" % "xchange-gdax" % "4.3.1",
    "org.knowm.xchange" % "xchange-bitstamp" % "4.3.1"
  )
  lazy val httpclient = "org.apache.httpcomponents" % "httpclient" % "4.5.4"
  lazy val flyway = "org.flywaydb" % "flyway-core" % "5.0.3"
  lazy val postgres = "org.postgresql" % "postgresql" % "42.1.4"
  lazy val anorm = "com.typesafe.play" %% "anorm" % "2.5.3"
  lazy val scalikejdbc = "org.scalikejdbc" %% "scalikejdbc" % "3.1.0"
  lazy val commonsMath =  "org.apache.commons" % "commons-math3" % "3.6.1"
  lazy val jackson = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.2",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.9.2"
  )
}
