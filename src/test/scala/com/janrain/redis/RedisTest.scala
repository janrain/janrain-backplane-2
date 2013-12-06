package com.janrain.redis

import com.janrain.backplane.dao.redis.{Redis => R}
import com.janrain.backplane.server2.dao.BP2DAOs

/**
 * @author Johnny Bufu
 */
object RedisTest {

  def tokenExpirationSeconds(token: String): Int = {
      R.readPool.withClient( c => {
      c.ttl("bp2local:bp2Token:" + token)
    }).map(_.toInt).getOrElse(throw new Exception("error getting token expiration for: " + token))
  }


}
