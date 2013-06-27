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

package com.janrain.utils;

import com.janrain.commons.util.InitSystemProps;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.naming.InitialContext;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Tom Raney
 */
public class BackplaneSystemProps extends InitSystemProps {

    public static final String GRAPHITE_SERVER    = "GRAPHITE_SERVER";
    public static final String ZOOKEEPER_SERVERS  = "ZOOKEEPER_SERVERS";
    public static final String REDIS_SERVER_PRIMARY = "REDIS_SERVER_PRIMARY";
    public static final String REDIS_SERVER_READS = "REDIS_SERVER_READS";
    public static final String IP_WHITE_LIST = "IP_WHITE_LIST";
    public static final String ANALYTICS_FALLBACK_PATH = "ANALYTICS_FALLBACK_PATH";
    public static final String ANALYTICS_FALLBACK_MAXSIZEMB = "ANALYTICS_FALLBACK_MAXSIZEMB";
    public static final String ANALYTICS_LOGGING = "ANALYTICS_LOGGING";
    public static final String ADMIN_USER = "bpadmin";
    public static final String BPSERVER_CONFIG_KEY = "bpserverconfig";

    public static String getMachineName() {
        try {
            return "backplane/" + InetAddress.getLocalHost().getHostName();
        }  catch (UnknownHostException e) {
            logger.warn("get localhost call failed");
        }
        return "n/a";
    }

    public BackplaneSystemProps(String log4jFile) {
        super(log4jFile);

        load(GRAPHITE_SERVER, false);
        load(ZOOKEEPER_SERVERS, true);
        load(REDIS_SERVER_PRIMARY, true);
        load(REDIS_SERVER_READS, true);
        load(IP_WHITE_LIST, false);
        load(ANALYTICS_LOGGING, false);
        load(ANALYTICS_FALLBACK_PATH, false);
        load(ANALYTICS_FALLBACK_MAXSIZEMB, false);

    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneSystemProps.class);

    private void load(String paramName, boolean required) {
        String result = System.getProperty(paramName);
        if (StringUtils.isBlank(result)) {
            try {
                javax.naming.Context initCtx = new InitialContext();
                result = (String) initCtx.lookup("java:comp/env/" + paramName);
                System.setProperty(paramName, result);
                System.out.println("Parameter " + paramName + " fetched from context and inserted as system property");
            } catch (Exception e) {
                //continue
                if (required) {
                    System.out.println("An error occurred trying to locate required parameter " + paramName + " => " + e.getMessage());
                }
            }
        } else {
            System.out.println("Parameter " + paramName + " exists as a system property");
        }
    }


}
