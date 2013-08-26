package com.janrain.backplane.server1.dao

import com.janrain.backplane.dao.Dao
import com.janrain.backplane.server1.model.Backplane1Message

/**
 * @author Johnny Bufu
 */
trait Backplane1MessageDao extends Dao[Backplane1Message] {

  def retrieveMessagesByBus(bus: String, since: String, sticky: String): List[Backplane1Message]

  def retrieveMessagesByChannel(channel: String, since: String, sticky: String): List[Backplane1Message]

  def messageCount(channel: String): Long

}
