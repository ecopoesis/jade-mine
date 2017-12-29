import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val xchange = Seq(
    "org.knowm.xchange" % "xchange-core" % "4.3.1",
    "org.knowm.xchange" % "xchange-gdax" % "4.3.1"
  )
}
