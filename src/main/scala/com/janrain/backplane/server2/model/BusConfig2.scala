package com.janrain.backplane.server2.model

import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}
import com.janrain.backplane.common.MessageException
import scala.collection.JavaConversions._
import com.janrain.backplane.dao.LegacySupport

/**
 * @author Johnny Bufu
 */
class BusConfig2(data: Map[String,String]) extends Message(data, BusConfig2Fields.values) with LegacySupport[com.janrain.backplane2.server.config.BusConfig2] {

  def this(javaData: java.util.Map[String,String]) = this(LegacySupport.fromLegacy(javaData))

  def idField = BusConfig2Fields.BUS_NAME

  def retentionTimeSeconds: Int = get(BusConfig2Fields.RETENTION_TIME_SECONDS).getOrElse(BusConfig2.RETENTION_MIN_SECONDS.toString).toInt

  def retentionTimeStickySeconds: Int = get(BusConfig2Fields.RETENTION_STICKY_TIME_SECONDS).getOrElse(BusConfig2.RETENTION_STICKY_MIN_SECONDS.toString).toInt

  def asLegacy = new com.janrain.backplane2.server.config.BusConfig2(LegacySupport.toLegacy(this))
}

object BusConfig2 {
  private[model] final val RETENTION_MIN_SECONDS = 60L
  private[model] final val RETENTION_MAX_SECONDS = 604800L         // one week
  private[model] final val RETENTION_STICKY_MIN_SECONDS = 28800L   // eight hours
  private[model] final val RETENTION_STICKY_MAX_SECONDS = 604800L  // one week
}

object  BusConfig2Fields extends MessageFieldEnum {

  type BusConfig2Field = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val BUS_NAME = new BusConfig2Field { def name = "bus_name" }

  val OWNER = new BusConfig2Field { def name = "owner" }

  val RETENTION_TIME_SECONDS = new BusConfig2Field { def name = "retention_time_seconds"

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
      fieldValue.map(_.toLong).foreach(longValue =>
        if (longValue < BusConfig2.RETENTION_MIN_SECONDS || longValue > BusConfig2.RETENTION_MAX_SECONDS)
          throw new MessageException("Value of " + name + " = " + longValue + " but must be between " + BusConfig2.RETENTION_MIN_SECONDS + " and " + BusConfig2.RETENTION_MAX_SECONDS)
      )
    }
  }

  val RETENTION_STICKY_TIME_SECONDS = new BusConfig2Field { def name = "retention_sticky_time_seconds"

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
      fieldValue.map(_.toLong).foreach(longValue =>
        if (longValue < BusConfig2.RETENTION_STICKY_MIN_SECONDS || longValue > BusConfig2.RETENTION_STICKY_MAX_SECONDS)
          throw new MessageException("Value of " + name + " = " + longValue + " but must be between " + BusConfig2.RETENTION_MIN_SECONDS + " and " + BusConfig2.RETENTION_STICKY_MAX_SECONDS)
      )
    }
  }
}
