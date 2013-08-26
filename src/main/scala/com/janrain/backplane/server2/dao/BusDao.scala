package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.DaoAll
import com.janrain.backplane.server2.model.{BusConfig2Fields, BusConfig2}

/**
 * @author Johnny Bufu
 */
trait BusDao extends DaoAll[BusConfig2] {

  def retrieveByOwner(busOwner: String): List[BusConfig2] =
    getAll.filter(_.get(BusConfig2Fields.OWNER).exists(_ == busOwner))

  def deleteByOwner(busOwner: String) {
    delete( getAll.flatMap(_.get(BusConfig2Fields.OWNER)): _*)
  }

}
