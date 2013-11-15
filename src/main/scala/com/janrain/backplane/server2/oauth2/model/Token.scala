package com.janrain.backplane.server2.oauth2.model

import com.janrain.backplane.common.model.{MessageField, MessageFieldEnum, Message}
import com.janrain.backplane2.server.{Scope, GrantType}
import com.janrain.backplane.common.MessageException
import com.janrain.oauth2.{OAuth2, TokenException}
import com.janrain.oauth2.OAuth2._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.janrain.backplane.server2.TokenSource
import com.janrain.backplane.server2.dao.{BP2DAOs}
import com.janrain.backplane.dao.{LegacySupport, DaoException}
import com.janrain.util.{Utils, Loggable}
import scala.collection.JavaConversions._
import java.util.Date
import com.janrain.backplane.server2.model.Backplane2MessageFields

/**
 * @author Johnny Bufu
 */
class Token(data: Map[String,String]) extends Message(data, TokenFields.values) with LegacySupport[com.janrain.backplane2.server.Token] {

  def this(javaData: java.util.Map[String,String]) = this(LegacySupport.fromLegacy(javaData))

  def idField = TokenFields.ID

  def asLegacy = new com.janrain.backplane2.server.Token(mapAsJavaMap(this))

  def expireSeconds: Option[Int] = get(TokenFields.EXPIRES).map( Utils.secondsLeft(_) )

  def grantType: GrantType = get(TokenFields.TYPE).map(typeValue => {
    try {
      GrantType.valueOf(typeValue)
    } catch {
      case e: IllegalArgumentException =>
        throw new IllegalStateException("Invalid GrantType on for TokenFields.TYPE, should have been validated on grant creation: " + typeValue)
    }
  })
  .getOrElse( throw new IllegalStateException("Missing value for TokenFields.TYPE"))


  def scope: Scope = get(TokenFields.SCOPE).map(scopeValue => {
    try {
      new Scope(scopeValue)
    } catch {
      case e: TokenException =>
        throw new IllegalStateException("Invalid value on for TokenFields.SCOPE, should have been validated on grant creation: " + scopeValue)
    }
  })
  .getOrElse( throw new IllegalStateException("Missing value for TokenFields.SCOPE"))

  def allowedSources: Seq[TokenSource.EnumVal] = {
    val gt = grantType
    if (! gt.isPrivileged) TokenSource.values
    else if (gt.isRefresh) Seq(TokenSource.POSTBODY_REFRESH)
    else Seq(TokenSource.AUTHHEADER)
  }

  def backingGrants: List[String] = get(TokenFields.BACKING_GRANTS).map(_.split(Token.GRANTS_SEPARATOR)).toIterable.flatten.toList

  def response(refreshToken: String): java.util.Map[String, AnyRef] = {
    var response = new java.util.LinkedHashMap[String,AnyRef]()
    response.put(OAUTH2_TOKEN_TYPE_PARAM_NAME, OAUTH2_TOKEN_TYPE_BEARER)
    response.put(OAUTH2_ACCESS_TOKEN_PARAM_NAME, id)
    get(TokenFields.SCOPE).foreach( response.put(OAUTH2_SCOPE_PARAM_NAME, _) )
    expireSeconds.foreach( expValue => response.put(OAUTH2_TOKEN_RESPONSE_EXPIRES, expValue.toString ) )
    if (refreshToken != null)
      response.put(OAUTH2_REFRESH_TOKEN_PARAM_NAME, refreshToken)
    response
  }

}

object Token extends Loggable {

  final val TOKEN_LENGTH: Int = 25
  final val LEGACY_TOKEN_LENGTH: Int = 20

  private def looksLikeOurToken(tokenString: String) = Option(GrantType.fromTokenString(tokenString))
    .map(gt => tokenString.substring(gt.getTokenPrefix.length))
    .exists(t => t.length == TOKEN_LENGTH || t.length == LEGACY_TOKEN_LENGTH)

  final val GRANTS_SEPARATOR = " "

  def fromRequest(request: HttpServletRequest): Option[Token] = {

    // multiple sources, multiple values per source considered
    val sourcesAndTokens = TokenSource.values.foldLeft( List[(TokenSource.EnumVal,List[String])]() ) {
      case (found, src) => src.extract(request, found.flatMap(_._2)) :: found
    }

    val foldAccInit: Option[(TokenSource.EnumVal, String)] = None
    val validSourceAndTokenString = sourcesAndTokens.foldLeft(foldAccInit) {
      case (found@Some(_), (nextSource, Nil)) => found
      case (found@Some(_), (nextSource, nextTokens)) => {
        throw new TokenException("token found in more than one source: %s and %s".format(found.get._1, nextSource))
      }
      case (None, (nextSource, Nil)) => None
      case (None, (nextSource, List(oneToken))) => Some((nextSource, oneToken))
      case (None, (nextSource, nextTokens)) => {
        throw new TokenException("multiple tokens found in " + nextSource)
      }
    }

    validSourceAndTokenString.map {
      case (source, tokenString) => {
        if (! Token.looksLikeOurToken(tokenString))
          throw new TokenException("invalid token: " + tokenString, HttpServletResponse.SC_FORBIDDEN)

        try {
          val token: Option[Token] = BP2DAOs.tokenDao.get(tokenString)

          if (token.exists(t => Message.isExpired(t.get(TokenFields.EXPIRES)))) {
            throw new TokenException("expired token: " + tokenString, HttpServletResponse.SC_FORBIDDEN)
          }

          if (! token.forall(t => t.allowedSources.contains(source))) {
            throw new TokenException("token source not allowed: " + source, HttpServletResponse.SC_FORBIDDEN)
          }

          if (token.exists(t => source.isRefresh != t.grantType.isRefresh)) {
            logger.warn("invalid token / source: %s / %s ".format(token, source))
            throw new TokenException("invalid token: " + tokenString, HttpServletResponse.SC_FORBIDDEN)
          }

          token
        } catch {
          case e: DaoException => {
            logger.error("Error looking up token: " + tokenString)
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error looking up token", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          }
        }

      }
    }.flatten
  }
}

object TokenFields extends MessageFieldEnum {

  type TokenField = EnumVal

  sealed trait EnumVal extends Value with MessageField {
    def required = true
  }

  val ID = new TokenField { def name = "id" }

  val TYPE = new TokenField { def name = "type"
    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      fieldValue.foreach(grantValue => {
        try {
          GrantType.valueOf(grantValue)
        } catch {
          case e: IllegalArgumentException =>
            throw new MessageException("Invalid GrantType on for TokenField.Type " + grantValue)
        }
      })
    }
  }

  val EXPIRES = new TokenField { def name = "expires"
    override def required = false
    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      if (! wholeMessage.asInstanceOf[Token].grantType.isRefresh)
        validateRequired(fieldValue)
      validateIso8601Date(fieldValue)
    }
  }

  val SCOPE = new TokenField { def name = "scope"

    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      fieldValue.foreach(scopeValue => {
        try {
          val scope = new Scope(scopeValue)
          if (! wholeMessage.asInstanceOf[Token].grantType.isPrivileged) {
            val buses = scope.getScopeFieldValues(Backplane2MessageFields.BUS)
            val channels = scope.getScopeFieldValues(Backplane2MessageFields.CHANNEL)
            if (buses == null || buses.size > 1 || channels == null || channels.size > 1) {
              throw new MessageException("invalid scope for anonymous token, must have exactly one bus and one channel specified: " + scopeValue)
            }
          }
        } catch {
          case te: TokenException => throw new MessageException("Invalid Scope: " + scopeValue)
          case cce: ClassCastException => throw new MessageException("Invalid token message for scope validation: " + wholeMessage)
        }
      })
    }
  }

  val ISSUED_TO_CLIENT = new TokenField { def name = "issued_to_client"

    override def required = false

    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      validatePrivileged(this, fieldValue:Option[String], wholeMessage: Message[_])
    }
  }

  val CLIENT_SOURCE_URL = new TokenField { def name = "client_source_url"

    override def required = false

    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      validateUrl(fieldValue)
      validatePrivileged(this, fieldValue:Option[String], wholeMessage: Message[_])
    }
  }

  val BACKING_GRANTS = new TokenField { def name = "backing_grants"

    override def required = false

    override def validate(fieldValue:Option[String], wholeMessage: Message[_]) {
      validatePrivileged(this, fieldValue:Option[String], wholeMessage: Message[_])
    }
  }

  private def validatePrivileged(field: TokenField, fieldValue: Option[String], wholeMessage: Message[_]) {
    try {
      if (wholeMessage.asInstanceOf[Token].grantType.isPrivileged)
        field.validateRequired(fieldValue)
    } catch {
      case cce: ClassCastException => throw new MessageException("Field " + field.name + " cannot be blank")
    }
  }

}
