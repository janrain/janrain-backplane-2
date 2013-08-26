package com.janrain.backplane.server2

import javax.servlet.http.HttpServletRequest
import com.janrain.util.Enum
import com.janrain.oauth2.OAuth2._
import scala.collection.JavaConversions._
/**
 * @author Johnny Bufu
 */
object TokenSource extends Enum {

  sealed trait EnumVal extends Value {
    /**
     * Extracts token source and list of token values from a request;
     * returned list is empty if no tokens were found for the given source.
     */
    def extract(request: HttpServletRequest, previouslyDiscovered: List[String]): (TokenSource.EnumVal, List[String])

    def isRefresh = ! isAccess

    def isAccess: Boolean
  }

  val QUERYPARAM = new EnumVal { def name = "queryparam"
    def isAccess = true
    def extract(request: HttpServletRequest, previouslyDiscovered: List[String]) = (this,
      Option(request.getQueryString).map(
        _.split("&")
        .map(_.span(_ != '='))
        .collect {
          case (name, value) if name == OAUTH2_ACCESS_TOKEN_PARAM_NAME
            => if (value.startsWith("=")) value.substring(1) else value
        }
      ).toIterable.flatten.toList
    )
  }

  val POSTBODY = new EnumVal { def name = "postbody"
    def isAccess = true
    def extract(request: HttpServletRequest, previouslyDiscovered: List[String]) = (this,
      Option(request.getParameterValues(OAUTH2_ACCESS_TOKEN_PARAM_NAME)).toIterable.flatten
        // query params mask parameters from post body with HttpServletRequest
        .filterNot( previouslyDiscovered.contains(_) ).toList
      )
  }

  val POSTBODY_REFRESH = new EnumVal { def name = "postbody_refresh"
    def isAccess = false
    def extract(request: HttpServletRequest, previouslyDiscovered: List[String]) = (this,
      Option(request.getParameterValues(OAUTH2_REFRESH_TOKEN_PARAM_NAME)).toIterable.flatten
        // query params mask parameters from post body with HttpServletRequest
        .filterNot( previouslyDiscovered.contains(_) ).toList
      )
  }

  val AUTHHEADER = new EnumVal { def name = "authheader"
    def isAccess = true
    def extract(request: HttpServletRequest, previouslyDiscovered: List[String]) = (this,
      Option(request.getHeaders("authorization").map(_.toString)
        .collect {
          case bearer if bearer.startsWith(OAUTH2_TOKEN_TYPE_BEARER) && bearer.length > OAUTH2_TOKEN_TYPE_BEARER.length +1
            => bearer.substring(OAUTH2_TOKEN_TYPE_BEARER.length +1)
        }
      ).toIterable.flatten.toList
    )
  }
}
