package com.janrain.backplane.dao

/**
 * Separate getAll() / "select * " API, since this can be an expensive operation.
 *
 * To be used judiciously.
 *
 * @author Johnny Bufu
 */
trait DaoAll[T <: {def id : String}] extends Dao[T] {

  def getAll: List[T]

}
