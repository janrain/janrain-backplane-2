package com.janrain.backplane.dao

/**
 * Stackable trait DAO for an expiring items:
 *
 * Items must implement expireSeconds(), but may optionally return None,
 * in which case the DAO-level expireSeconds() is used.
 *
 * @author Johnny Bufu
 */
trait ExpiringDao[ET <: {def id : String; def expireSeconds: Option[Int]}] extends Dao[ET] {

  val expireSeconds: Int

  def expire(seconds: Int, id: String)

  def expire( idsAndSeconds: (String,Int)* ): List[(String, Boolean)]

  def store(item: ET, expSeconds: Int) {
    super.store(item)
    expire(expSeconds, item.id)
  }

  abstract override def store(item: ET) {
    store(item, itemExpireSeconds(item))
  }

  abstract override def store(items: ET*): List[(String,Boolean)] = {
    val itemIdsAndExpirations = items.map(i => (i.id, itemExpireSeconds(i)))
    val itemIds = items.map(_.id)
    itemIds.zip(
      for {
        storeRes <- super.store(items: _*).map(_._2)
        expireRes <- expire(itemIdsAndExpirations: _*).map(_._2)
      } yield {
        storeRes && expireRes
      }).toList
  }

  private def itemExpireSeconds(item: ET): Int = item.expireSeconds.getOrElse(expireSeconds)
}
