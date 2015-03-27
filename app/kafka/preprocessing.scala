package kafka

import java.util.concurrent.ConcurrentHashMap
import java.util.{Date, Properties, UUID}

import com.twitter.bijection.Injection
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.{Encoder, StringDecoder}
import kafka.utils.VerifiableProperties
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.joda.time.DateTime
import play.api.libs.json.{JsError, JsResult, Json}
import spire.algebra.Trig
import geometry.GpsPos

object KafkaSink {
  import collection.JavaConversions._
  import scala.collection.concurrent.{Map => CMap}
  import serialization.entryInjection

  class TraceEntryAvroEncoder(props: VerifiableProperties = null) extends Encoder[TraceEntryRecord] {
    override def toBytes(t: TraceEntryRecord): Array[Byte] = Injection[TraceEntryRecord, Array[Byte]]( t )
  }


  type Key = String
  type Val = TraceEntryRecord

  private[this] lazy val producers : CMap[Properties, Producer[Key, Val]]
      = new ConcurrentHashMap[Properties, Producer[Key, Val]]()

  private[this] def mkProducer( properties: Properties ) : Producer[Key, Val] = {
    new Producer[Key, Val]( new ProducerConfig( properties ) )
  }

  private[this] implicit def toKeyedMessage( rec: Val ) : KeyedMessage[Key, Val] =
    KeyedMessage("TraceEntryRecord", rec.getId, rec.getId, rec)

  def publish( kafkaConfig : Properties)( record : Val ) : Unit = {
    producers.getOrElseUpdate( kafkaConfig, mkProducer(kafkaConfig) ).send( record )
  }

  Runtime.getRuntime.addShutdownHook( new Thread( new Runnable {
    override def run(): Unit = producers.foreach( _._2.close() )
  }))
}

object DataModel {

  case class TraceByCar(id: String, year: Int, month: Int, day: Int, hour: Int, date: Date, lat: BigDecimal, lng: BigDecimal, isOccupied: Boolean)

  case class TraceByTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, id: String, lat: BigDecimal, lng: BigDecimal, isOccupied: Boolean)

  case class PickupDropoff(year: Int, date: Date, id: String, lat: BigDecimal, lng: BigDecimal, isPickup: Boolean)

  object TraceByCar {
    def fromTraceEntry( te: TraceEntry ) = {
      TraceByCar(
        id = te.id,
        year = te.timestamp.year().get,
        month = te.timestamp.monthOfYear().get,
        day = te.timestamp.dayOfMonth().get,
        hour = te.timestamp.hourOfDay().get(),
        date = te.timestamp.toDate(),
        lat = te.lat,
        lng = te.lng,
        isOccupied = te.isOccupied
      )
    }
  }

  object TraceByTime {
    def fromTraceEntry(te : TraceEntry) = {
      TraceByTime(
        id = te.id,
        year = te.timestamp.year().get,
        month = te.timestamp.monthOfYear().get,
        day = te.timestamp.dayOfMonth().get,
        hour = te.timestamp.hourOfDay().get(),
        minute = te.timestamp.minuteOfHour().get,
        lat = te.lat,
        lng = te.lng,
        isOccupied = te.isOccupied
      )
    }
  }


}

object PreprocessingStream {

  import spire.implicits._
  import spire.math._

  def deltaLambda( pos1: GpsPos, pos2: GpsPos ) = {
    val delta = pos2.lng - pos1.lng
    Trig[BigDecimal].toRadians(delta)
  }

  val R = BigDecimal( 6371009 )

  def distance( from: GpsPos, to: GpsPos )(implicit trig : Trig[BigDecimal]) : BigDecimal = {
    val phi1 = trig.toRadians( from.lat )
    val phi2 = trig.toRadians( to.lat )
    val deltaPhi = trig.toRadians( to.lat - from.lat )

    val a = sin( deltaPhi / 2 ).pow( 2 ) + cos( phi1 ) * cos( phi2 ) * sin( deltaLambda(from, to) / 2).pow( 2 )
    val c = 2 * atan2( sqrt(a), sqrt(BigDecimal( 1 ) - a) )
    R * c
  }

  implicit def entryPos( entry : TraceEntry ) : GpsPos = GpsPos( entry.lat, entry.lng )

  def speed( from : TraceEntry, to: TraceEntry ) : BigDecimal = {
    import com.github.nscala_time.time.Imports._
    val l = distance( from, to )
    val time = (from.timestamp to to.timestamp).toDuration.getStandardSeconds
    (l / time) * 1000 / 360
  }

  def parseCsv(str: String) : TraceEntry = {
    val fields = str.split(" ")
    TraceEntry(
      id = fields(0),
      timestamp = new DateTime( fields(4).toLong * 1000 ),
      lat = BigDecimal( fields(1) ),
      lng = BigDecimal( fields(2) ),
      isOccupied = fields(3) == "true"
    )
  }

  def parseJson( str : String ) : JsResult[TraceEntry] = Json.fromJson[TraceEntry]( Json.parse(str) )

  def start(ssc: StreamingContext, zookeeperConnect: String, brokerList: List[String]) {
    import org.apache.spark.streaming.StreamingContext._
    import serialization.{EntryToRecord, TraceEntryAvroDecoder}


    val kafkaParams = Map("group.id" -> UUID.randomUUID().toString(), "zookeeper.connect" -> zookeeperConnect,
      "consumer.timeout.ms" -> "1000")

    val inputStream = KafkaUtils.createStream[String, String, StringDecoder, StringDecoder](
      ssc, kafkaParams, Map("gps_trace" -> 1), StorageLevel.MEMORY_ONLY)

    val props = new java.util.Properties()
    props.put("metadata.broker.list", brokerList.mkString(","))
    props.put("key.serializer.class", "kafka.serializer.StringEncoder")
    props.put( "serializer.class", classOf[kafka.KafkaSink.TraceEntryAvroEncoder].getName )

    val parsedEntries = inputStream.map( x => x._1 -> parseJson(x._2) )

    val correctMessages = parsedEntries.filter( _._2.isSuccess ).map( x => (x._1, x._2.get) )

    val entriesStream = correctMessages.map( _._2 )

    import kafka.DataModel._
    import com.datastax.spark.connector.streaming._

    entriesStream.map(TraceByCar.fromTraceEntry).saveToCassandra("rvi_demo", "trace_by_car")

    entriesStream.map(TraceByTime.fromTraceEntry).filter(_.minute % 5 == 0).saveToCassandra("rvi_demo", "traces_by_time")

    val invalidMessages = parsedEntries
      .filter( _._2.isError ).map( _._2.fold[String]( err => Json.stringify( JsError.toFlatJson( err ) ), _ => "" )  )
      .foreachRDD( _.foreach( println ))

    val speedStream = correctMessages.updateStateByKey[(Boolean, TraceWithSpeed)] { (xs : Seq[TraceEntry], acc : Option[(Boolean, TraceWithSpeed)]) =>
      (xs.reverse.toList, acc) match {
        case (Nil, Some((_, entry: TraceWithSpeed))) => Some( false, entry )
        case (first :: Nil, None ) => Some( ( true, TraceWithSpeed( first, 0)) )
        case (to :: Nil, Some((_, from))) => Some( (true, TraceWithSpeed( to, speed( from.entry, to) )) )
        case (to :: from :: _, _) => Some( (true, TraceWithSpeed( to, speed( from, to ))) )
        case (Nil, None) => None
      }
    }.filter(_._2._1).map( x => EntryToRecord(x._2._2) ).foreachRDD( rdd =>
      rdd.foreachPartition{ partition =>
        partition.foreach( KafkaSink.publish( props ) )
      })

    ssc.start()

  }

}
