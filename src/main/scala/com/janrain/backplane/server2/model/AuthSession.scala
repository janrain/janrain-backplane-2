package com.janrain.backplane.server2.model

import com.janrain.backplane.common.DateTimeUtils
import java.util.Date
import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}

/**
 * @author Johnny Bufu
 */
class AuthSession(data: Map[String,String]) extends Message(data, AuthSessionFields.values) {

  def this(authUser: String, cookie: String) = this(
    Map( AuthSessionFields.COOKIE.name -> cookie,
         AuthSessionFields.AUTH_USER.name -> authUser,
         AuthSessionFields.EXPIRES.name
           -> DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AuthSession.AUTH_SESSION_TIMEOUT_SECONDS * 1000))
    ))

  def idField = AuthSessionFields.COOKIE
}

object AuthSession {
  private[model] final val AUTH_SESSION_TIMEOUT_SECONDS: Long = 3600L
}

object AuthSessionFields extends MessageFieldEnum {

  type AuthSessionField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val COOKIE = new AuthSessionField { def name = "cookie" }

  val AUTH_USER = new AuthSessionField { def name = "auth_user" }

  val EXPIRES = new AuthSessionField { def name = "expires" }
}
