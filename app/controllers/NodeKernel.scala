package controllers

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.spark.streaming.StreamingContext

object NodeKernel {
  def props(ssc: StreamingContext, zookeeper: String) = Props( classOf[NodeKernel], ssc, zookeeper)
}

class NodeKernel(ssc: StreamingContext, zookeeper: String) extends Actor with ActorLogging {

  val queryProcessor = context.actorOf( QueryProcessorActor.props( ssc ), "query-processor" )
  val kafkaConsumer = context.actorOf(kafka.KafkaConsumerActor.props(zookeeper, "gps_trace", false).withDispatcher("kafka-dispatcher"), "kafka-consumer")

  override def receive: Receive = {
    case m =>
      queryProcessor.forward(m)
  }

}
