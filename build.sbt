name := """data-api"""

version := "0.1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

val SparkVersion = "1.2.0"

val spark = Seq(
  "com.datastax.spark"  %% "spark-cassandra-connector"  % "1.2.0-alpha2",
  "org.apache.spark"    %% "spark-streaming-kafka"      % SparkVersion exclude("com.google.guava", "guava") exclude("org.apache.spark", "spark-core"),

  "org.apache.spark" %% "spark-core" % SparkVersion,
  "org.apache.spark" %% "spark-streaming" % SparkVersion
)


libraryDependencies ++= spark ++ Seq(
  "com.github.nscala-time"    %% "nscala-time"    % "1.8.0",
  "org.apache.kafka"          %% "kafka"          % "0.8.1" excludeAll(ExclusionRule("org.slf4j"))
)

enablePlugins(DockerPlugin)

dockerBaseImage := "dockerfile/java:oracle-java8"

dockerExposedPorts in Docker := Seq(9000)

dockerRepository in Docker := Some("advancedtelematic")

packageName in Docker := "rvi_data_api"

dockerUpdateLatest in Docker := true