package com.janrain.backplane.server2.oauth2.model

import com.janrain.backplane.common.DateTimeUtils
import java.util.Date
import com.janrain.util.RandomUtils
import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}

/**
 * @author Johnny Bufu
 */
class AuthorizationDecisionKey(data: Map[String,String]) extends Message(data, AuthorizationDecisionKeyFields.values) {

  def this(authCookie: String) = this(
    Map( AuthorizationDecisionKeyFields.AUTH_COOKIE.name -> authCookie,
         AuthorizationDecisionKeyFields.KEY.name
           -> RandomUtils.randomString(AuthorizationDecisionKey.AUTHORIZATION_DECISION_KEY_LENGTH),
         AuthorizationDecisionKeyFields.EXPIRES.name
           -> DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AuthorizationDecisionKey.AUTHORIZATION_DECISION_TIMEOUT_SECONDS * 1000))
    ))

  def idField = AuthorizationDecisionKeyFields.KEY
}

object AuthorizationDecisionKey {
  private[model] final val AUTHORIZATION_DECISION_KEY_LENGTH: Int = 30
  private[model] final val AUTHORIZATION_DECISION_TIMEOUT_SECONDS: Long = 300L
}

object AuthorizationDecisionKeyFields extends MessageFieldEnum {

  type AuthorizationDecisionKeyField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val KEY = new AuthorizationDecisionKeyField { def name = "key" }

  val AUTH_COOKIE = new AuthorizationDecisionKeyField { def name = "auth_cookie" }

  val EXPIRES = new AuthorizationDecisionKeyField { def name = "expires" }
}
