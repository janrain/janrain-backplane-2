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

package com.janrain.backplane.server2.oauth2;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Class for building an OAuth2 URL response with parameters sent out in either query or fragment
 *
 * @author Johnny Bufu
 */
public enum UrlResponseFormat {

    QUERY {
        @Override
        public String encode(String redirectUrl, Map<String, String> params) throws ValidationException {
            OAuth2.validateRedirectUri(redirectUrl); // no fragment
            StringBuilder result = new StringBuilder(redirectUrl);
            stitchParams(result, params, redirectUrl.contains("?") ? "&" : "?");
            return result.toString();
        }},

    FRAGMENT {
        @Override
        public String encode(String redirectUrl, Map<String, String> params) throws ValidationException {
            OAuth2.validateRedirectUri(redirectUrl); // no fragment
            StringBuilder result = new StringBuilder(redirectUrl);
            stitchParams(result, params, "#");
            return result.toString();
        }};

    public abstract String encode(String redirectUri, Map<String,String> params) throws ValidationException;

    // - PRIVATE

    private static final Charset UTF8 = Charset.forName("UTF-8");


    private static void stitchParams(StringBuilder result, Map<String,String> params, String firstChar) {
        boolean firstParam = true;
        for(Map.Entry<String,String> param : params.entrySet()) {
            result.append( firstParam ? firstChar : "&");
            firstParam = false;
            try {
                result.append(URLEncoder.encode(param.getKey(), UTF8.name()));
                result.append("=");
                result.append(URLEncoder.encode(param.getValue(), UTF8.name()));
            } catch (UnsupportedEncodingException e) {
                // do nothing, Utf8StringUtils.UTF8 would have crashed at deploy-time
            }
        }
    }
}
