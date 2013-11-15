package com.janrain.oauth2;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DaoException;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.oauth2.model.*;
import com.janrain.backplane2.server.GrantLogic;
import com.janrain.backplane2.server.GrantType;
import com.janrain.backplane2.server.Scope;
import com.janrain.backplane2.server.TokenBuilder;
import com.janrain.commons.util.Pair;
import com.janrain.util.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import scala.Option;
import scala.collection.JavaConversions;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static com.janrain.oauth2.OAuth2.OAUTH2_TOKEN_INVALID_CLIENT;
import static com.janrain.oauth2.OAuth2.OAUTH2_TOKEN_UNSUPPORTED_GRANT;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * @author Johnny Bufu
 */
public class AuthenticatedTokenRequest implements TokenRequest {

    // - PUBLIC

    public AuthenticatedTokenRequest(String grantType, Client authenticatedClient, String code,
                                     String redirectUri, String scope,
                                     HttpServletRequest request) throws TokenException, DaoException {

        this.authenticatedClientId = authenticatedClient.id();
        this.authenticatedClientSourceUrl = Utils.getOrNull(authenticatedClient.get(ClientFields.SOURCE_URL()));
        try {
            // this must be done as early as possible in order to invalidate misused codes/grants, before any other failures
            if (StringUtils.isNotEmpty(code)) {
                // throw whenever a code is parameter is supplied, regardless of the grant type declared in the request
                this.codeGrant = GrantLogic.getAndActivateCodeGrant(code, this.authenticatedClientId);
            }
        } catch (BackplaneServerException e) {
            logger.error("error processing token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        if (StringUtils.isEmpty(authenticatedClientId)) {
            throw new TokenException(OAUTH2_TOKEN_INVALID_CLIENT, "Client authentication failed", SC_UNAUTHORIZED);
        }

        this.grantType = GrantType.fromOauthType(grantType);
        if (this.grantType == null) {
            throw new TokenException(OAUTH2_TOKEN_UNSUPPORTED_GRANT, "Invalid grant type: " + grantType);
        }

        if ( this.grantType == GrantType.AUTHORIZATION_CODE ^ StringUtils.isNotEmpty(code) ) {
            throw new TokenException("code is required if and only if grant_type is authorization_code");
        }

        if ( this.grantType == GrantType.AUTHORIZATION_CODE ^ codeGrant != null ) {
            throw new TokenException("Invalid code: " + code);
        }

        Option<Token> token = Token.fromRequest(request);

        if ( this.grantType == GrantType.REFRESH_PRIVILEGED ^ (token.isDefined()) ) {
            throw new TokenException("refresh_token is required if and only if grant_type is refresh_token");
        }

        if ( this.grantType == GrantType.AUTHORIZATION_CODE ^ StringUtils.isNotEmpty(redirectUri) ) {
            throw new TokenException("redirect_uri is required if and only if grant_type is authorization_code");
        }
        try {
            OAuth2.validateRedirectUri(redirectUri, Utils.getOrNull(authenticatedClient.get(ClientFields.REDIRECT_URI())));
        } catch (ValidationException e) {
            throw new TokenException(e.getCode(), "Invalid redirect_uri: " + redirectUri);
        }



        if (token.isDefined()) {
            this.refreshToken = token.get();
            if ( ! this.refreshToken.grantType().isRefresh()) {
                logger.warn("access token presented where refresh token is expected: " + refreshToken);
                throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_REQUEST, "invalid token: " + refreshToken);
            }
        }

        this.requestScope = new Scope(scope);
    }

    @Override
    public Map<String,Object> tokenResponse() throws TokenException, DaoException {
        logger.info("Responding to authenticated token request...");
        final Token accessToken;
        final Integer expiresIn = 31536000;  // 1 year
        Date expires = new Date(System.currentTimeMillis() + expiresIn.longValue() * 1000);
        Pair<Scope,List<String>> scopeGrants = processScope();
        try {
            accessToken = new TokenBuilder(grantType.getAccessType(), scopeGrants.getLeft().toString())
                    .expires(expires)
                    .issuedToClient(authenticatedClientId)
                    .clientSourceUrl(authenticatedClientSourceUrl)
                    .grants(scopeGrants.getRight())
                    .buildToken();
            BP2DAOs.tokenDao().store(accessToken);
            return accessToken.response(generateRefreshToken(grantType.getRefreshType(), accessToken));
        } catch (DaoException e) {
            logger.error("error processing anonymous access token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (BackplaneServerException bpe) {
            logger.error("error processing anonymous access token request: " + bpe.getMessage(), bpe);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        } finally {
            try {
                if (this.refreshToken != null) {
                    BP2DAOs.tokenDao().delete(this.refreshToken.id());
                }
            } catch (DaoException e) {
                logger.error("error deleting used refresh token: " + refreshToken.id(), e);
            }
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(AuthenticatedTokenRequest.class);

    private final GrantType grantType;
    private final String authenticatedClientId;
    private final String authenticatedClientSourceUrl;
    private final Scope requestScope;

    private Grant2 codeGrant;
    private Token refreshToken;

    private Pair<Scope,List<String>> processScope() throws TokenException, DaoException {

        if (refreshToken != null) {
            return new Pair<Scope, List<String>>(Scope.checkCombine(refreshToken.scope(), requestScope), JavaConversions.seqAsJavaList(refreshToken.backingGrants()));
        }

        if (codeGrant != null) {
            return new Pair<Scope, List<String>>(Scope.checkCombine(codeGrant.getAuthorizedScope(), requestScope), new ArrayList<String>() {{add(codeGrant.id());}});
        }

        // client credentials scope
        try {
            Map<Scope,Set<Grant2>> scopeGrantsMap = GrantLogic.retrieveClientGrants(authenticatedClientId, requestScope);
            List<String> allGrants = new ArrayList<String>();
            for(Set<Grant2> grants : scopeGrantsMap.values()) {
                for(Grant2 grant : grants) {
                    allGrants.add(grant.id());
                }
            }

            if (requestScope.isAuthorizationRequired()) {
                if (!scopeGrantsMap.keySet().containsAll(requestScope.getAuthReqScopes())) {
                    throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "unauthorized scope: " + requestScope);
                }
                return new Pair<Scope, List<String>>(requestScope, allGrants);
            } else {
                return new Pair<Scope, List<String>>(Scope.checkCombine(scopeGrantsMap.keySet().iterator().next(), requestScope), allGrants);
            }
        } catch (BackplaneServerException e) {
            logger.error("error processing token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String generateRefreshToken(GrantType refreshType, Token accessToken) throws BackplaneServerException, DaoException {
        if (! refreshType.isRefresh()) return null;
        Token refreshToken = new TokenBuilder(refreshType, accessToken.get(TokenFields.SCOPE()).get())
                .issuedToClient(authenticatedClientId)
                .clientSourceUrl(authenticatedClientSourceUrl)
                .grants(JavaConversions.seqAsJavaList(accessToken.backingGrants()))
                .buildToken();
        BP2DAOs.tokenDao().store(refreshToken);
        return refreshToken.id();
    }
}
