package kafka

import com.twitter.bijection.{Bijection, Injection}
import com.twitter.bijection.avro.SpecificAvroCodecs
import kafka.serializer.{Encoder, Decoder}
import kafka.utils.VerifiableProperties
import org.joda.time.DateTime

object serialization {
  implicit val entryInjection = SpecificAvroCodecs[TraceEntryRecord]

  class TraceEntryAvroDecoder(props: VerifiableProperties = null) extends Decoder[TraceWithSpeed] {
    override def fromBytes(bytes: Array[Byte]): TraceWithSpeed = EntryToRecord.andThen( entryInjection ).invert(bytes).get
  }

  implicit val EntryToRecord = Bijection.build[TraceWithSpeed, TraceEntryRecord](e =>
    TraceEntryRecord.newBuilder()
      .setId(e.entry.id)
      .setTimestamp(e.entry.timestamp.getMillis)
      .setLat(e.entry.lat.doubleValue())
      .setLng(e.entry.long.doubleValue())
      .setIsOccupied(e.entry.isOccupied)
      .setSpeed(e.speed.doubleValue())
      .build()
  )(r =>
    TraceWithSpeed(entry = TraceEntry(
      id = r.getId,
      timestamp = new DateTime(r.getTimestamp),
      long = BigDecimal(r.getLng),
      lat = BigDecimal(r.getLat),
      isOccupied = r.getIsOccupied
    ), speed = BigDecimal(r.getSpeed))
  )

}

case class TraceWithSpeed( entry: TraceEntry, speed: BigDecimal )