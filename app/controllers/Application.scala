package controllers

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.{Listen, Deafen}
import java.io.File
import akka.util.Timeout
import controllers.QueryProcessorActor.{GetOldestEntryDate, TraceByTime, GetFleetPosition}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.concurrent.Akka
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

  import scala.concurrent.duration._
  import play.api.libs.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val kernel = Akka.system.actorSelection("/user/kernel")

  val websocket = WebSocket.acceptWithActor[String, String] { request => out =>
    WsHandlerActor.props( out )
  }

  implicit val reqTimeout: Timeout = 3.seconds

  val TraceWrite = Writes[TraceByTime] { trace =>
    Json.obj(
      "type" -> "Feature",
      "properties" -> Json.writes[TraceByTime].writes( trace ),
      "geometry" -> Json.obj(
        "type" -> "Point",
        "coordinates" -> Json.arr( trace.lng, trace.lat )
      )
    )
  }

  implicit val TracesWrite = Writes[Seq[TraceByTime]]  { seq =>
    Json.obj(
      "type" -> "FeatureCollection",
      "features" -> seq.map( TraceWrite.writes )
    )
  }

  import akka.pattern.ask
  def oldestEntryDate = Action.async {
    (kernel ? GetOldestEntryDate).mapTo[DateTime].map( d => Ok(d.toString))
  }

  def fleetPosition(time: Long) = Action.async {
    val dateTime = new DateTime(time)
    (kernel ? GetFleetPosition( dateTime ))
      .mapTo[Seq[TraceByTime]].map( traces => Ok( Json.toJson( traces )  ).withHeaders( "Access-Control-Allow-Origin" -> "*" ) )
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}
