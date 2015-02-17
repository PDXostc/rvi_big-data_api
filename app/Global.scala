/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */
import controllers.NodeKernel
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import play.api._
import play.api.libs.concurrent.Akka

object Global extends GlobalSettings {

  var sparkStreaming : Option[StreamingContext] = None

  override def onStart(app: Application) {
    val zookeeper = app.configuration.getString("zookeeper.connect").getOrElse("localhost:2181")
    val akka = Akka.system(app)
    akka.actorOf(kafka.KafkaConsumerActor.props(zookeeper, "gps_trace", false).withDispatcher("kafka-dispatcher"), "kafka-consumer")
    val ssc = startSpark( app.configuration )
    sparkStreaming = Some( ssc )
    akka.actorOf(NodeKernel.props(ssc), "kernel")
  }

  private def startSpark(configuration: Configuration): StreamingContext = {
    val conf = new SparkConf().setAppName(getClass.getSimpleName)
      .setMaster("local[3]")
      .set("spark.cassandra.connection.host", configuration.getString("cassandra.host").getOrElse("localhost"))
      .set("spark.cleaner.ttl", (3600*2).toString)

    val sc = new SparkContext(conf)

    /** Creates the Spark Streaming context. */
    new StreamingContext(sc, Milliseconds(500))
  }

  override def onStop(app: Application) = {
    sparkStreaming.foreach( _.stop() )
  }
}
