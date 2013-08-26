package com.janrain.backplane.server2.oauth2.model

import com.janrain.oauth2.{AuthorizationException, ValidationException, OAuth2}
import com.janrain.servlet.InvalidRequestException
import org.apache.commons.lang.exception.ExceptionUtils
import com.janrain.backplane.common.DateTimeUtils
import java.util.Date
import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}
import com.janrain.backplane.server2.dao.BP2DAOs

/**
 * @author Johnny Bufu
 */
class AuthorizationRequest(data: Map[String,String]) extends Message(data, AuthorizationRequestFields.values) {

  def this(cookie: String, data: Map[String,String]) = this(data ++
    Map( AuthorizationRequestFields.COOKIE.name -> cookie,
         AuthorizationRequestFields.EXPIRES.name
           -> DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AuthorizationRequest.AUTH_REQUEST_TIMEOUT_SECONDS * 1000))
    ))

  def idField = AuthorizationRequestFields.COOKIE

  def getRedirectUri: String = {
    val client_id = get(AuthorizationRequestFields.CLIENT_ID)
    client_id
      .map(BP2DAOs.clientDao.get(_))
      .map(validRedirectUri(_, get(AuthorizationRequestFields.REDIRECT_URI)))
      .getOrElse(
       throw new AuthorizationException(OAuth2.OAUTH2_AUTHZ_DIRECT_ERROR, "invalid client_id: " + client_id, this)
      )
  }

  private def validRedirectUri(client: Option[Client], requestRedirectUri: Option[String]): String = {
    val clientRedirectUri = client.flatMap(_.get(ClientFields.REDIRECT_URI))
    (for {
      configuredUri <- clientRedirectUri
      requestedUri <- requestRedirectUri
    } yield {
      try {
        OAuth2.validateRedirectUri(requestedUri, configuredUri)
        requestedUri
      } catch {
        case e: ValidationException =>
          throw new AuthorizationException(OAuth2.OAUTH2_AUTHZ_DIRECT_ERROR, "invalid redirect_uri: " + e.getMessage, this)
      }
    })
    .getOrElse(clientRedirectUri.getOrElse(
      throw new AuthorizationException(OAuth2.OAUTH2_AUTHZ_DIRECT_ERROR, "no redirect_uri configured for client_id: " + id, this)))
  }
}

object AuthorizationRequest {
  private[model] final val AUTH_REQUEST_TIMEOUT_SECONDS: Long = 1200L
}

object AuthorizationRequestFields extends MessageFieldEnum {

  type AuthorizationRequestField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val COOKIE = new AuthorizationRequestField { def name = "cookie" }

  val EXPIRES = new AuthorizationRequestField { def name = "expires" }

  val CLIENT_ID = new AuthorizationRequestField { def name = "client_id" }

  val RESPONSE_TYPE  = new AuthorizationRequestField { def name = "response_type"

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      if (fieldValue.exists(_ != OAuth2.OAUTH2_TOKEN_RESPONSE_TYPE_CODE)) {
        throw new IllegalArgumentException("Unsupported OAuth2 response_type: " + fieldValue)
      }
    }
  }

  val REDIRECT_URI = new AuthorizationRequestField { def name = "redirect_uri"; override def required = false

    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      fieldValue.foreach(
        try {
          OAuth2.validateRedirectUri(_)
        } catch {
          case e: ValidationException => throw new InvalidRequestException(ExceptionUtils.getRootCauseMessage(e))
        })
    }
  }

  val SCOPE = new AuthorizationRequestField { def name = "scope"; override def required = false }

  val STATE = new AuthorizationRequestField { def name = "state"; override def required = false }
}