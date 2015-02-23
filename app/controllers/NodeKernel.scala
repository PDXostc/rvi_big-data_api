package controllers

import akka.actor.{Props, Actor, ActorLogging}
import controllers.QueryProcessorActor.GetFleetPosition
import org.apache.spark.streaming.StreamingContext

object NodeKernel {
  def props(ssc: StreamingContext) = Props( classOf[NodeKernel], ssc)
}

class NodeKernel(ssc: StreamingContext) extends Actor with ActorLogging {

  val queryProcessor = context.actorOf( QueryProcessorActor.props( ssc ), "query-processor" )

  override def receive: Receive = {
    case m =>
      queryProcessor.forward(m)
  }

}
