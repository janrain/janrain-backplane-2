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

import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Johnny Bufu
 */
public class AuthorizationException extends OAuth2Exception {

    public AuthorizationException(String oauthErrorCode, String message, HttpServletRequest request) {
        this(oauthErrorCode, message, request, null);
    }

    public AuthorizationException(String oauthErrorCode, String message, HttpServletRequest request, @Nullable Throwable cause) {
        super(oauthErrorCode, message, cause);
        this.redirectUri = request.getParameter(AuthorizationRequest.Field.REDIRECT_URI.getFieldName().toLowerCase());
        this.state = request.getParameter(AuthorizationRequest.Field.STATE.getFieldName().toLowerCase());
    }

    public AuthorizationException(String oauthErrorCode, String message, AuthorizationRequest request) {
        this(oauthErrorCode, message, request, null);
    }

    public AuthorizationException(String oauthErrorCode, String message, AuthorizationRequest request, @Nullable Throwable cause) {
        super(oauthErrorCode, message, cause);
        this.redirectUri = request.get(AuthorizationRequest.Field.REDIRECT_URI);
        this.state = request.get(AuthorizationRequest.Field.STATE);
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getState() {
        return state;
    }

    private final String redirectUri;
    private final String state;
}
