name := """data-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.github.nscala-time" %% "nscala-time" % "1.6.0",
  "org.apache.kafka" %% "kafka" % "0.8.2-beta"
)

enablePlugins(DockerPlugin)

dockerBaseImage := "dockerfile/java:oracle-java8"

dockerExposedPorts in Docker := Seq(9000)
