package com.janrain.util

import org.apache.commons.lang.StringUtils
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTimeZone

/**
 * @author Johnny Bufu
 */
object Utils {

  final val ISO8601 = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(DateTimeZone.UTC)

  final val INTERNET_DATE = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(DateTimeZone.UTC)

  def constantTimeEquals(r: String, s: String): Boolean = {
    if (r == null ^ s == null) return false
    if (r == null) return true
    if (r.length != s.length) {
      return false
    }
    var result = 0
    for ( (x,y) <- r zip s ) {
      result |= x ^ y
    }
    result == 0
  }

  def constantTimeEqualsNotNull(r: String, s: String) =
    r != null && s != null && r.length == s.length && ( (0 /: (r zip s))( (partial,rs) => partial | (rs._1 ^ rs._2) ) == 0 )

  def getOptionalSystemProperty(propName: String): Option[String] = System.getProperty(propName) match {
    case s if StringUtils.isNotBlank(s) => Some(s)
    case _ => None
  }

  def getRequiredSystemProperty(propName: String) = getOptionalSystemProperty(propName).getOrElse(
    throw new RuntimeException("Required system property configuration missing: " + propName)
  )

  def getSystenEnv(envName: String) = System.getenv(envName) match {
    case s if StringUtils.isNotBlank(s) => s
    case _ => throw new RuntimeException("Required environment configuration missing: " + envName)
  }

  /*
    final val ISO8601: ThreadLocal[DateFormat] = new ThreadLocal[DateFormat] {
      protected override def initialValue: DateFormat = new SimpleDateFormat(("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) {
        setTimeZone(TimeZone.getTimeZone("GMT"))
      }
    }
  */

}
