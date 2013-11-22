package com.janrain.backplane.dao.redis

import java.util.concurrent.{TimeUnit, Executors}
import com.janrain.backplane.config.{SystemProperties, BackplaneConfig}
import java.util.concurrent.atomic.AtomicBoolean
import com.netflix.curator.framework.CuratorFramework
import com.netflix.curator.framework.state.ConnectionState
import com.redis.{RedisCommand, RedisClient}
import com.janrain.backplane.common.model.{MessageField, BackplaneMessage, Message}
import com.yammer.metrics.Metrics
import com.yammer.metrics.core.MetricName
import com.janrain.backplane.dao.{Dao, DaoException}
import org.apache.commons.lang.exception.ExceptionUtils
import java.util.Date
import com.janrain.backplane.common.DateTimeUtils
import com.janrain.util.Loggable
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener
import scala.annotation.tailrec


/**
 * ZooKeeper Leader Selector / message processor support for the Redis DAO implementation
 *
 * @author Johnny Bufu
 */
class RedisMessageProcessor[BMF <: MessageField, BMT <: BackplaneMessage[BMF]]( private val dao: Dao[BMT] with MessageProcessorDaoSupport[BMF,BMT] )
  extends LeaderSelectorListener with Loggable {

  private val cleanupRunnable = new Runnable {
    override def run() {
      try {
        deleteExpiredMessages()
      } catch {
        case e: Exception => logger.warn(e)
      }
    }
  }

  private val scheduledExecutor = Executors.newScheduledThreadPool(1)

  BackplaneConfig.addToBackgroundServices("%scleanup_runner".format(dao.processorId), scheduledExecutor)

  private val leader = new AtomicBoolean(false)

  private def isLeader: Boolean = leader.get && ! BackplaneConfig.isLeaderDisabled

  override def takeLeadership(curatorFramework: CuratorFramework) {
    leader.set(true)
    logger.info("[%s] %s leader elected for message processing".format(SystemProperties.machineName, dao.processorId))
    val cleanupTask = scheduledExecutor.scheduleAtFixedRate(cleanupRunnable, 1, 2, TimeUnit.HOURS)
    insertMessages()
    cleanupTask.cancel(false)
    logger.info("[%s] %s leader ended message processing".format(SystemProperties.machineName, dao.processorId))
  }

  override def stateChanged(curatorFramework: CuratorFramework, connectionState: ConnectionState) {
    logger.info("%s leader selector state changed to %s".format(dao.processorId, connectionState))
    if (isLeader && (ConnectionState.LOST == connectionState || ConnectionState.SUSPENDED == connectionState)) {
      leader.set(false)
      logger.info("%s leader lost connection, giving up leadership".format(dao.processorId))
    }
  }

  protected def deleteExpiredMessages() {
    Redis.readPool.withClient(_.zrangebyscore(dao.messagesKey, 0, minInclusive = true, Double.MaxValue, maxInclusive = true, None, RedisClient.ASC))
      .map( allMsgIds => {
      Redis.writePool.withClient(_.pipeline( p => {
        allMsgIds.map(msgMeta => msgMeta.split(" ")).collect {
          case Array(bus, channel, msgId, expTime) if Message.isExpired(Option(expTime)) =>
            p.del(msgId)
            p.zrem(dao.messagesKey, metaData(bus, channel, msgId, expTime))
            p.zrem(dao.busKey(bus), msgId)
            p.zrem(dao.channelKey(channel), msgId)
        }
      }))
    })
  }

  private val timeInQueue = Metrics.newHistogram(new MetricName(dao.processorId, this.getClass.getName.replace(".", "_"), "time_in_queue"))

  private def metaData(bus: String, channel: String, msgId: String, expire: String) = "%s %s %s %s".format(bus, channel, msgId, expire)

  /** Processor to pull messages off queue and make them available */
  private def insertMessages() {
    @tailrec
    def insertLoop(leader: => Boolean, redisConsecutiveFailures: Int) {
      if (leader && redisConsecutiveFailures < 3) {
        insertLoop(isLeader,
          try {
            Redis.writePool.withClient(processSingleBatchOfPendingMessages)
            Thread.sleep(150)
            0
          } catch {
            case e: Exception => {
              logger.warn(e)
              try {
                Thread.sleep(2000)
              } catch {
                case ie: InterruptedException => // ignore
              }
              redisConsecutiveFailures + 1
            }
          }
        )
      }
    }

    insertLoop(isLeader, 0)
  }

  private def processSingleBatchOfPendingMessages(redisClient: RedisClient) {
    try {
      // no writes go through if another node updates LAST_ID
      redisClient.send("WATCH", dao.lastIdKey)(redisClient.asString)

      val redisLastId = redisClient.get(dao.lastIdKey).getOrElse("")
      val initialInsertionTimes: List[String] = Nil
      // is there a better way to extract the insertionTimes from inside the pipeline loaner pattern?
      var finalPostedIds: List[String] = Nil

      val messagesToProcess: List[String] = redisClient.lrange(dao.messagesQueueKey, 0, 9).toIterable.flatten.flatten.toList
      if ( ! messagesToProcess.isEmpty ) {
        redisClient.pipeline( p => {
          // pipelines are actually transactions (MULTI/EXEC) with scala-redis lib
          val (latestId, postedIds) = messagesToProcess.foldLeft( (redisLastId, initialInsertionTimes) ) {
            case ( (lastId, postedIdsCollector), messageString ) => {
              val (backplaneMessage, postedId) = fixId(Message.deserialize(messageString), lastId)
              processSingleMessage(backplaneMessage, postedId, postedIdsCollector, p)
            }
          }
          p.set(dao.lastIdKey, latestId)
          logger.info("%s processing transaction with %s message(s)".format(dao.processorId, postedIds.size))
          finalPostedIds = postedIds
        })
          .filter(! _.isEmpty).getOrElse(throw new DaoException("empty result received for message insertion transaction"))

        val now = System.currentTimeMillis
        for {
          postedId <- finalPostedIds
          diff = now - BackplaneMessage.timeFromId(postedId)
        } {
          if (diff < 0 || diff > 2880000)
            logger.warn("%s: message post time vs message processor insertion time diff is bizarre for original id: %s, delta from now: %s ".format(dao.processorId, postedId, diff))
          else
            timeInQueue.update(diff)
        }
        logger.info("%s flushed %s messages".format(dao.processorId, finalPostedIds.size))
      }

    } catch {
      case e: Exception => {
        logger.warn("%s error while trying to process message batch: %s".format(dao.processorId, ExceptionUtils.getRootCauseMessage(e)), BackplaneConfig.getDebugException(e))
        throw e
      }
    }
  }

  private final val ID_FIELD_NAME = dao.idField.name

  private def fixId(messageData: Map[String,String], lastId: String): (BMT, String) = {
    val postedId = messageData.get(ID_FIELD_NAME).getOrElse {
      logger.warn("%s: message was not assigned an ID when it was posted, generating it at queue processing time: %s".format(dao.processorId, messageData.mkString("\n")))
      BackplaneMessage.generateMessageId(new Date)
    }
    val msg = dao.mpInstantiate( messageData.map {
      case (ID_FIELD_NAME, posted) if BackplaneMessage.dateFromId(posted).exists(_.getTime < BackplaneMessage.timeFromId(lastId)) => {
        val newId = BackplaneMessage.generateMessageId(new Date(BackplaneMessage.timeFromId(lastId) + 1))
        logger.warn("%s: message id %s was posted before latest id %s, fixed to: %s".format(dao.processorId, posted, lastId, newId))
        ID_FIELD_NAME -> newId
      }
      case other => other
    })

    (msg, postedId)
  }

  /**
   * Store one message to redis, as part of the supplied redis pipeline / transaction.
   * The following associated redis data structures are created or updated:
   * - key = dao.itemKey(msgId), value = serialized message string
   * - key = dao.messagesKey, add sorted set entry, score = msg time, value = msg metadata
   * - key = dao.busKey(bus), add sorted set entry, score = msg time, value = msgId
   * - key = dao.channelKey(channel), add sorted set entry, score = msg time, value = msgId
   *
   * @return pair of:
   *         last, possibly updated, id
   *         old message id prepended to the list of supplied insertion times
   */
  protected def processSingleMessage(backplaneMessage: BMT, postedId: String, insertionTimes: List[String], redisClient: RedisCommand): (String,List[String]) = {
    val msgId = backplaneMessage.id
    val messageTime = BackplaneMessage.timeFromId(msgId)
    redisClient.hmset(dao.itemKey(msgId), backplaneMessage)
    redisClient.expire(dao.itemKey(msgId), DateTimeUtils.getExpireSeconds(msgId, backplaneMessage.expiration, backplaneMessage.sticky))
    redisClient.zadd(dao.channelKey(backplaneMessage.channel), messageTime, msgId)
    redisClient.zadd(dao.busKey(backplaneMessage.bus), messageTime, msgId)
    redisClient.zadd(dao.messagesKey, messageTime, metaData(backplaneMessage.bus, backplaneMessage.channel, msgId, backplaneMessage.expiration))
    redisClient.lpop(dao.messagesQueueKey)
    logger.info("%s pipelined message: %s -> %s".format(dao.processorId, postedId, msgId))
    (msgId, postedId :: insertionTimes)
  }

}
