package com.janrain.backplane.dao

/**
 * Generic batch-style DAO.
 *
 * Entities that can be persisted using this type of DAO must implement a id: String structural type.
 *
 * Primitives that accept lists of arguments attempt to perform the operation for each item
 * and return a list of id -> (item or error) pairs
 *
 * @author Johnny Bufu
 */
trait Dao[T <: {def id : String} ] {

  // Create

  @throws(classOf[DaoException])
  def store(item: T)

  /** @return list of (id -> (Boolean stored or DaoException) ) pairs */
  @throws(classOf[DaoException])
  def store(items: T*): List[(String,Boolean)]

  // Read

  /** @return retrieved item or throws on any error */
  @throws(classOf[DaoException])
  def get(id: String): Option[T]

  /** @return list of (id ->(instance or DaoException)) pairs */
  @throws(classOf[DaoException])
  def get(ids: String*): List[(String,Option[T])]

  // Update

  @throws(classOf[DaoException])
  def update(item: T)

  /** @return list of (id ->(Boolean or DaoException)) pairs */
  @throws(classOf[DaoException])
  def update(items: T*): List[(String,Boolean)]

  // Delete

  /** @return deleted item or throws on any error */
  @throws(classOf[DaoException])
  def delete(id: String): Boolean

  /** @return list of (id ->(null-success or DaoException)) pairs */
  @throws(classOf[DaoException])
  def delete(ids: String*):  List[(String,Boolean)]
}
