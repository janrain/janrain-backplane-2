package com.janrain.backplane.config.model

import com.janrain.backplane.common.model.{UserField, User}

/**
 * Mixin for User subclasses who want to declare a password/hash field.
 *
 * @author Johnny Bufu
 */
trait Password[UF <: UserField, UT <: User[UF]] {

  self: UT =>

  def pwdHashField: UF

}
