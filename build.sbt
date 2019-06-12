organization in ThisBuild := "com.example"
scalaVersion in ThisBuild := "2.12.8"

lazy val `slick-batching-demo` = (project in file("."))
  .settings(name := "slick-batching-demo")
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % "3.3.0",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
      "org.slf4j" % "slf4j-api" % "1.7.26",
      "ch.qos.logback" % "logback-core" % "1.2.3",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.postgresql" % "postgresql" % "42.2.5"
    )
  )
