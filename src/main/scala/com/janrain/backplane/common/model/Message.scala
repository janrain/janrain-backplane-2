package com.janrain.backplane.common.model

import com.janrain.util.{Utils, Loggable, Enum}
import org.apache.commons.codec.binary.Base64._
import com.janrain.commons.util.Utf8StringUtils._
import scala.Some
import java.util.Date
import org.apache.commons.lang.StringUtils
import java.text.ParseException
import org.apache.commons.codec.binary.Base64
import com.janrain.backplane.common.DateTimeUtils
import java.net.{MalformedURLException, URL}
import com.janrain.backplane.common.MessageException

/**
 * @author Johnny Bufu
 */
abstract class Message[MF <: MessageField](private val data: Map[String,String], val declaredFields: Traversable[MF])
  extends Map[String, String] with Loggable {

  // constructor
  declaredFields.foreach(field => field.validate(get(field), this))

  // abstract
  def idField: MF

  // implementation
  def id = get(idField) match {
    case Some(s) => s
    case None => throw new MessageException("ID field not set: " + idField)
  }

  def get(field: MF): Option[String] = get(field.name)

  // delegate to data map
  def get(key: String) = data.get(key)

  def iterator = data.iterator

  def -(key: String) = data - key

  def +[B1 >: String](kv: (String, B1)) = data + kv

  /** each base64: key:val,key:val... */
  final def serialize: String = map( entry =>
    base64.encodeToString(entry._1.getBytes(UTF8)) + Message.SERIAL_KEY_VAL_SEP + base64.encodeToString(entry._2.getBytes)).mkString(Message.SERIAL_ENTRY_SEP)

  private lazy val base64 = new Base64
}

object Message extends Loggable {

  final def deserialize(serialized: String): Map[String,String] = serialized match {
    case s: String => s.split(Message.SERIAL_ENTRY_SEP).map(_.split(Message.SERIAL_KEY_VAL_SEP, 2) match {
      case Array(k, v) => (new String(decodeBase64(k), UTF8), new String(decodeBase64(v), UTF8))
      case _ => throw new MessageException("error deserializing IdP resposne from: " + serialized)
    }).toMap[String, String]
    case _ => Map()
  }

  final def isExpired(fieldValue: Option[String]) = fieldValue match {
    case Some(s: String) =>
      try {
        new Date().getTime > Utils.ISO8601.parseDateTime(s).getMillis
      } catch {
        case e: Throwable => true
      }
    case _ => false
  }

  private final val SERIAL_KEY_VAL_SEP = ":"
  private final val SERIAL_ENTRY_SEP = ","
}

trait MessageFieldEnum extends Enum { type EnumVal <: Value with MessageField }

trait MessageField {
  import com.janrain.backplane.common.MessageException

  def name: String
  def required: Boolean

  @throws(classOf[MessageException])
  def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
    if (required) validateRequired(fieldValue)
  }

  def validateRequired(message: Message[_]) { validateRequired(message.get(this.name)) }

  def validateRequired(fieldValue: Option[String]) {
    fieldValue match {
      case Some(s: String) if StringUtils.isNotEmpty(s) => ()
      case _ => throw new MessageException("value required for field: " + name + ", got: " + fieldValue)
    }
  }

  def validateIso8601Date(fieldValue: Option[String]) {
    try {
      fieldValue.foreach(Utils.ISO8601.parseDateTime(_))
    } catch {
      case e: ParseException => throw new MessageException("invalid value for " + name + ": " + fieldValue)
    }
  }

  def validateInternetDate(fieldValue: Option[String]) {
    try {
      fieldValue.foreach(DateTimeUtils.INTERNETDATE.get.parse(_))
    } catch {
      case e: Exception => throw new MessageException("invalid internet date value for " + name + ": " + fieldValue)
    }
  }

  def validateBoolean(fieldValue: Option[String]) {
    try {
      fieldValue.foreach(_.toBoolean)
    } catch {
      case e: Exception => throw new MessageException("invalid boolean string value for " + name + ": " + fieldValue)
    }
  }

  def validateLong(fieldValue: Option[String]) {
    try {
      fieldValue.foreach(_.toLong)
    } catch {
      case e: NumberFormatException => throw new MessageException("invalid long value for " + name + ": " + fieldValue)
    }
  }

  def validateUrl(fieldValue: Option[String]) {
    try {
      fieldValue.foreach(new URL(_))
    }
    catch {
      case e: MalformedURLException => throw new MessageException("Invalid URL value for " + name + ": " + fieldValue)
    }
  }
}