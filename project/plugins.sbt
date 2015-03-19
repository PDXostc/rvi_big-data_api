resolvers ++= Seq(
  "sbt-plugin-releases-repo" at "http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases",
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
)

// https://github.com/cavorite/sbt-avro
addSbtPlugin("com.cavorite" % "sbt-avro" % "0.3.2")

// https://github.com/jrudolph/sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M5")
