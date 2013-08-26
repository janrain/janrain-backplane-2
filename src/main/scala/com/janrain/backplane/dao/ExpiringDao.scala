package com.janrain.backplane.dao

/**
 * @author Johnny Bufu
 */
trait ExpiringDao[T <: {def id : String}] extends Dao[T] {

  val expireSeconds: Int

  def expire(seconds: Int, id: String)

  def expire(seconds: Int, ids: String*): List[(String, Boolean)]

  abstract override def store(item: T) {
    super.store(item)
    expire(expireSeconds, item.id)
  }

  abstract override def store(items: T*): List[(String,Boolean)] =
    items.map(_.id).zip(
      for {
        storeRes <- super.store(items: _*).map(_._2)
        expireRes <- expire(expireSeconds, items.map(_.id): _*).map(_._2)
      } yield {
        storeRes && expireRes
      }).toList
}
