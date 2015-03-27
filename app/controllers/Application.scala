/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */
package controllers

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.{Listen, Deafen}
import java.io.File
import akka.util.Timeout
import controllers.QueryProcessorActor.{GetOldestEntryDate, TraceByTime, GetFleetPosition, GetPickupsDropoffs, PickupDropoff}
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import play.api.libs.concurrent.Akka
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.duration.Duration
import scala.util.Try
import concurrent.duration._
import geometry.{Polygon, GpsPos}
import kafka.{TraceWithSpeed, TraceEntry}

object WsHandlerActor {
  def props( out : ActorRef ) : Props = Props( classOf[WsHandlerActor], out )
}

class WsHandlerActor(out: ActorRef) extends Actor with ActorLogging {
  import concurrent.duration._

  val feed = context.actorSelection("/user/kernel/kafka-consumer")

  override def preStart() = feed ! Listen(self)

  import play.api.libs.json._

  case class SpeedFilter(min: BigDecimal, max: BigDecimal) {
    def check( speed: BigDecimal ) : Boolean = speed >= min && speed <= max
  }

  case class AreaFilter(polygon: Vector[GpsPos]) {
    def check( pos : GpsPos) : Boolean = Polygon.isPointInPolygon( pos, polygon)
  }

  object AreaFilter {
    def default = new AreaFilter(Vector.empty) {
      override def check( pos : GpsPos ) = true
    }
  }

  object SpeedFilter {
    val default = SpeedFilter( BigDecimal(0), BigDecimal(250) )
  }
  implicit val speedFilterReads = Json.reads[SpeedFilter]
  implicit val gpsPosReads = Json.reads[GpsPos]

  def streaming(speedFilter: SpeedFilter, areaFilter: AreaFilter) : Receive = {
    case msg: String =>
      Json.parse( msg ).validate[SpeedFilter] match {
        case JsSuccess(filter, _) => context become streaming( filter, areaFilter )
        case error : JsError => log.warning( s"$msg is not speed filter. Error: " + Json.stringify( JsError.toFlatJson(error) ) )
      }
      Json.parse( msg ).validate[List[GpsPos]] match {
        case JsSuccess(filter, _) => context become streaming( speedFilter, AreaFilter(filter.toVector) )
        case error : JsError => log.warning( s"$msg is not area filter. Error: " + Json.stringify( JsError.toFlatJson(error) ) )
      }
    case TraceWithSpeed(entry, speed) =>
      if( speedFilter.check( speed ) && areaFilter.check( GpsPos(entry.lat, entry.lng) ))
        out ! s"""{:id ${entry.id} :lat ${entry.lat} :lng ${entry.lng} :occupied ${entry.isOccupied}}"""
  }

  override def receive: Receive = streaming( SpeedFilter.default, AreaFilter.default )

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

  implicit val reqTimeout: Timeout = 53.seconds

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

  val PacificTZ = DateTimeZone.forID( "US/Pacific" )

  def dateTime( time : Long ) =
    new DateTime(time).withZoneRetainFields( PacificTZ ).withZone( DateTimeZone.UTC )


  import akka.pattern.ask
  def oldestEntryDate = Action.async {
    (kernel ? GetOldestEntryDate).mapTo[DateTime].map( d => Ok(d.toString))
  }

  def toPolygon( points : List[String]) : Vector[GpsPos] = {
    points.map { s =>
      val latLng = s.split(",").map(BigDecimal.apply)
      GpsPos(latLng(0), latLng(1))
    }.toVector
  }

  def fleetPosition(time: Long, area: List[String]) = Action.async {
    val dt = dateTime( time )
    (kernel ? GetFleetPosition( dt, toPolygon( area ) ))
      .mapTo[Seq[TraceByTime]].map( traces => Ok( Json.toJson( traces )  ).withHeaders( "Access-Control-Allow-Origin" -> "*" ) )
  }

  def pickups(dateFrom: Long, dateTo: Long, hourFrom: Int, hourTo: Int, area: List[String]) = Action.async {
    (kernel ? GetPickupsDropoffs( dateTime(dateFrom), dateTime(dateTo), hourFrom, hourTo, toPolygon(area) ))
      .mapTo[Seq[PickupDropoff]]
      .map( _.map( x => Array(x.lat, x.lng)) )
      .map( xs => Ok( Json.toJson( xs ) ).withHeaders( "Access-Control-Allow-Origin" -> "*" )  )
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}
