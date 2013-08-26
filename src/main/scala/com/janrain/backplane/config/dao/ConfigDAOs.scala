package com.janrain.backplane.config.dao

import com.janrain.backplane.dao.redis.RedisMessageDao
import com.janrain.backplane.config.model.{AdminFields, ServerConfigFields, ServerConfig, Admin}
import com.janrain.backplane.dao.{LegacyDaoForwarder, PasswordHasherDao, ExpiringCacheDao}
import com.janrain.backplane.config.SystemProperties

object ConfigDAOs {

  private val DEFAULT_CONFIG_CACHE_SECONDS = 60L // 1 min

  val adminDao: AdminDao = new RedisMessageDao[Admin]("admin:") with AdminDao
    with PasswordHasherDao[AdminFields.EnumVal,Admin]
    with LegacyDaoForwarder[com.janrain.backplane2.server.config.User, Admin] {

    protected def instantiate(data: Map[_, _]) = new Admin( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def storeFromLegacy(convertedItem: Admin) {
      storeNoPwdHash(convertedItem)
    }

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getAdminDao
  }

  val serverConfigDao: ServerConfigDao = new RedisMessageDao[ServerConfig]("backplane_server_config")
    with ServerConfigDao
    with ExpiringCacheDao[ServerConfig] {

    def oneServerConfig = get("SERVER_CONFIG_KEY_IGNORED")

    override protected def getKey(itemId: String) =  SystemProperties.INSTANCE_ID + ":" + keyPrefix// ignore itemId, there's only one, don't allow access to other instances' config

    protected def instantiate(data: Map[_, _]) = new ServerConfig( data.map( kv => kv._1.toString -> kv._2.toString ))

    def maxCacheAgeMillis =
      Option(cache.get(SystemProperties.INSTANCE_ID)).flatMap(_._2.flatMap(_.get(ServerConfigFields.CONFIG_CACHE_AGE_SECONDS)))
        .map(1000L * _.toLong)
        .getOrElse(1000L * DEFAULT_CONFIG_CACHE_SECONDS)
  }
}
