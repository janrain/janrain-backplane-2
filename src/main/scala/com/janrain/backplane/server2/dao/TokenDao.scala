package com.janrain.backplane.server2.dao

import com.janrain.backplane.server2.oauth2.model.Token
import com.janrain.backplane.dao.{ExpiringDao, DaoAll}

/**
 * @author Johnny Bufu
 */
trait TokenDao extends DaoAll[Token] with ExpiringDao[Token] {
  val expireSeconds = 3600 // 1h
}