package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.{ExpiringDao, Dao}
import com.janrain.backplane.server2.model.Channel

/**
 * @author Johnny Bufu
 */
trait ChannelDao extends Dao[Channel] with ExpiringDao[Channel] {

  val expireSeconds = 3600 // 1h

  def getExpire(channelId: String): Int
}