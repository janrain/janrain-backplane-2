package com.janrain.backplane.server2.dao.redis

import com.janrain.backplane.server2.model.{Backplane2MessageFields, Backplane2Message}
import com.janrain.backplane.dao.Dao
import com.janrain.backplane.dao.redis.{MessageProcessorDaoSupport, RedisMessageProcessor}
import com.redis.RedisCommand
import com.janrain.backplane.server2.dao.BP2DAOs
import com.janrain.util.Utils

/**
 * @author Johnny Bufu
 */
class RedisBackplane2MessageProcessor
  (dao: Dao[Backplane2Message] with MessageProcessorDaoSupport[Backplane2MessageFields.EnumVal,Backplane2Message])
  extends RedisMessageProcessor(dao) {

  override protected def processSingleMessage(backplaneMessage: Backplane2Message, postedId: String, insertionTimes: List[String], redisClient: RedisCommand) = {
    // extend channel life with sticky message retention time + 1h
    if (backplaneMessage.sticky) BP2DAOs.channelDao.expire(
        3600 + Utils.secondsLeft(backplaneMessage.expiration),
        backplaneMessage.channel)
    super.processSingleMessage(backplaneMessage, postedId, insertionTimes, redisClient)
  }
}
