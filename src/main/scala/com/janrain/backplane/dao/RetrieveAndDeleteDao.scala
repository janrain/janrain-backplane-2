package com.janrain.backplane.dao

/**
 * DAO support for atomic retrieve-and-delete operation
 *
 * @author Johnny Bufu
 */
trait RetrieveAndDeleteDao[T <: {def id : String} ] {

  def retrieveAndDelete(itemId: String): Option[T]

}
