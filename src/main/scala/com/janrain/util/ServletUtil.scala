/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.janrain.util

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.ModelAndView
import org.apache.log4j.Logger
import scala.collection.JavaConversions._
import org.apache.commons.lang.StringUtils
import java.net.URLEncoder
import com.janrain.commons.util.Utf8StringUtils
import com.janrain.backplane.common.BackplaneServerException

object ServletUtil {

  final val DIRECT_RESPONSE = "direct_response" // both view name and jsp variable

  final val PROTOCOL_ACTION_REQUEST_ATTR_NAME = "idsProtocolAction"
  final val CUSTOMER_CONFIG_REQUEST_ATTR_NAME = "idsCustomerConfig"
  final val INTERNAL_FORWARD_ATTR_NAME = "idsInternalForward"
  final val ORIGINAL_REQUEST_URL = "idsOriginalRequestUrl"

  final val FORWARDED_PROTO_HEADER = "x-forwarded-proto"

  def isSecure(request: HttpServletRequest) =
    request.isSecure ||
      ("https" equalsIgnoreCase request.getHeader(FORWARDED_PROTO_HEADER)) ||
      ("localhost" equalsIgnoreCase request.getServerName)

  def isForwarded(request: HttpServletRequest) =
    StringUtils.isNotEmpty(request.getHeader(FORWARDED_PROTO_HEADER))

  def checkSecure(request: HttpServletRequest) {
    if (!isSecure(request)) {
      throw new BackplaneServerException("Connection must be made over https", HttpStatus.FORBIDDEN)
    }
  }

  def requestScheme(request: HttpServletRequest) =
    if (request.isSecure || ("https" equalsIgnoreCase request.getHeader(FORWARDED_PROTO_HEADER))) "https" else "http"

  def defaultPort(scheme: String) =
    if ("http".equalsIgnoreCase(scheme)) 80 else if ("https".equalsIgnoreCase(scheme)) 443 else -1

  def portAndContextPath(scheme: String, request: HttpServletRequest) =
    if (isForwarded(request)) ""
    else (if (defaultPort(scheme) != request.getServerPort) ":" + request.getServerPort  else "") + request.getContextPath

  def error(code: HttpStatus, errMsg: String, response: HttpServletResponse,
            logger: Logger = Logger.getLogger(this.getClass) ) = {
    logger.error(errMsg)
    response.setStatus(code.value)
    new ModelAndView(ERROR_JSP, Map(ERROR_FIELD -> errMsg))
  }

  def singleValueRequestParams(request: HttpServletRequest): Map[String,String] = (for {
    entry <- request.getParameterMap
    (key, value) = entry match {
      case (k: String, v: Array[String]) if v.length > 0 => (k, v(0))
      case _ => (null, null)
    }
    if key != null
  } yield (key, value)).toMap

  def hasQueryParameter(request: HttpServletRequest, paramName: String) = {
    val paramNameEncoded = URLEncoder.encode(paramName, Utf8StringUtils.UTF8.name)
    Option(request.getQueryString).map(_.split("&")).toIterable.flatten.exists(_.startsWith(paramNameEncoded + "="))
  }

  def hasOnlyPostBodyParameter(request: HttpServletRequest, paramName: String) = {
    request.getParameterNames.contains(paramName) && ! hasQueryParameter(request, paramName)
  }

  def getHostFromUrl(url: String): String = {
    if (url == null || url.length == 0) return ""

    var doubleslash = url.indexOf("//")
    if (doubleslash == -1) doubleslash = 0
    else doubleslash += 2

    var end = url.indexOf('/', doubleslash)
    end = if (end >= 0) end else url.length

    url.substring(doubleslash, end)
  }

  private val ERROR_JSP = "error"

  private val ERROR_FIELD = "error"
}


