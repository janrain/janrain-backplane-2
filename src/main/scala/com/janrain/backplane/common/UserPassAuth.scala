package com.janrain.backplane.common

import com.janrain.backplane.common.model.{UserField, User}
import com.janrain.backplane.dao.Dao
import com.janrain.backplane.config.model.Password

/**
 * Password authentication mixin for "user with password field" types.
 *
 * @author Johnny Bufu
 */
trait UserPassAuth[UF <: UserField, UT <: User[UF] with Password[UF, UT]] {

  this: Dao[UT] =>

  def getAuthenticated(user: String, password: String): UT =
    get(user).filter(u => u.get(u.pwdHashField).exists(HmacHashUtils.checkHmacHash(password, _)))
    .getOrElse { throw new AuthException("access denied") }
}
