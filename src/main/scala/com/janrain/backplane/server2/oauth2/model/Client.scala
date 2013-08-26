package com.janrain.backplane.server2.oauth2.model

import com.janrain.backplane.common.model.{Message, UserFieldEnum, User}
import com.janrain.oauth2.{ValidationException, OAuth2}
import com.janrain.servlet.InvalidRequestException
import com.janrain.backplane.config.model.Password
import scala.collection.JavaConversions._
import com.janrain.backplane.dao.LegacySupport

/**
 * @author Johnny Bufu
 */
class Client(data: Map[String,String]) extends User("bp2Client", data, ClientFields.values)
  with Password[ClientFields.EnumVal, Client]
  with LegacySupport[com.janrain.backplane2.server.config.Client] {

  def this(javaData: java.util.Map[String,String]) = this(LegacySupport.fromLegacy(javaData))

  def idField = ClientFields.USER

  def pwdHashField = ClientFields.PWDHASH

  def asLegacy = new com.janrain.backplane2.server.config.Client(LegacySupport.toLegacy(this))
}

object ClientFields extends UserFieldEnum {

  trait ClientEnumVal extends EnumVal {
    def required = true
  }

  val SOURCE_URL = new ClientEnumVal { def name = "source_url" }

  val REDIRECT_URI = new ClientEnumVal { def name = "redirect_uri"
    override def validate(fieldValue: Option[String], wholeMessage: Message[_]) {
      super.validate(fieldValue, wholeMessage)
      try {
        fieldValue.foreach(OAuth2.validateRedirectUri(_))
      } catch {
        case e: ValidationException => throw new InvalidRequestException(e.getMessage)
      }
    }
  }
}
