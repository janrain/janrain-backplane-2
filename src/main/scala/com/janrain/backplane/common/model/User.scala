package com.janrain.backplane.common.model

/**
 * Base class for user types.
 *
 * @author Johnny Bufu
 */
abstract class User[UF <: UserField](userTyp: String, data: Map[String,String], fields: Traversable[UF])
  extends Message[UF](data + (User.typeFieldName -> userTyp), fields)

object User {
  val typeFieldName = "type"
}

trait UserFieldEnum extends MessageFieldEnum {
  trait EnumVal extends Value with UserField
  val TYPE = new EnumVal { def name = User.typeFieldName; final def required = true }
  val USER = new EnumVal { def name = "user"; def required = true }
  val PWDHASH = new EnumVal { def name = "pwdhash"; def required = true }
}

trait UserField extends MessageField