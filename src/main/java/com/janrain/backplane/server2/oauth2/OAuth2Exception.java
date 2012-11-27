package com.janrain.backplane.server2.oauth2;

/**
 * @author Johnny Bufu
 */
public class OAuth2Exception extends Exception {

    public OAuth2Exception(String oauthErrorCode, String message) {
        super(message);
        this.oauthErrorCode = oauthErrorCode;
    }

    public OAuth2Exception(String oauthErrorCode, String message, Throwable cause) {
        super(message, cause);
        this.oauthErrorCode = oauthErrorCode;
    }

    public OAuth2Exception(String oauthErrorCode, Throwable cause) {
        super(cause);
        this.oauthErrorCode = oauthErrorCode;
    }

    public String getOauthErrorCode() {
        return oauthErrorCode;
    }

    // - PRIVATE

    private final String oauthErrorCode;
}
