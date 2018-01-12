import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "org.miker",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "jade-mine",
    assemblyJarName in assembly := "jade-mine.jar",
    libraryDependencies ++= xchange,
    libraryDependencies += httpclient,
    libraryDependencies ++= jackson,
    libraryDependencies += flyway,
    libraryDependencies += postgres,
    libraryDependencies += anorm,
    libraryDependencies += scalikejdbc,
    libraryDependencies += commonsMath,
    libraryDependencies += scalaTest % Test
  )

// see https://github.com/ronmamo/reflections/issues/169
val workaround = {
  sys.props += "packaging.type" -> "jar"
  ()
}