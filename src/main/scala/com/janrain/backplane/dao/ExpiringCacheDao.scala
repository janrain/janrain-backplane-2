package com.janrain.backplane.dao

import java.util.Collections
import java.util.{Map => JavaMap}
import java.util
import util.Map.Entry
import com.janrain.util.Loggable

/**
 * @author Johnny Bufu
 */
trait ExpiringCacheDao[T <: {def id : String}] extends Loggable with CachedDao[T] {

  def maxCacheAgeMillis: Long

  val maxEntries = 1000

  val cache: JavaMap[String, (Long, Option[T])] = Collections.synchronizedMap(
    new java.util.LinkedHashMap[String, (Long, Option[T])]() {
      override def removeEldestEntry(eldest: Entry[String, (Long, Option[T])]) = {
        size > maxEntries  || isExpired(eldest.getValue._1)
      }
    }
  )

  def cache(id: String, item: Option[T]) = {
    cache.put(id, (System.currentTimeMillis() -> item))
    item
  }

  def cachedGet(id: String): (Boolean, Option[T]) = Option(cache.get(id)) match {

    case Some( (timestamp, notExpired) ) if !isExpired(timestamp) => {
      logDebug("cache hit (value=%s) for %s".format(notExpired.getClass.getSimpleName, id))
      true -> notExpired
    }

    case _ => {
      logDebug("cache miss for %s".format(id))
      false -> None
    }

  }

  private def isExpired(timestamp: Long): Boolean = timestamp + maxCacheAgeMillis < System.currentTimeMillis()

}
