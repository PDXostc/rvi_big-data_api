/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */
package kafka

import akka.actor.Props
import akka.actor.{Actor, ActorLogging}
import java.util.Properties
import java.util.UUID
import kafka.consumer._
import kafka.serialization.TraceEntryAvroDecoder
import kafka.serializer.DefaultDecoder
import kafka.serializer.StringDecoder
import org.joda.time.DateTime
import scala.util.Try

case class TraceEntry(id: String, timestamp: DateTime, lat: BigDecimal, long: BigDecimal, isOccupied: Boolean)

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

