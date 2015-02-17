package controllers

import akka.actor.{Props, ActorLogging, Actor}
import org.apache.spark.streaming.StreamingContext
import org.joda.time.DateTime

object QueryProcessorActor {

  def props( ssc: StreamingContext ) = Props( classOf[QueryProcessorActor], ssc )

  case class GetFleetPosition(time: DateTime)

  case object GetOldestEntryDate

  case class TraceByTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, id: String, lat: BigDecimal, lng: BigDecimal, isOccupied: Boolean)

  case class VehiclePosition(id: String)
}

class QueryProcessorActor(ssc: StreamingContext) extends Actor with ActorLogging {
  import com.datastax.spark.connector._
  import controllers.QueryProcessorActor._
  import org.apache.spark.SparkContext._
  import akka.pattern.pipe

  import context.dispatcher

  implicit object DateTimeOrdering extends Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int =
      implicitly[Ordering[Long]].compare( x.getMillis, y.getMillis )
  }

  override def receive: Receive = {
    case GetFleetPosition(time) =>
      log.info(s"Fleet position at time $time")
      val respondTo = sender()
      ssc.sparkContext.cassandraTable[TraceByTime]( "rvi_demo", "traces_by_time" )
        .where("year = ? AND month = ? AND day = ? AND hour = ? and minute = ?", time.getYear, time.getMonthOfYear,
          time.getDayOfMonth, time.getHourOfDay, (time.getMinuteOfHour / 5) * 5)
        .collectAsync()
        .pipeTo( respondTo )

    case GetOldestEntryDate =>
      sender ! ssc.sparkContext.cassandraTable[(Int, Int, Int)]( "rvi_demo", "traces_by_time" )
        .select("year, month, day").map {
          case (y, m, d) => new DateTime(y, m, d, 0, 0)
        }.min()
  }
}
