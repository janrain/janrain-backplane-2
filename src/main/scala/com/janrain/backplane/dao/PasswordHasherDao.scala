package com.janrain.backplane.dao

import com.janrain.backplane.config.model.Password
import com.janrain.backplane.common.model.{UserField, User}
import com.janrain.backplane.common.HmacHashUtils

/**
 * Stackable modification trait for "user with password field" types
 * that transparently hashes the clear text provided password into the password hash field.
 *
 * @author Johnny Bufu
 */
trait PasswordHasherDao[UF <: UserField, UT <:  User[UF] with Password[UF,UT]] extends Dao[UT] {

  this: MessageDao[UT] =>

  def storeNoPwdHash(item: UT) {
    super.store(item)
  }

  abstract override def store(item: UT) {
    super.store(hashPwd(item))
  }

  abstract override def store(items: UT*): List[(String,Boolean)] = {
    super.store(items.map(hashPwd): _*)
  }

  private def hashPwd(item: UT): UT = {
    val pwdFieldName = item.pwdHashField.name
    instantiate(item.map(kv => if (kv._1 != pwdFieldName) kv else kv._1 -> HmacHashUtils.hmacHash(kv._2)))
  }

}
