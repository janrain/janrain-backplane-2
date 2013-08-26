package com.janrain.backplane.server1.model

import com.janrain.backplane.common.model.{BackplaneMessage, MessageField, MessageFieldEnum, Message}
import com.janrain.servlet.InvalidRequestException
import com.janrain.backplane.common.{BackplaneServerException, DateTimeUtils}
import java.util.Date
import com.janrain.util.{Loggable, RandomUtils}
import org.codehaus.jackson.map.ObjectMapper
import java.io.IOException
import scala.collection.JavaConversions._
import scala.collection.JavaConversions
import com.janrain.backplane.dao.LegacySupport

/**
 * @author Johnny Bufu
 */
class Backplane1Message(data: Map[String,String]) extends BackplaneMessage(data, Backplane1MessageFields.values)
  with LegacySupport[com.janrain.backplane.server.BackplaneMessage] {

  def this(bus: String, channel: String, defaultExpireSeconds: Int, maxExpireSeconds: Int, upstreamData: java.util.Map[String,AnyRef]) =
    this(
      Backplane1Message.processExpire(
        Backplane1Message.parseUpstreamData(upstreamData.toMap), defaultExpireSeconds, maxExpireSeconds )
        ++
        Map(
          Backplane1MessageFields.ID.name -> Backplane1Message.generateMessageId(new Date),
          Backplane1MessageFields.BUS.name -> bus,
          Backplane1MessageFields.CHANNEL_NAME.name -> channel
        ))

  def this(javaData: java.util.Map[String,String]) = this(LegacySupport.fromLegacy(javaData))

  def idField = Backplane1MessageFields.ID

  def asLegacy = new com.janrain.backplane.server.BackplaneMessage(mapAsJavaMap(this))

  def asFrame(version: String): Map[String, AnyRef] = {
    val (upstream, topFrame) = Backplane1MessageFields.values
      .map(field => field -> field.frameOutput(id, version, get(field))).toMap
      .filter { case (k,v) => v.isDefined } .mapValues(_.get)
      .partition(_._1.isUpstream)

    topFrame.map(kv => kv._1.name -> kv._2) ++ Map("message" -> JavaConversions.mapAsJavaMap(upstream.map(kv => kv._1.name -> kv._2)))
  }

  def bus: String = get(Backplane1MessageFields.BUS)
    .getOrElse(throw new IllegalStateException("bus field missing from bp2 message, should have failed validation"))

  def channel: String = get(Backplane1MessageFields.CHANNEL_NAME)
    .getOrElse(throw new IllegalStateException("channel field missing from bp2 message, should have failed validation"))

  def sticky = get(Backplane1MessageFields.STICKY).exists(_.toBoolean)

  def expiration: String = get(Backplane1MessageFields.EXPIRE)
    .getOrElse(throw new IllegalStateException("expire field missing from bp2 message, should have failed validation"))
}

object Backplane1Message {

  private final val MESSAGE_ID_RANDOM_LENGTH = 10
  private final val MESSAGE_ID_LEGACY_RANDOM_LENGTH = 10

  private final val UPSTREAM_FIELDS = Backplane1MessageFields.values.filter(_.isUpstream).toList
  private final val UPSTREAM_FIELD_NAMES = UPSTREAM_FIELDS.map(_.name)

  def generateMessageId(date: Date) =
    DateTimeUtils.ISO8601.get.format(date) + "-" + RandomUtils.randomString(MESSAGE_ID_RANDOM_LENGTH)

  def legacyIdLength: Int = 25 + MESSAGE_ID_LEGACY_RANDOM_LENGTH // ISO8601 length + "-" + random_length

  private def parseUpstreamData(upstreamData: Map[String,AnyRef]): Map[String,String] = {
    Backplane1Message.checkUpstreamFields(upstreamData)
    Backplane1Message.UPSTREAM_FIELDS
      .map(field => field.name -> field.parseUpstreamValue(upstreamData)).toMap
      .filter { case (k,v) => v.isDefined } .mapValues(_.get)
  }

  private def checkUpstreamFields(upstreamData: Map[String,AnyRef]) {
    upstreamData.keySet.foreach( field =>
      if (! UPSTREAM_FIELD_NAMES.contains(field))
        throw new InvalidRequestException("Invalid field in upstream message: " + field))
  }

  private def processExpire(data: Map[String,String], defaultExpSeconds: Int, stickyExpSeconds: Int): Map[String,String] = {
    val sticky = data.get(Backplane1MessageFields.STICKY.name).exists(_.equalsIgnoreCase(true.toString))
    val exp = data.get(Backplane1MessageFields.EXPIRE.name).getOrElse(null)
    data ++ Map(Backplane1MessageFields.EXPIRE.name -> DateTimeUtils.processExpireTime(sticky, exp, defaultExpSeconds, stickyExpSeconds))
  }
}

object Backplane1MessageFields extends MessageFieldEnum with Loggable {

  type BackplaneMessageField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
    def isUpstream = false
    def parseUpstreamValue(upstreamData: Map[String,AnyRef]): Option[String] = upstreamData.get(name).map(_.toString)
    def frameOutput( msgId: String, version: String, fieldValue: Option[String]): Option[AnyRef] = fieldValue
  }

  val ID = new BackplaneMessageField { def name = "id"
    override def frameOutput(msgId: String, version: String, fieldValue: Option[String]) = None
    override def validateLong(fieldValue: Option[String]) {
      super.validateLong(fieldValue)
      fieldValue.foreach(BackplaneMessage.dateFromId)
    }
  }

  val CHANNEL_NAME = new BackplaneMessageField { def name = "channel_name" }

  val BUS = new BackplaneMessageField { def name = "bus" }

  val STICKY = new BackplaneMessageField { def name = "sticky"
    override def isUpstream = true
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateBoolean(fieldValue)
    }
    override def parseUpstreamValue(upstreamData: Map[String, AnyRef]) =
      upstreamData.get(name).map(_.toString).orElse(Option(false.toString))
  }

  val EXPIRE = new BackplaneMessageField { def name = "expire"
    override def isUpstream = true
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateInternetDate(fieldValue)
    }

    override def frameOutput(msgId: String, version: String, fieldValue: Option[String]) =
      if ("1.3" == version) fieldValue
      else None
  }

  val SOURCE = new BackplaneMessageField { def name = "source"
    override def isUpstream = true
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateUrl(fieldValue)
    }
  }

  val TYPE = new BackplaneMessageField { def name = "type"
    override def isUpstream = true
  }

  val PAYLOAD = new BackplaneMessageField { def name = "payload"
    override def isUpstream = true
    override def parseUpstreamValue(upstreamData: Map[String, AnyRef]) = {
      try {
        upstreamData.get(name).map(new ObjectMapper().writeValueAsString(_))
      } catch {
        case e: IOException => {
          val errMsg = "Error serializing message payload: " + e.getMessage
          logger.error(errMsg)
          throw new BackplaneServerException(errMsg, e)
        }
      }
    }

    override def frameOutput(msgId: String, version: String, fieldValue: Option[String]) = {
      try {
        Some(new ObjectMapper().readValue(fieldValue.getOrElse(null), classOf[AnyRef])) // un-quote the value
      } catch {
        case e: IOException => {
          val errMsg = "Error deserializing message payload: " + e.getMessage
          logger.error(errMsg)
          throw new BackplaneServerException(errMsg, e)
        }
      }
    }
  }

}
