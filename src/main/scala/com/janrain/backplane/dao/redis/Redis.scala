package com.janrain.backplane.dao.redis

import com.redis.RedisClientPool
import com.janrain.util.{Utils, Loggable}
import com.janrain.backplane.config.SystemProperties
import scala.util.Random

/**
 * @author Johnny Bufu
 */
object Redis extends Loggable {
  private final val REDIS_DEFAULT_PORT = 6379
  private final val REDIS_DEFAULT_MAX_IDLE_CLIENTS = 100
  private final val REDIS_DEFAULT_DATABASE = 0


  private val (writeRedisHost, writeRedisPort) = redisHostAndPort(Utils.getRequiredSystemProperty(SystemProperties.REDIS_SERVER_PRIMARY))

  val writePool = new RedisClientPool(writeRedisHost, writeRedisPort, REDIS_DEFAULT_MAX_IDLE_CLIENTS, REDIS_DEFAULT_DATABASE)
  logger.info("initialized redis write pool for %s:%s, max idle connections: %s)".format(writeRedisHost, writeRedisPort, REDIS_DEFAULT_MAX_IDLE_CLIENTS))

  private val readPools: Array[(String,RedisClientPool)] =
    Utils.getRequiredSystemProperty(SystemProperties.REDIS_SERVER_READS)
    .split(",").map(r => redisHostAndPort(r))
    .map( hostAndPort => hostAndPort.toString() -> new RedisClientPool(hostAndPort._1, hostAndPort._2, REDIS_DEFAULT_MAX_IDLE_CLIENTS, REDIS_DEFAULT_DATABASE) )

  def readPool: RedisClientPool = readPools(Random.nextInt(readPools.size))._2

  logger.info("initialized redis read pool(s) for [%s], max idle connections per server: %s)".format(readPools.map(_._1).mkString(" "), REDIS_DEFAULT_MAX_IDLE_CLIENTS))


  private def redisHostAndPort(hostAndPort: String) = {
    val (host,port) = hostAndPort.span(_ != ':')
    try {
      (host, port.drop(1).toInt)
    } catch {
      case e: Throwable =>
        (host, REDIS_DEFAULT_PORT)
    }
  }
}
