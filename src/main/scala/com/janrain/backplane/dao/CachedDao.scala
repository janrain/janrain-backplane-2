package com.janrain.backplane.dao

/**
 * @author Johnny Bufu
 */
trait CachedDao[T <: {def id : String}] extends Dao[T] {

  /** @return item */
  def cache(id: String, item: Option[T]): Option[T]

  /** @return (foundCached, cachedValue) -- allows to cache None values */
  def cachedGet(id:String): (Boolean, Option[T])

  abstract override def get(id: String): Option[T] = cachedGet(id) match {
    case (found, value) =>
      if (found) value else cache(id, super.get(id))
  }

  abstract override def get(ids: String*): List[(String,Option[T])] = {
    val (cached, notCached) = ids.map(id => id -> cachedGet(id)).partition(_._2._1 == true)
    cached.map { case (id, foundEntry) => id -> foundEntry._2}.toList ++
      super.get(notCached.map(_._1): _*).map { case (id, value) => id -> cache(id, value) }.toList
  }
}
