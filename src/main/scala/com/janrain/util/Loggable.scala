package com.janrain.util

import org.apache.log4j.Logger

/**
 * @author Johnny Bufu
 */
trait Loggable { self =>

  val logger = Logger.getLogger(self.getClass)

  def logDebug(msg: => String) {
    if (logger.isDebugEnabled) logger.debug(msg)
  }
}
