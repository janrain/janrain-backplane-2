package com.janrain.oauth2;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DaoException;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.backplane.server2.model.BusConfig2;
import com.janrain.backplane.server2.model.Channel;
import com.janrain.backplane.server2.model.ChannelFields;
import com.janrain.backplane.server2.oauth2.model.Token;
import com.janrain.backplane2.server.GrantType;
import com.janrain.backplane2.server.Scope;
import com.janrain.backplane2.server.TokenBuilder;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.util.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import scala.Option;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class AnonymousTokenRequest implements TokenRequest {

    // - PUBLIC

    public AnonymousTokenRequest( String callback, String bus, String scope, HttpServletRequest request) throws TokenException, DaoException {

        Option<Token> token = Token.fromRequest(request);

        this.grantType = token.isDefined() ? GrantType.REFRESH_ANONYMOUS : GrantType.ANONYMOUS;

        if (StringUtils.isBlank(callback)) {
            throw new TokenException("Callback cannot be blank");
        }

        if (!callback.matches("[\\._a-zA-Z0-9]*")) {
            throw new TokenException("callback parameter value is malformed");
        }

        this.requestScope = new Scope(scope);
        if ( this.requestScope.isAuthorizationRequired() ||
             ( this.requestScope.getScopeFieldValues(Backplane2MessageFields.CHANNEL()) != null &&
               ! this.requestScope.getScopeFieldValues(Backplane2MessageFields.CHANNEL()).isEmpty())) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "Buses and channels not allowed in the scope of anonymous token requests");
        }

        if (token.isDefined()) {
            this.refreshToken = token.get();
            if ( ! this.refreshToken.grantType().isRefresh()) {
                logger.warn("access token presented where refresh token is expected: " + refreshToken);
                throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_REQUEST, "invalid token: " + refreshToken);
            }
            if (! this.refreshToken.scope().containsScope(this.requestScope)) {
                throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid scope for refresh token: " + refreshToken + " : " + scope);
            }
        }

        if ( (!token.isDefined()) ^ StringUtils.isNotEmpty(bus)) {
            throw new TokenException("bus parameter is required if and only if refresh_token is not present");
        }

        try {
            if (StringUtils.isNotEmpty(bus)) {
                this.busConfig = Utils.getOrNull(BP2DAOs.busDao().get(bus));
                if ( this.busConfig == null) {
                    throw new TokenException("Invalid bus: " + bus);
                }
            } else if (refreshToken != null) {
                final Set<String> channels = refreshToken.scope().getScopeFieldValues(Backplane2MessageFields.CHANNEL());
                final Set<String> buses = refreshToken.scope().getScopeFieldValues(Backplane2MessageFields.BUS());
                if ( channels == null || channels.isEmpty() || channels.size() > 1 ||
                        buses == null || buses.isEmpty() || buses.size() > 1 ) {
                    throw new TokenException("invalid anonymous refresh token: " + refreshToken.id());
                } else {
                    busConfig = Utils.getOrNull(BP2DAOs.busDao().get(buses.iterator().next()));
                    channelExistingId = channels.iterator().next();
                    channelExistingExpireSeconds = BP2DAOs.channelDao().getExpire(channelExistingId);
                }
            }
        } catch (Exception e) {
            logger.error("error processing anonymous token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        // todo: check this properly, perhaps in controller?
        // throw new TokenException("Must not include client_secret for anonymous token requests");
    }

    @Override
    public Map<String,Object> tokenResponse() throws TokenException {
        logger.info("Responding to anonymous token request...");
        final Token accessToken;
        try {
            int accessTokenExpireSeconds = BP2DAOs.tokenDao().expireSeconds();
            Channel channel = createOrRefreshChannel(accessTokenExpireSeconds);
            Scope processedScope = processScope(channel.id(), Utils.getOrNull(channel.get(ChannelFields.BUS())));
            Date expires = new Date(System.currentTimeMillis() + accessTokenExpireSeconds * 1000);
            accessToken = new TokenBuilder(grantType.getAccessType(), processedScope.toString()).expires(expires).buildToken();
            BP2DAOs.tokenDao().store(accessToken);
            String refreshToken = generateRefreshToken(grantType.getRefreshType(), processedScope, accessTokenExpireSeconds);
            return accessToken.response(refreshToken);
        } catch (Exception e) {
            logger.error("error processing anonymous access token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            logger.info("exiting anonymous token request");
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

    private static final Logger logger = Logger.getLogger(AnonymousTokenRequest.class);


    private final GrantType grantType;
    private final Scope requestScope;
    private Token refreshToken;
    private BusConfig2 busConfig;
    private String channelExistingId = null;
    private int channelExistingExpireSeconds = 0;

    private String generateRefreshToken(GrantType refreshType, Scope scope, int tokenExpireSeconds) throws BackplaneServerException, DaoException {
        if (refreshType == null || ! refreshType.isRefresh()) return null;
        // channelExistingExpireSeconds updated on sticky message post
        int refreshExpireSeconds = Math.max(channelExistingExpireSeconds, tokenExpireSeconds + busConfig.retentionTimeSeconds());
        Token refreshToken = new TokenBuilder(refreshType, scope.toString())
                .expires(new Date(System.currentTimeMillis() + refreshExpireSeconds * 1000)).buildToken();
        BP2DAOs.tokenDao().store(refreshToken);
        return refreshToken.id();
    }

    private Channel createOrRefreshChannel(int tokenExpireSeconds) throws TokenException, SimpleDBException, BackplaneServerException, DaoException {
        // channelExistingExpireSeconds updated on sticky message post
        int channelExpireSeconds = Math.max(channelExistingExpireSeconds, tokenExpireSeconds + busConfig.retentionTimeSeconds());
        Channel channel = new Channel(channelExistingId, busConfig, channelExpireSeconds);
        BP2DAOs.channelDao().store(channel, channelExpireSeconds);
        return channel;
    }

    private Scope processScope(final String channel, final String bus) {
        Map<Backplane2MessageFields.EnumVal,LinkedHashSet<String>> scopeMap = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();
        scopeMap.putAll(requestScope.getScopeMap());
        scopeMap.put(Backplane2MessageFields.BUS(), new LinkedHashSet<String>() {{ add(bus);}});
        scopeMap.put(Backplane2MessageFields.CHANNEL(), new LinkedHashSet<String>() {{ add(channel);}});
        return new Scope(scopeMap);
    }
}
