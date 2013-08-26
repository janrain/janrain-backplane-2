package com.janrain.backplane.server2.oauth2.model

import com.janrain.backplane.common.model.{UserFieldEnum, User}
import com.janrain.backplane.config.model.Password
import scala.collection.JavaConversions._
import com.janrain.backplane.dao.LegacySupport

/**
 * @author Johnny Bufu
 */
class BusOwner(data: Map[String,String]) extends User("bp2BusOwner", data, BusOwnerFields.values)
  with Password[BusOwnerFields.EnumVal, BusOwner]
  with LegacySupport[com.janrain.backplane2.server.config.User] {

  def this(username: String, pwdhash: String) = this(Map(
    BusOwnerFields.USER.name -> username,
    BusOwnerFields.PWDHASH.name -> pwdhash
  ))

  def this(javaData: java.util.Map[String,String]) = this(LegacySupport.fromLegacy(javaData))

  def idField = BusOwnerFields.USER

  def pwdHashField = BusOwnerFields.PWDHASH

  def asLegacy = new com.janrain.backplane2.server.config.User(LegacySupport.toLegacy(this))
}

object BusOwnerFields extends UserFieldEnum {
  trait BusOwnerEnumVal extends EnumVal
  // no new, BusOwner.-specific fields
}
