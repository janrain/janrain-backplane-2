package com.janrain.backplane.config

import com.janrain.backplane.dao.redis.Redis
import com.janrain.backplane.server2.dao.BP2DAOs
import com.janrain.backplane.config.dao.ConfigDAOs

/**
 * We want these to initialize (and fail, if they do) when the web app is deployed.
 *
 * @author Johnny Bufu
 */
class ScalaSingletonsInit {
  SystemProperties.INSTANCE_ID
  Redis.writePool
  com.janrain.commons.util.Utf8StringUtils.UTF8
  ConfigDAOs.serverConfigDao.oneServerConfig.get
  BP2DAOs.messageDao
}
