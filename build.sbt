name := """data-api"""

version := "0.2.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.5"

val SparkVersion = "1.3.0"

val bijectionVersion = "0.7.1"

val spark = Seq(
  "com.datastax.spark"  %% "spark-cassandra-connector"  % "1.2.0-alpha3",
  "org.apache.spark"    %% "spark-streaming-kafka"      % SparkVersion exclude("com.google.guava", "guava") exclude("org.apache.spark", "spark-core") exclude("org.apache.kafka", "kafka"),
  "org.apache.spark" %% "spark-core" % SparkVersion,
  "org.apache.spark" %% "spark-streaming" % SparkVersion
)

val bijection = Seq(
  "com.twitter" %% "bijection-core" % bijectionVersion,
  "com.twitter" %% "bijection-avro" % bijectionVersion
)

libraryDependencies ++= spark ++ bijection ++ Seq(
  "com.github.nscala-time"    %% "nscala-time"    % "1.8.0",
  "org.apache.kafka"          %% "kafka"          % "0.8.2.1" excludeAll(ExclusionRule("org.slf4j")),
  "org.spire-math" %% "spire" % "0.9.0"
)

enablePlugins(DockerPlugin)

dockerBaseImage := "dockerfile/java:oracle-java8"

dockerExposedPorts in Docker := Seq(9000)

dockerRepository in Docker := Some("advancedtelematic")

packageName in Docker := "rvi_data_api"

dockerUpdateLatest in Docker := true

seq(sbtavro.SbtAvro.avroSettings : _*)

// Configure the desired Avro version.  sbt-avro automatically injects a libraryDependency.
(version in avroConfig) := "1.7.6"

(sourceDirectory in avroConfig) <<= (baseDirectory in Compile)(_ / "avro")

(stringType in avroConfig) := "String"