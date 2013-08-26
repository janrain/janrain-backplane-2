package com.janrain.backplane.dao

import com.janrain.backplane.dao.{LegacySupport, MessageDao, DAOLegacy, DaoAll}
import com.janrain.commons.supersimpledb.message.NamedMap
import scala.collection.JavaConversions._
import com.janrain.backplane.common.model.Message
import com.janrain.util.Loggable

/**
 * Stackable modification trait for Message DAOs that:
 * 1) stores in both (legacy and new) formats
 * 2) attempts to read from new format first, then falls back to legacy format/DAOLegacy
 * 3) on first (and only on first) successful fallback, the entry is converted to new format
 *
 * @author Johnny Bufu
 */
trait LegacyDaoForwarder[LT <: NamedMap, T <: Message[_] with LegacySupport[LT]] extends MessageDao[T] with DaoAll[T] with Loggable {

  val legacyDao: DAOLegacy[LT]

  def instantiateFromLegacy(legacyItem: LT): T = instantiate(legacyItem.toMap.map{
    case (k,v) => k.toLowerCase -> v
  })

  def storeFromLegacy(convertedItem: T) = super.store(convertedItem)

  abstract override def get(id: String): Option[T] = super.get(id) match {
    case None => try {
      val legacyItem = legacyDao.get(id)
      val convertedItem = instantiateFromLegacy(legacyItem)
      // should happen only once, instantiate/convert throws NPE if legacyDao returns null
      // then (after one successful super.store) new DAO (super.get) will find this item/id
      storeFromLegacy(convertedItem)
      logger.info("converted %s : %s to new dao/format".format(legacyItem.getClass.getSimpleName, legacyItem.getName))
      Some(convertedItem)
    } catch {
      case npe: NullPointerException => None
    }

    case nonLegacyItem => nonLegacyItem
  }

  abstract override def delete(id: String) = {
    legacyDao.delete(id)
    super.delete(id)
  }

  abstract override def getAll: List[T] = {
    val newItems = super.getAll
    val legacyItems = legacyDao.getAll.map(legacyItem => instantiate(LegacySupport.fromLegacy(legacyItem))).toList
    val itemsToConvert = legacyItems.filterNot(newItems.contains)
    if(! itemsToConvert.isEmpty) {
      store(itemsToConvert: _*)
      logger.info("converted %s : [ %s ] to new dao/format".format(itemsToConvert.head.getClass.getSimpleName, itemsToConvert.map(_.id).mkString(" ")))
    }

    (newItems ++ legacyItems).toSet.toList // remove duplicates
  }

  abstract override def store(item: T) {
    legacyDao.persist(item.asLegacy)
    super.store(item)
  }

}
