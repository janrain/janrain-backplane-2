package com.janrain.backplane.config.model

import com.janrain.backplane.config.SystemProperties
import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}

/**
 * @author Johnny Bufu
 */
class ServerConfig(data: Map[String,String]) extends Message(ServerConfig.DEFAULT ++ data, ServerConfigFields.values) {
  def idField = ServerConfigFields.ID
  def isDebugMode = get(ServerConfigFields.DEBUG_MODE).getOrElse("false").toBoolean
}

object ServerConfig {

  final val DEFAULT = Map(
    ServerConfigFields.ID.name -> SystemProperties.INSTANCE_ID,
    ServerConfigFields.DEBUG_MODE.name -> false.toString,
    ServerConfigFields.CONFIG_CACHE_AGE_SECONDS.name -> 10.toString,
    ServerConfigFields.CLEANUP_INTERVAL_MINUTES.name -> 2.toString,
    ServerConfigFields.TOKEN_CACHE_MAX_MB.name -> 100.toString
  )
}

object ServerConfigFields extends MessageFieldEnum {

  type ServerConfigField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val ID = new ServerConfigField { def name = "id" }
  val DEBUG_MODE = new ServerConfigField { def name = "debug_mode" }
  val CONFIG_CACHE_AGE_SECONDS = new ServerConfigField { def name = "config_cache_age_seconds" }
  val CLEANUP_INTERVAL_MINUTES = new ServerConfigField { def name = "cleanup_interval_minutes" }
  val DEFAULT_MESSAGES_MAX = new ServerConfigField { def name = "default_messages_max"
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      if (required && fieldValue.isDefined) validateLong(fieldValue)
    }
  }
  val TOKEN_CACHE_MAX_MB = new ServerConfigField { def name = "token_cache_max_mb" }

  // must be exactly "true" to return messages obtained using the new DAO
  val BP1_MESSAGES_USE_NEW_DAO = new ServerConfigField { def name = "bp1_messages_use_new_dao" }

  // must be exactly "true" to instruct message processor to stop writing old serialization format and old index/redis keys
  val BP1_MP_STOP_WRITING_LEGACY_FORMAT = new ServerConfigField { def name = "bp1_mp_stop_writing_legacy_format" }

}