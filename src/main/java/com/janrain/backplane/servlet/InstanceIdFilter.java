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

package com.janrain.backplane.servlet;


import com.janrain.backplane.config.BackplaneConfig;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Johnny Bufu
 */
public class InstanceIdFilter implements Filter {

    // - PUBLIC

    @Override
    public void init(FilterConfig filterConfig) {
        this.fc = filterConfig;
        logger.info("InstanceIdFilter initialized.");
    }

    @Override
    public void destroy() {
        this.fc = null;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.addHeader(SSO_ID_HEADER, bpConfig.getInstanceId() + "-" + bpConfig.getBuildVersion());

        //add EC2 instance id
        response.addHeader("EC2-instance-id", BackplaneConfig.getEC2InstanceId());

        // pass the request/response on
        chain.doFilter(req, response);
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(InstanceIdFilter.class);

    private static final String SSO_ID_HEADER = "X-BP-Instance";

    @Inject
    private BackplaneConfig bpConfig;

    private FilterConfig fc;

}
