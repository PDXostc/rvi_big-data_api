/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */
package controllers

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.{Listen, Deafen}
import java.io.File
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.duration.Duration
import scala.util.Try
import concurrent.duration._
import kafka.TraceEntry

object WsHandlerActor {
  def props( out : ActorRef ) : Props = Props( classOf[WsHandlerActor], out )
}

class WsHandlerActor(out: ActorRef) extends Actor with ActorLogging {
  import concurrent.duration._

  val feed = context.actorSelection("/user/kafka-consumer")

  override def preStart() = feed ! Listen(self)

  override def receive: Receive = {
    case msg: String =>
      out ! "Got it" + msg
    case entry: TraceEntry =>
      out ! s"""{:id ${entry.id} :lat ${entry.lat} :lng ${entry.long} :occupied ${entry.isOccupied}}"""
  }

  override def postStop() = {
    log.info( "Socket closed!" )
    feed ! Deafen( self )
  }
}

object Application extends Controller {

  val websocket = WebSocket.acceptWithActor[String, String] { request => out =>
    WsHandlerActor.props( out )
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}
