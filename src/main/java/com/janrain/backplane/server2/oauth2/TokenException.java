package com.janrain.backplane.server2.oauth2;

import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletResponse;

/**
 * OAuth2 /token processing errors.
 * If not supplied on constructor, defaults to:
 * - 400 bad request HTTP response status code
 * - error=invalid_request OAuth2 token error code
 *
 * @author Johnny Bufu
 */
public class TokenException extends OAuth2Exception {

    public TokenException(String message) {
        this(OAuth2.OAUTH2_TOKEN_INVALID_REQUEST, message, HttpServletResponse.SC_BAD_REQUEST, null);
    }

    public TokenException(String message, int httpResponseCode) {
        this(OAuth2.OAUTH2_TOKEN_INVALID_REQUEST, message, httpResponseCode, null);
    }

    public TokenException(String oauthErrorCode, String message) {
        this(oauthErrorCode, message, HttpServletResponse.SC_BAD_REQUEST, null);
    }

    public TokenException(String oauthErrorCode, String message, Throwable cause) {
        this(oauthErrorCode, message, HttpServletResponse.SC_BAD_REQUEST, cause);
    }

    public TokenException(String oauthErrorCode, Throwable cause) {
        this(oauthErrorCode, null, HttpServletResponse.SC_BAD_REQUEST, cause);
    }

    public TokenException(String oauthErrorCode, String message, int httpResponseCode) {
        this(oauthErrorCode, message, httpResponseCode, null);
    }

    public TokenException(String oauthErrorCode, @Nullable String message, int httpResponseCode, @Nullable Throwable cause) {
        super(oauthErrorCode, message, cause);
        this.httpResponseCode = httpResponseCode;
    }

    public int getHttpResponseCode() {
        return httpResponseCode;
    }

    // - PROTECTED
    
    protected final int httpResponseCode;

}
