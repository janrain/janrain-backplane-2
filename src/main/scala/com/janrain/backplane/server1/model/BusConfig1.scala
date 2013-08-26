package com.janrain.backplane.server1.model

import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}
import com.janrain.backplane.common.MessageException
import scala.collection.JavaConversions._
import com.janrain.backplane.server.BusConfig1.BUS_PERMISSION
import com.janrain.backplane.dao.LegacySupport

/**
 * @author Johnny Bufu
 */
class BusConfig1(data: Map[String,String]) extends Message(data, BusConfig1Fields.values) with LegacySupport[com.janrain.backplane.server.BusConfig1] {

  def this(javaData: java.util.Map[String,String]) = this(BusConfig1.fromLegacy(javaData))

  def idField = BusConfig1Fields.BUS_NAME

  def isAllowed(user: String, permissionField: BusConfig1Fields.EnumVal) = get(permissionField).exists(_.split(",").contains(user))

  def retentionTimeSeconds: Int = get(BusConfig1Fields.RETENTION_TIME_SECONDS).getOrElse(BusConfig1.RETENTION_MIN_SECONDS.toString).toInt

  def retentionTimeStickySeconds: Int = get(BusConfig1Fields.RETENTION_STICKY_TIME_SECONDS).getOrElse(BusConfig1.RETENTION_STICKY_MIN_SECONDS.toString).toInt

  def asLegacy = {
    val getallUsers = get(BusConfig1Fields.GETALL_USERS).map(_.split(",")).toIterable.flatten
    val postUsers = get(BusConfig1Fields.POST_USERS).map(_.split(",")).toIterable.flatten

    val g = getallUsers.map(user => if (postUsers.contains(user)) user -> "GETALL,POST" else user -> "GETALL").toMap
    val p = postUsers.map(user => if (getallUsers.contains(user)) user -> "GETALL,POST" else user -> "POST").toMap
    val userPermissionsMap = g ++ p

    val legacyFieldNames = com.janrain.backplane.server.BusConfig1.Field.values().map(_.getFieldName.toLowerCase)

    val filtered = this.filterKeys(legacyFieldNames.contains(_)).map {
      case (k,v) => k.toUpperCase -> v
    }

    new com.janrain.backplane.server.BusConfig1(mapAsJavaMap(filtered ++ userPermissionsMap))
  }
}

object BusConfig1 {
  private[model] final val RETENTION_MIN_SECONDS = 60L
  private[model] final val RETENTION_MAX_SECONDS = 604800L        // one week
  private[model] final val RETENTION_STICKY_MIN_SECONDS = 28800L  // eight hours
  private[model] final val RETENTION_STICKY_MAX_SECONDS = 604800L // one week

  def fromLegacy(javaData: java.util.Map[String, String]): Map[String,String] = {
    val scalaMap = javaData.toMap
    val getallUsers = scalaMap.collect {
      case (k, v) if v.contains(BUS_PERMISSION.GETALL.name()) => k
    }
    val postUsers = scalaMap.collect {
      case (k, v) if v.contains(BUS_PERMISSION.POST.name()) => k
    }

    scalaMap.collect {
      case (k, v) if !getallUsers.contains(k) && !postUsers.contains(k) => k.toLowerCase -> v
    } ++
      Map( BusConfig1Fields.POST_USERS.name -> postUsers.mkString(","),
           BusConfig1Fields.GETALL_USERS.name -> getallUsers.mkString(","))
  }
}

object BusConfig1Fields extends MessageFieldEnum {

  type BusConfig1Field = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val BUS_NAME = new BusConfig1Field { def name = "bus_name" }

  val RETENTION_TIME_SECONDS = new BusConfig1Field { def name = "retention_time_seconds"

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
      fieldValue.map(_.toLong).foreach(longValue =>
        if (longValue < BusConfig1.RETENTION_MIN_SECONDS || longValue > BusConfig1.RETENTION_MAX_SECONDS)
          throw new MessageException("Value of " + name + " = " + longValue + " but must be between " + BusConfig1.RETENTION_MIN_SECONDS + " and " + BusConfig1.RETENTION_MAX_SECONDS)
      )
    }
  }

  val RETENTION_STICKY_TIME_SECONDS = new BusConfig1Field { def name = "retention_sticky_time_seconds"

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateLong(fieldValue)
      fieldValue.map(_.toLong).foreach(longValue =>
        if (longValue < BusConfig1.RETENTION_STICKY_MIN_SECONDS || longValue > BusConfig1.RETENTION_STICKY_MAX_SECONDS)
          throw new MessageException("Value of " + name + " = " + longValue + " but must be between " + BusConfig1.RETENTION_MIN_SECONDS + " and " + BusConfig1.RETENTION_STICKY_MAX_SECONDS)
      )
    }
  }

  val POST_USERS = new BusConfig1Field { def name = "post_users" }

  val GETALL_USERS = new BusConfig1Field { def name = "getall_users" }
}
