package com.janrain.backplane.common.model

import java.util.Date
import com.janrain.backplane.common.MessageException
import com.janrain.util.{Loggable, Utils, RandomUtils}

/**
 * Base class for Backplane[1|2]Message
 *
 * @author Johnny Bufu
 */
abstract class BackplaneMessage[MF <: MessageField](data: Map[String,String], declaredFields: Traversable[MF])
  extends Message(data, declaredFields) {
  def bus: String
  def channel: String
  def sticky: Boolean
  def expiration: String
}

object BackplaneMessage extends Loggable {

  private final val MESSAGE_ID_RANDOM_LENGTH = 10
  private final val MESSAGE_ID_LEGACY_RANDOM_LENGTH = 10
  private final val MESSAGE_ID_TIMESTAMP_SEP = "-"

  def generateMessageId: String = generateMessageId(new Date)

  def generateMessageId(date: Date): String =
    Utils.ISO8601.print(new Date().getTime) + MESSAGE_ID_TIMESTAMP_SEP + RandomUtils.randomString(MESSAGE_ID_RANDOM_LENGTH)

  def legacyIdLength: Int = 25 + MESSAGE_ID_LEGACY_RANDOM_LENGTH // ISO8601 length + "-" + random_length

  /** @return milliseconds since 1970-01-01, or 0 if the provided parameter is not a valid ISO8601 date prefixed string */
  final def timeFromId(timestampPrefixedId: String): Long = {
    try {
      dateFromId(timestampPrefixedId).map(_.getTime).getOrElse(0)
    } catch {
      case e: Exception =>
        logDebug("invalid message ID: " + timestampPrefixedId)
        0
    }
  }

  /** throws if the provided parameter is not a valid ISO8601 date prefixed string */
  final def dateFromId(timestampPrefixedId: String): Option[Date] =
    try {
      Some(Utils.ISO8601.parseDateTime(timestampPrefixedId.substring(0, timestampPrefixedId.indexOf("Z") + 1)).toDate)
    } catch {
      case e: Throwable => {
        throw new MessageException("error extracting timestamp from id: " + e.getMessage)
      }
    }

}