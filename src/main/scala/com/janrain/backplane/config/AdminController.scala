package com.janrain.backplane.config

import org.springframework.web.bind.annotation.{RequestMethod, RequestMapping}
import com.janrain.util.{ServletUtil, Loggable}
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.janrain.backplane.config.dao.ConfigDAOs
import com.janrain.backplane.config.model.{AdminFields, Admin, ServerConfig, ServerConfigFields}
import org.apache.commons.lang.exception.ExceptionUtils
import com.janrain.backplane.common.HmacHashUtils

/**
 * @author Johnny Bufu
 */
@RequestMapping (value = Array ("/admin/*") )
class AdminController extends Loggable {

  private val ADMIN_USER = "bpadmin"

  @RequestMapping(value = Array("/"), method = Array(RequestMethod.GET, RequestMethod.HEAD))
  def admin(request: HttpServletRequest, response: HttpServletResponse): ModelAndView = {

    ServletUtil.checkSecure(request)

    if (RequestMethod.HEAD.toString == request.getMethod) {
      response.setContentLength(0)
    }

    new ModelAndView("admin") {
      {
        addObject("adminUserExists", ConfigDAOs.adminDao.get(ADMIN_USER).isDefined)
        addObject("configKey", SystemProperties.INSTANCE_ID)
        addObject("debugMode", BackplaneConfig.isDebugMode)
        addObject("defaultMessagesMax", ConfigDAOs.serverConfigDao.oneServerConfig.get.get(ServerConfigFields.DEFAULT_MESSAGES_MAX))
      }
    }
  }

  @RequestMapping(value = Array("/update"), method = Array(RequestMethod.POST))
  def updateConfiguration(request: HttpServletRequest, response: HttpServletResponse) = {

    ServletUtil.checkSecure(request)

    val existingConfig = ConfigDAOs.serverConfigDao.oneServerConfig.get

    val newConfig = new ServerConfig(existingConfig ++ Map(
      ServerConfigFields.DEBUG_MODE.name -> request.getParameter("debug_mode"),
      ServerConfigFields.DEFAULT_MESSAGES_MAX.name -> request.getParameter("default_messages_max")
    ))

    val statusMsg =
    try {
      ConfigDAOs.serverConfigDao.store(newConfig)
      logger.info(newConfig.toString())
      "configuration updated"
    } catch {
      case e: Throwable => {
        logger.error(e)
        "error updating configuratio: " + ExceptionUtils.getRootCauseMessage(e)
      }
    }

    new ModelAndView("adminadd") {{
      addObject("message", statusMsg)
    }}
  }

  @RequestMapping(value = Array("/add"), method = Array(RequestMethod.POST))
  def addAdmin(request: HttpServletRequest, response: HttpServletResponse) = {

    ServletUtil.checkSecure(request)

    val statusMsg: String =
    try {
      ConfigDAOs.adminDao.get(ADMIN_USER)
        // be sure no record exists
        .map(admin => "Admin user already exists.  You must delete the entry from the database before submitting a new admin user.")
        // no admin exists...
        .getOrElse({
          if (ADMIN_USER != request.getParameter("username")) "Admin user name must be " + ADMIN_USER
          else {
            ConfigDAOs.adminDao.store(new Admin(Map(
              AdminFields.USER.name -> ADMIN_USER,
              AdminFields.PWDHASH.name -> HmacHashUtils.hmacHash(request.getParameter("password"))
            )))
            "Admin user " + ADMIN_USER + " updated"
          }
        })
    } catch {
      case e: Throwable => {
        logger.error(e)
        "error updating admin user " + ExceptionUtils.getRootCauseMessage(e)
      }
    }

    if (RequestMethod.HEAD.toString == request.getMethod) {
      response.setContentLength(0)
    }

    new ModelAndView("adminadd") {{
      addObject("message", statusMsg)
    }}
  }
}
