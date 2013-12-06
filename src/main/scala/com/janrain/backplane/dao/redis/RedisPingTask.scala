package com.janrain.backplane.dao.redis

import java.util.concurrent.{ExecutorService, TimeUnit, Executors}
import com.janrain.util.Loggable
import com.netflix.curator.framework.recipes.leader.LeaderSelector
import com.janrain.commons.util.Pair
import scala.collection.JavaConversions._


/**
 * @author Johnny Bufu
 */
object RedisPingTask extends Loggable {

  def scalaObject = this

  def apply(leaderSelectors: java.util.List[LeaderSelector]): Pair[String, ExecutorService] = {

    val ping = Executors.newScheduledThreadPool(1)
    ping.scheduleWithFixedDelay(new Runnable() {
      override def run() {
        val pingResults = Redis.pingAll
        pingResults.foreach {
          case (name, result) => logger.info(s"PING " + name + " -> " + result.getOrElse("<n/a>"))
        }
        if (pingResults.forall(_._2.exists(_ == "PONG"))) {
          // requeue if all pings are successful
          leaderSelectors.foreach( _.requeue() )
        }
        // else no LeaderSelector API to cancel queue
      }
    }, 30, 10, TimeUnit.SECONDS)

    new Pair("redis/ping", ping)
  }

}
