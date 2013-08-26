package com.janrain.backplane.server2.model

import com.janrain.backplane.common.model.{BackplaneMessage, MessageField, MessageFieldEnum, Message}
import com.janrain.backplane2.server.Scope
import com.janrain.backplane2.server.Scope.ScopeType._
import com.janrain.servlet.InvalidRequestException
import com.janrain.backplane.common.{BackplaneServerException, DateTimeUtils}
import java.util.Date
import com.janrain.util.Loggable
import org.codehaus.jackson.map.ObjectMapper
import java.io.IOException
import scala.collection.JavaConversions._
import com.janrain.backplane2.server.Scope.ScopeType

/**
 * @author Johnny Bufu
 */
class Backplane2Message(data: Map[String,String]) extends BackplaneMessage(data, Backplane2MessageFields.values) {

  def this(clientSourceUrl: String, defaultExpireSeconds: Int, maxExpireSeconds: Int, upstreamData: java.util.Map[String,AnyRef]) =
    this(
      Backplane2Message.processExpire(
        Backplane2Message.parseUpstreamData(upstreamData.toMap), defaultExpireSeconds, maxExpireSeconds )
      ++
      Map(
        Backplane2MessageFields.ID.name -> BackplaneMessage.generateMessageId(new Date),
        Backplane2MessageFields.SOURCE.name -> clientSourceUrl
      ))

  def idField = Backplane2MessageFields.ID

  def asFrame(serverDomain: String, includePayload: Boolean): Map[String, AnyRef] =
    Backplane2MessageFields.values
      .map(field => field.name -> field.frameOutput(id, serverDomain, includePayload, get(field))).toMap
      .filter { case (k,v) => v.isDefined } .mapValues(_.get)

  def bus: String = get(Backplane2MessageFields.BUS)
    .getOrElse(throw new IllegalStateException("bus field missing from bp2 message, should have failed validation"))

  def channel: String = get(Backplane2MessageFields.CHANNEL)
    .getOrElse(throw new IllegalStateException("channel field missing from bp2 message, should have failed validation"))

  def sticky = get(Backplane2MessageFields.STICKY).exists(_.toBoolean)

  def expiration: String = get(Backplane2MessageFields.EXPIRE)
    .getOrElse(throw new IllegalStateException("expire field missing from bp2 message, should have failed validation"))
}

object Backplane2Message {

  private final val UPSTREAM_FIELDS = Backplane2MessageFields.values.filter(_.isUpstream).toList
  private final val UPSTREAM_FIELD_NAMES = UPSTREAM_FIELDS.map(_.name)

  val scopeKeys = Backplane2MessageFields.values.filter(_.scopeType != ScopeType.NONE).map(sc => sc.name -> sc).toMap

  private def parseUpstreamData(upstreamData: Map[String,AnyRef]): Map[String,String] = {
    Backplane2Message.checkUpstreamFields(upstreamData)
    Backplane2Message.UPSTREAM_FIELDS
      .map(field => field.name -> field.parseUpstreamValue(upstreamData)).toMap
      .filter { case (k,v) => v.isDefined } .mapValues(_.get)
  }

  private def checkUpstreamFields(upstreamData: Map[String,AnyRef]) {
    upstreamData.keySet.foreach( field =>
      if (! UPSTREAM_FIELD_NAMES.contains(field))
        throw new InvalidRequestException("Invalid field in upstream message: " + field))
  }

  private def processExpire(data: Map[String,String], defaultExpSeconds: Int, stickyExpSeconds: Int): Map[String,String] = {
    val sticky = data.get(Backplane2MessageFields.STICKY.name).exists(_.equalsIgnoreCase(true.toString))
    val exp = data.get(Backplane2MessageFields.EXPIRE.name).getOrElse(null)
    data ++ Map(Backplane2MessageFields.EXPIRE.name -> DateTimeUtils.processExpireTime(sticky, exp, defaultExpSeconds, stickyExpSeconds))
  }
}

object Backplane2MessageFields extends MessageFieldEnum with Loggable {

  type BackplaneMessageField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
    def isUpstream = true
    def parseUpstreamValue(upstreamData: Map[String,AnyRef]): Option[String] = upstreamData.get(name).map(_.toString)
    def scopeType: Scope.ScopeType
    def frameOutput(msgId: String, serverDomain: String, includePayload: Boolean, fieldValue: Option[String]): Option[AnyRef] = fieldValue
  }

  val ID = new BackplaneMessageField { def name = "id"; def scopeType = NONE
    override def isUpstream = false
    override def frameOutput(msgId: String, serverDomain: String, includePayload: Boolean, fieldValue: Option[String]) = None
    override def validateLong(fieldValue: Option[String]) {
      super.validateLong(fieldValue)
      fieldValue.foreach(BackplaneMessage.dateFromId)
    }
  }

  val CHANNEL = new BackplaneMessageField { def name = "channel"; def scopeType = FILTER }

  val BUS = new BackplaneMessageField { def name = "bus"; def scopeType = AUTHZ_REQ }

  val STICKY = new BackplaneMessageField { def name = "sticky"; def scopeType = FILTER
    override def required = false
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateBoolean(fieldValue)
    }
    override def parseUpstreamValue(upstreamData: Map[String, AnyRef]) =
      upstreamData.get(name).map(_.toString).orElse(Option(false.toString))
  }

  val EXPIRE = new BackplaneMessageField { def name = "expire"; def scopeType = NONE
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateInternetDate(fieldValue)
    }
  }

  val SOURCE = new BackplaneMessageField { def name = "source"; def scopeType = FILTER
    override def isUpstream = false
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateUrl(fieldValue)
    }
  }

  val TYPE = new BackplaneMessageField { def name = "type"; def scopeType = FILTER }

  val MESSAGE_URL = new BackplaneMessageField { def name = "messageURL"; def scopeType = FILTER
    override def required = false
    override def isUpstream = false
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateUrl(fieldValue)
    }
    override def frameOutput(msgId: String, serverDomain: String, includePayload: Boolean, fieldValue: Option[String]) =
      Some("https://" + serverDomain + "/v2/message/" + msgId)
  }

  val PAYLOAD = new BackplaneMessageField { def name = "payload"; def scopeType = NONE
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
    override def frameOutput(msgId: String, serverDomain: String, includePayload: Boolean, fieldValue: Option[String]) = {
      if (includePayload) {
        try {
          Some(new ObjectMapper().readValue(fieldValue.getOrElse(null), classOf[AnyRef]) ) // un-quote the value
        } catch {
          case e: IOException => {
            val errMsg = "Error deserializing message payload: " + e.getMessage
            logger.error(errMsg)
            throw new BackplaneServerException(errMsg, e)
          }
        }
      } else {
        None
      }
    }
  }

}
