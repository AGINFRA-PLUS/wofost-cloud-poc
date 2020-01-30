name := "wofost-cloud"
version := "0.5.0"
organization := "Wageningen University and Research"
scalaVersion := "2.12.10"
test in assembly := {}
assemblyJarName in assembly := "wofost-cloud.jar"

val akkaVersion = "2.5.26"
val akkaHttpVersion = "10.1.10"
val log4jVersion = "2.12.1"

// compiler options
scalacOptions += "-Ypartial-unification"

// scala xml support
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0"

// html DSL
libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.7.0"

// scalatest and scalacheck
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"

// cats
libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"

// akka modules
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
)

// akka sl4j logback for logging
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

// dependencies for AgroDataCube (reading from PostgreSQL at the moment)
libraryDependencies += "postgresql" % "postgresql" % "9.1-901-1.jdbc4"

// https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.28.0"

// dependencies for WOFOST-WISS (from https://mvnrepository.com/)
libraryDependencies += "commons-cli" % "commons-cli" % "1.4"
libraryDependencies += "commons-logging" % "commons-logging" % "1.2"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % log4jVersion
libraryDependencies += "org.apache.logging.log4j" % "log4j-jcl" % log4jVersion
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % log4jVersion
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.9"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "net.jafama" % "jafama" % "2.3.1"
libraryDependencies += "org.yaml" % "snakeyaml" % "1.25"
