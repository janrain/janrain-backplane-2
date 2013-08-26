package com.janrain.backplane.config.dao

import com.janrain.backplane.dao.Dao
import com.janrain.backplane.config.model.ServerConfig

/**
 * @author Johnny Bufu
 */
trait ServerConfigDao extends Dao[ServerConfig] {
  def oneServerConfig: Option[ServerConfig]
}
