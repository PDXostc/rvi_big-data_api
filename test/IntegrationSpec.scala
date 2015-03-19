/**
 * Copyright 2015, ATS Advanced Telematic Systems GmbH
 * All Rights Reserved
 */

import java.io.File

import com.twitter.bijection.Injection
import com.twitter.bijection.avro.{SpecificAvroCodecs, GenericAvroCodecs}
import kafka.TraceEntryRecord
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.joda.time.DateTime
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Specification {

  "Application" should {

    "serialize and deserialize trace entry using bijection and avro" in {
      // new_askmecle.txt,2015-03-11T12:16:45.000+01:00,37.79991872746409906432235929024477,-122.4351238522933425846584727780817,false),31.7044277691886160671905518109585)
      val entry = TraceEntryRecord.newBuilder()
        .setId( "new_askmecle.txt" )
        .setTimestamp( DateTime.now().getMillis )
        .setLat(37.79991872746409906432235929024477)
        .setLng(-122.4351238522933425846584727780817)
        .setIsOccupied( false )
        .setSpeed( 31.70442776918861606719055181095 ).build()
      implicit val entryInjection = SpecificAvroCodecs[TraceEntryRecord]
      val bytes = Injection[TraceEntryRecord, Array[Byte]]( entry )
      val attempt = Injection.invert[TraceEntryRecord, Array[Byte]]( bytes )
      attempt.get == entry
    }
  }
}
