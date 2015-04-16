name := """data-api"""

version := "0.4.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

val SparkVersion = "1.2.1"

val AkkaVersion = "2.3.9"

val akka = Seq(
  "com.typesafe.akka"   %% "akka-actor" % AkkaVersion
)

val spark = Seq(
  "com.datastax.spark"  %% "spark-cassandra-connector"  % "1.2.0-rc3",
  "org.apache.spark" %% "spark-core" % SparkVersion exclude("com.google.guava", "guava") exclude( "org.slf4j", "slf4j-log4j12" ),
  "org.apache.spark" %% "spark-streaming" % SparkVersion
)

libraryDependencies ++= spark ++ Seq(
  "com.github.nscala-time"    %% "nscala-time"    % "1.8.0",
  "org.apache.kafka"          %% "kafka"          % "0.8.2.1",
  "org.spire-math" %% "spire" % "0.9.0"
)

enablePlugins(DockerPlugin)

dockerBaseImage := "dockerfile/java:oracle-java8"

dockerExposedPorts in Docker := Seq(9000)

dockerRepository in Docker := Some("advancedtelematic")

packageName in Docker := "rvi_data_api"

dockerUpdateLatest in Docker := true