package com.janrain.backplane.config.model

import scala.reflect.BeanInfo

/**
 * Data bean with admin credentials posted from admin form.
 *
 * @author Johnny Bufu
 */
@BeanInfo class AdminPostData(var username: String, var password: String)
