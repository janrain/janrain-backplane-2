package com.janrain.backplane.server1.dao.redis

import com.janrain.backplane.dao.redis.{MessageProcessorDaoSupport, Redis, RedisMessageDao}
import com.janrain.backplane.server1.model.{Backplane1MessageFields, Backplane1Message}
import com.janrain.backplane.server1.dao.Backplane1MessageDao
import com.janrain.backplane.common.model.BackplaneMessage
import com.redis.RedisClient

/**
 * @author Johnny Bufu
 */
class RedisBackplane1MessageDao extends RedisMessageDao[Backplane1Message]("bp1Message:")
  with Backplane1MessageDao
  with MessageProcessorDaoSupport[Backplane1MessageFields.EnumVal,Backplane1Message] {

  val idField = Backplane1MessageFields.ID

  protected def instantiate(data: Map[_, _]) = new Backplane1Message(data.map(kv => kv._1.toString -> kv._2.toString))

  override def store(item: Backplane1Message) {
    Redis.writePool.withClient(_.rpush(messagesQueueKey, item.serialize))
  }

  override def messageCount(channel: String): Long = Redis.readPool.withClient(_.zcard(channelKey(channel))).getOrElse(0)

  override def retrieveMessagesByBus(bus: String, since: String, sticky: String) = retrieveMessagesByKey(busKey, bus, since, sticky)

  override def retrieveMessagesByChannel(channel: String, since: String, sticky: String) = retrieveMessagesByKey(channelKey, channel, since, sticky)

  private def retrieveMessagesByKey(keyFunc: (String) => String, key: String, since: String, stickyRequested: String) = {
    val redisResult =
    Redis.readPool.withClient(
      _.zrangebyscore(keyFunc(key), BackplaneMessage.timeFromId(since),
        minInclusive = false, Double.PositiveInfinity, maxInclusive = true, None, RedisClient.ASC))

    val filtered = redisResult
      .withFilter(!_.isEmpty).map(msgIds => get(msgIds: _*).map(_._2).flatten)
      .toList.flatten

    val stickyOrNotSticky = filtered
      .filter( ! stickyRequested.toBoolean || _.sticky)
      .sortBy(_.id)

    stickyOrNotSticky
  }

}
