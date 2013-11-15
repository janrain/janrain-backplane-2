package com.janrain.backplane.server2.model

import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}
import scala.collection.JavaConversions._
import com.janrain.util.RandomUtils
import com.janrain.backplane.dao.LegacySupport

/**
 * Binds a previously server-generated channel ID to a bus/config.
 * Also passes along the bus expiration for
 *
 * @author Johnny Bufu
 */
class Channel(data: Map[String,String]) extends Message(data, ChannelFields.values) with LegacySupport[com.janrain.backplane2.server.Channel] {

  def this(channelId: String, config: BusConfig2, channelExpireSeconds: Int) {
    this(Map(
      ChannelFields.ID.name -> (if (channelId == null) RandomUtils.randomString(Channel.CHANNEL_NAME_LENGTH) else channelId),
      ChannelFields.BUS.name -> config.id,
      ChannelFields.EXPIRE_SECONDS.name -> channelExpireSeconds.toString,
      ChannelFields.MESSAGE_EXPIRE_DEFAULT_SECONDS.name -> config.retentionTimeSeconds.toString,
      ChannelFields.MESSAGE_EXPIRE_MAX_SECONDS.name -> config.retentionTimeStickySeconds.toString
    ))
  }

  def idField = ChannelFields.ID

  def expireSeconds = get(ChannelFields.EXPIRE_SECONDS).map(_.toInt)

  def asLegacy = new com.janrain.backplane2.server.Channel(mapAsJavaMap(this))
}

object Channel {
  final val CHANNEL_LEGACY_NAME_LENGTH = 32
  final val CHANNEL_NAME_LENGTH = 34
}

object ChannelFields extends MessageFieldEnum {

  type ChannelField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val ID = new ChannelField { def name = "id" }

  val BUS = new ChannelField { def name = "bus" }

  val EXPIRE_SECONDS = new ChannelField { def name = "expire_seconds"
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
    }
  }

  val MESSAGE_EXPIRE_DEFAULT_SECONDS = new ChannelField { def name = "message_expire_default_seconds"
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
    }
  }

  val MESSAGE_EXPIRE_MAX_SECONDS = new ChannelField { def name = "message_expire_max_seconds"
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
    }
  }

}
