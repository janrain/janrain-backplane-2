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

package com.janrain.servlet;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author Tom Raney
 */
public class JsonpCallbackFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("JsonpCallbackFilter initialized.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Map<String, String[]> parameters = httpRequest.getParameterMap();
        String callbackName = "";
        String[] values = parameters.get("callback");
        if (values != null) {
            callbackName = values[0];
        }

        if (StringUtils.isNotBlank(callbackName)) {
            // wrap the json in the callback
            OutputStream stream = httpResponse.getOutputStream();
            GenericResponseWrapper wrapper = new GenericResponseWrapper(httpResponse);

            chain.doFilter(request, wrapper);

            stream.write(("/* */" + callbackName + "(").getBytes());
            stream.write(wrapper.getData());
            stream.write(");".getBytes());

            wrapper.setContentType("text/javascript;charset=UTF-8");
            // always return 200 when using the callback to allow the message to
            // reach the browser based function
            wrapper.setStatus(HttpServletResponse.SC_OK);
            stream.close();


        } else {
            // pass the request/response on
            chain.doFilter(request, response);
        }

    }

    @Override
    public void destroy() {}

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(JsonpCallbackFilter.class);

}
