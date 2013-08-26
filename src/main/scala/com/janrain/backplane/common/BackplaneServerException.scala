package com.janrain.backplane.common

import org.springframework.http.HttpStatus
import reflect.BeanProperty

class BackplaneServerException(
                          message: String,
                          @BeanProperty val code: HttpStatus = HttpStatus.BAD_REQUEST,
                          cause: Throwable = null)
  extends Exception(message, cause) {

  def this(message: String, cause: Throwable) = this(message, HttpStatus.BAD_REQUEST, cause)

  def this(message: String) = this(message, HttpStatus.BAD_REQUEST, null)
}
