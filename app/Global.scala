import play.api._
import play.api.libs.concurrent.Akka

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    val zookeeper = app.configuration.getString("zookeeper.connect").getOrElse("localhost:2181")
    Akka.system(app).actorOf(kafka.KafkaConsumerActor.props(zookeeper, "gps_trace", false).withDispatcher("kafka-dispatcher"), "kafka-consumer")
  }
}
