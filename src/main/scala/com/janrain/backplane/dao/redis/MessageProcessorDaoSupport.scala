package com.janrain.backplane.dao.redis

import com.janrain.backplane.common.model.{MessageField, BackplaneMessage}
import com.janrain.backplane.config.SystemProperties

/**
 * Mixin for RedisMessageDao with support for Backplane[1|2]Message / Message Processor
 *
 * @author Johnny Bufu
 */
trait MessageProcessorDaoSupport[BMF <: MessageField,BMT <: BackplaneMessage[BMF]] {

  this: RedisMessageDao[BMT] =>

  val processorId: String = keyPrefix + "processor"

  val idField: BMF

  def busKey(bus: String): String = SystemProperties.INSTANCE_ID + ":" + keyPrefix + "busIndex:" + bus

  def channelKey(channel: String): String = SystemProperties.INSTANCE_ID + ":" + keyPrefix + "channelIndex:" + channel

  val messagesQueueKey: String = SystemProperties.INSTANCE_ID + ":" + keyPrefix + "queue"

  val messagesKey: String = SystemProperties.INSTANCE_ID + ":" + keyPrefix + "index"

  val lastIdKey: String = SystemProperties.INSTANCE_ID + ":" + keyPrefix +  "lastId"

  // extend access scope from Redis/MessageDao'
  def itemKey(itemId: String): String = getKey(itemId)
  def mpInstantiate(data: Map[_,_]): BMT = instantiate(data)

}
