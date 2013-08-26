package com.janrain.backplane.dao

import com.janrain.backplane.common.model.Message

/**
 * @author Johnny Bufu
 */
trait MessageDao[MT <: Message[_]] extends Dao[MT] {

  protected def instantiate(data: Map[_,_]): MT

}
