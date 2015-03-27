/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */
package kafka

import akka.actor.Props
import akka.actor.{Actor, ActorLogging}
import java.util.Properties
import java.util.UUID
import geometry.GpsPos
import kafka.consumer._
import kafka.serialization.TraceEntryAvroDecoder
import kafka.serializer.DefaultDecoder
import kafka.serializer.StringDecoder
import org.joda.time.DateTime
import play.api.libs.json.{Json, JsValue}
import scala.util.Try

case class TraceEntry(id: String, timestamp: DateTime, lat: BigDecimal, lng: BigDecimal, isOccupied: Boolean)

object TraceEntry {

  private[this] case class DataChannel(channel: String, value: JsValue)
  private[this] implicit val DataChannelReads = Json.reads[DataChannel]

  import play.api.libs.json._
  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  private implicit val GpsPosReads = ((__ \ "lat").read[BigDecimal] and (__ \ "lon").read[BigDecimal])(GpsPos.apply _)

  implicit val TraceEntryReads = new Reads[TraceEntry] {
    override def reads(json: JsValue): JsResult[TraceEntry] = {
      for {
        id <- (json \ "vin").validate[String]
        timestamp <- (json \ "timestamp").validate[DateTime]
        data <- (json \ "data").validate[List[DataChannel]]
        GpsPos(lat, lng) <- data.find( _.channel == "location" ).map( _.value.validate[GpsPos] ).getOrElse( JsError("No location found.") )
        occupancy <- data.find( _.channel == "occupancy" ).map( _.value.validate[Int].map( _ == 1 ) ).getOrElse( JsError("No occupacy found") )
      } yield TraceEntry(id, timestamp, lat, lng, occupancy)
    }
  }
}

object KafkaConsumerActor {
  object Next
  def props(zookeeperConnect: String, topic: String, startFromBeggining: Boolean) =
    Props( classOf[KafkaConsumerActor], zookeeperConnect, topic, startFromBeggining)
}

class KafkaConsumerActor(zookeeperConnect: String, topic: String, startFromBeggining: Boolean) extends Actor with akka.routing.Listeners with ActorLogging {

  val props = new Properties()
  props.put("group.id", UUID.randomUUID().toString())
  props.put("zookeeper.connect", zookeeperConnect)
  props.put("auto.offset.reset", if(startFromBeggining) "smallest" else "largest")
  props.put("consumer.timeout.ms", "500")
  val config = new ConsumerConfig(props)
  val connector = Consumer.create(config)

  val filterSpec = new Whitelist(topic)
  val iterator = connector.createMessageStreamsByFilter(filterSpec, 1, new StringDecoder(), new TraceEntryAvroDecoder())(0).iterator()

  self ! KafkaConsumerActor.Next
  log.info( "Kafka consumer started" )

  def dispatch : Receive = {
    case KafkaConsumerActor.Next  =>
      try {
        if( iterator.hasNext() ) {
          val msg = iterator.next().message()
          log.debug( "Message from kafka: " + msg )
          gossip( msg )
        }
      } catch {
        case e: ConsumerTimeoutException => log.debug( e.getMessage )
      }
      self ! KafkaConsumerActor.Next
        
  }

  def receive = listenerManagement orElse dispatch

  override def postStop() = connector.shutdown()

}

