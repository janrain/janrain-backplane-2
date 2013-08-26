package com.janrain.backplane.server1.dao.redis

import com.janrain.backplane.dao.redis.{Redis, MessageProcessorDaoSupport, RedisMessageProcessor}
import com.janrain.backplane.server1.model.{Backplane1MessageFields, Backplane1Message}
import com.janrain.backplane.dao.Dao
import com.redis.{RedisClient, RedisCommand}
import com.janrain.backplane.common.model.BackplaneMessage
import com.janrain.backplane.common.DateTimeUtils
import com.janrain.util.Loggable
import com.janrain.backplane.server.redisdao.RedisBackplaneMessageDAO
import org.apache.commons.lang.SerializationUtils

/**
 * Transition MessageProcessor implementation for BP1:
 * - posted messages are written (by the new DAO) to a new queue address, and using the new format
 * - this message processor implementation reads and processes messages from the new queue/key address, and
 * - writes both old and new message formats to the final (redis) keys (in a single redis transaction)
 *
 * @author Johnny Bufu
 */
class RedisBackplane1DualFormatMessageProcessor(dao: Dao[Backplane1Message] with MessageProcessorDaoSupport[Backplane1MessageFields.EnumVal,Backplane1Message])
  extends RedisMessageProcessor(dao) with Loggable {
  /**
   * Store one message to redis, as part of the supplied redis pipeline / transaction.
   * The following associated redis data structures are created or updated:
   * - key = msgId, value = serialized message string
   * - key = bp2messages, add sorted set entry, score = msg time, value = msg metadata
   * - key = bp2bus:<bus>, add sorted set entry, score = msg time, value = msgId
   * - key = bp2channel:<channel>, add sorted set entry, score = msg time, value = msgId
   *
   * @return pair of:
   *         last, possibly updated, id
   *         old message id prepended to the list of supplied insertion times
   */
  override protected def processSingleMessage(backplaneMessage: Backplane1Message, postedId: String, insertionTimes: List[String], redisClient: RedisCommand): (String,List[String]) = {

    val msgId = backplaneMessage.id
    val messageTime = BackplaneMessage.timeFromId(msgId)
    val expireSeconds = DateTimeUtils.getExpireSeconds(msgId, backplaneMessage.expiration, backplaneMessage.sticky)

    // new format
    redisClient.hmset(dao.itemKey(msgId), backplaneMessage)
    redisClient.expire(dao.itemKey(msgId), expireSeconds)
    redisClient.zadd(dao.channelKey(backplaneMessage.channel), messageTime, msgId)
    redisClient.zadd(dao.busKey(backplaneMessage.bus), messageTime, msgId)
    val metaDataNew = "%s %s %s %s".format(backplaneMessage.bus, backplaneMessage.channel, msgId, backplaneMessage.expiration)
    redisClient.zadd(dao.messagesKey, messageTime, metaDataNew)

    // legacy format
    redisClient.setex(RedisBackplaneMessageDAO.getKey(msgId), expireSeconds, SerializationUtils.serialize(backplaneMessage.asLegacy))
    redisClient.rpush(RedisBackplaneMessageDAO.getChannelKey(backplaneMessage.channel), msgId.getBytes)
    redisClient.zadd(RedisBackplaneMessageDAO.getBusKey(backplaneMessage.bus), messageTime, msgId.getBytes)
    redisClient.zadd(RedisBackplaneMessageDAO.V1_MESSAGES.getBytes, messageTime, legacyMetaData(backplaneMessage.bus, backplaneMessage.channel, msgId).getBytes)

    redisClient.lpop(dao.messagesQueueKey)
    logger.info("%s pipelined message: %s -> (%s, %s)".format(dao.processorId, postedId, dao.itemKey(msgId), RedisBackplaneMessageDAO.getKey(msgId)))

    (msgId, postedId :: insertionTimes)

  }

  override protected def deleteExpiredMessages() {
    // new format cleanup
    super.deleteExpiredMessages()

    // legacy format cleanup
    val allMsgMetas: Option[List[String]] =
      Redis.readPool.withClient(_.zrangebyscore(RedisBackplaneMessageDAO.V1_MESSAGES.getBytes, 0, minInclusive = true, Double.MaxValue, maxInclusive = true, None, RedisClient.ASC))

    val parsedMsgMetas: List[(String,String,String)] =
    allMsgMetas.map(_.map(_.split(" ")).collect {
      case Array(bus, channel, msgId) => (msgId, channel, bus)
    }).getOrElse(Nil)

    val existsRes: List[Any] =
    Redis.readPool.withClient(_.pipeline(p => parsedMsgMetas.foreach {
      case (id, channel, bus) => p.exists(RedisBackplaneMessageDAO.getKey(id))
    })).getOrElse(throw new Exception)

    val expiredMsgIds = parsedMsgMetas.zip(existsRes).collect {
      case (parsedMetaTuple3, notExists: Long) if notExists == 0 => parsedMetaTuple3
    }

    Redis.writePool.withClient(_.pipeline( p => {
      expiredMsgIds.foreach {
        case (msgId, channel, bus) => {
          p.zrem(RedisBackplaneMessageDAO.V1_MESSAGES.getBytes, legacyMetaData(bus, channel, msgId))
          p.lrem(RedisBackplaneMessageDAO.getChannelKey(channel), 0, msgId.getBytes)
          p.zrem(RedisBackplaneMessageDAO.getBusKey(bus), msgId.getBytes)
          p.del(RedisBackplaneMessageDAO.getKey(msgId))
        }
      }
    }))
  }

  private def legacyMetaData(bus: String, channel: String, msgId: String) = "%s %s %s".format(bus, channel, msgId)
}
