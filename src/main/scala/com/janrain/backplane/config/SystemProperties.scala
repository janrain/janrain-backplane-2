package com.janrain.backplane.config

import java.net.InetAddress
import com.janrain.commons.util.InitSystemProps
import javax.naming.InitialContext
import org.apache.commons.lang.StringUtils
import scala.Predef.String
import com.janrain.util.{Utils, Loggable}
import org.apache.commons.lang.exception.ExceptionUtils

/**
 * @author Johnny Bufu
 */
object SystemProperties extends InitSystemProps("/log4j.xml") with Loggable {

  final val ZOOKEEPER_SERVERS: String = "ZOOKEEPER_SERVERS"
  load(ZOOKEEPER_SERVERS, true)

  final val REDIS_SERVER_PRIMARY: String = "REDIS_SERVER_PRIMARY"
  load(REDIS_SERVER_PRIMARY, true)

  final val REDIS_SERVER_READS: String = "REDIS_SERVER_READS"
  load(REDIS_SERVER_READS, true)

  final val GRAPHITE_SERVER: String = "GRAPHITE_SERVER"
  load(GRAPHITE_SERVER, false)

  final val IP_WHITE_LIST: String = "IP_WHITE_LIST"
  load(IP_WHITE_LIST, false)

  final val ANALYTICS_FALLBACK_PATH = "ANALYTICS_FALLBACK_PATH"
  load(ANALYTICS_FALLBACK_PATH, false)

  final val ANALYTICS_FALLBACK_MAXSIZEMB = "ANALYTICS_FALLBACK_MAXSIZEMB"
  load(ANALYTICS_FALLBACK_MAXSIZEMB, false)

  final val ANALYTICS_LOGGING = "ANALYTICS_LOGGING"
  load(ANALYTICS_LOGGING, false)

  load(InitSystemProps.AWS_INSTANCE_ID)
  final val INSTANCE_ID = Utils.getRequiredSystemProperty(InitSystemProps.AWS_INSTANCE_ID)
  logger.info("Configured Backplane instance: " + INSTANCE_ID)

  def machineName: String = try {
    "backplane/" + InetAddress.getLocalHost.getHostName
  } catch {
    case e: Throwable => {
      logger.warn("getLocalHost() call failed: " + ExceptionUtils.getRootCause(e), BackplaneConfig.getDebugException(e))
      "n/a"
    }
  }

  // copied from federate-utils/v1.2.3, since we're stuck for now with InitSystemProps from commons-supersimpledb
  private def load(paramName: String, required: Boolean) {
    var result = System.getProperty(paramName)
    if (StringUtils.isBlank(result)) {
      try {
        val initCtx = new InitialContext
        result = initCtx.lookup("java:comp/env/" + paramName).asInstanceOf[String]
        System.setProperty(paramName, result)
        System.out.println("Parameter " + paramName + " fetched from context and inserted as system property")
      } catch {
        case e: Exception => {
          //continue
          if (required) {
            System.out.println("An error occurred trying to locate required parameter " + paramName + " => " + e.getMessage)
          }
        }
      }
    } else {
      System.out.println("Parameter " + paramName + " exists as a system property")
    }
  }

  private def load(paramName: String) {
    load(paramName, false)
  }

}
