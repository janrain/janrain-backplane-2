package com.janrain.backplane.server2.oauth2;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.server2.*;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.commons.message.MessageException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class AnonymousTokenRequest implements TokenRequest {

    // - PUBLIC

    public AnonymousTokenRequest( String callback, String bus, String scope, String refreshToken,
                                  HttpServletRequest request, String authHeader) throws TokenException {

        this.grantType = StringUtils.isEmpty(refreshToken) ? GrantType.ANONYMOUS : GrantType.REFRESH_ANONYMOUS;

        if (StringUtils.isBlank(callback)) {
            throw new TokenException("Callback cannot be blank");
        }

        if (!callback.matches("[\\._a-zA-Z0-9]*")) {
            throw new TokenException("callback parameter value is malformed");
        }

        if ( StringUtils.isEmpty(refreshToken) ^ StringUtils.isNotEmpty(bus)) {
            throw new TokenException("bus parameter is required if and only if refresh_token is not present");
        }

        try {
            if (StringUtils.isNotEmpty(bus)) {
                this.busConfig = BP2DAOs.getBusDao().get(bus);
                if ( this.busConfig == null) {
                    throw new TokenException("Invalid bus: " + bus);
                }
            }
        } catch (BackplaneServerException e) {
            logger.error("error processing anonymous token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        this.requestScope = new Scope(scope);
        if ( this.requestScope.isAuthorizationRequired() ||
             ( this.requestScope.getScopeFieldValues(BackplaneMessage.Field.CHANNEL) != null &&
               ! this.requestScope.getScopeFieldValues(BackplaneMessage.Field.CHANNEL).isEmpty())) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "Buses and channels not allowed in the scope of anonymous token requests");
        }

        if (StringUtils.isNotEmpty(refreshToken)) {
            this.refreshToken = Token.fromRequest(request, refreshToken, authHeader);
            if (! this.refreshToken.getScope().containsScope(this.requestScope)) {
                throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid scope for refresh token: " + refreshToken + " : " + scope);
            }
        }

        // todo: check this properly, perhaps in controller?
        // throw new TokenException("Must not include client_secret for anonymous token requests");
    }

    @Override
    public Map<String,Object> tokenResponse() throws TokenException {
        logger.info("Responding to anonymous token request...");
        final Token accessToken;
        final Integer expiresIn = grantType.getAccessType().getTokenExpiresSecondsDefault();
        Date expires = new Date(System.currentTimeMillis() + expiresIn.longValue() * 1000);
        try {
            Channel channel = createOrRefreshChannel(10 * expiresIn);
            Scope processedScope = processScope(channel.getIdValue(), channel.get(Channel.ChannelField.BUS));
            accessToken = new Token.Builder(grantType.getAccessType(), processedScope.toString()).expires(expires).buildToken();
            BP2DAOs.getTokenDao().persist(accessToken);
            return accessToken.response(generateRefreshToken(grantType.getRefreshType(), processedScope));
        } catch (Exception e) {
            logger.error("error processing anonymous access token request: " + e.getMessage(), e);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_SERVER_ERROR, "error processing anonymous token request", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            logger.info("exiting anonymous token request");
            try {
                if (this.refreshToken != null) {
                    BP2DAOs.getTokenDao().delete(this.refreshToken.getIdValue());
                }
            } catch (BackplaneServerException e) {
                logger.error("error deleting used refresh token: " + refreshToken.getIdValue(), e);
            }
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(AnonymousTokenRequest.class);


    private final GrantType grantType;
    private final Scope requestScope;
    private Token refreshToken;
    private BusConfig2 busConfig;

    private static String generateRefreshToken(GrantType refreshType, Scope scope) throws MessageException, BackplaneServerException {
        if (refreshType == null || ! refreshType.isRefresh()) return null;
        Token refreshToken = new Token.Builder(refreshType, scope.toString()).buildToken();
        BP2DAOs.getTokenDao().persist(refreshToken);
        return refreshToken.getIdValue();
    }

    private Channel createOrRefreshChannel(int expireSeconds) throws TokenException, MessageException, BackplaneServerException {
        String channelId = null;
        BusConfig2 config;
        if (refreshToken != null ) {
            final Set<String> channels = refreshToken.getScope().getScopeFieldValues(BackplaneMessage.Field.CHANNEL);
            final Set<String> buses = refreshToken.getScope().getScopeFieldValues(BackplaneMessage.Field.BUS);
            if ( channels == null || channels.isEmpty() || channels.size() > 1 ||
                    buses == null || buses.isEmpty() || buses.size() > 1 ) {
                throw new TokenException("invalid anonymous refresh token: " + refreshToken.getIdValue());
            } else {
                config = BP2DAOs.getBusDao().get(buses.iterator().next());
                channelId = channels.iterator().next();
            }
        } else {
            config = busConfig;
        }
        Channel channel = new Channel(channelId, config, expireSeconds);
        BP2DAOs.getChannelDao().persist(channel);
        return channel;
    }

    private Scope processScope(final String channel, final String bus) {
        Map<BackplaneMessage.Field,LinkedHashSet<String>> scopeMap = new LinkedHashMap<BackplaneMessage.Field, LinkedHashSet<String>>();
        scopeMap.putAll(requestScope.getScopeMap());
        scopeMap.put(BackplaneMessage.Field.BUS, new LinkedHashSet<String>() {{ add(bus);}});
        scopeMap.put(BackplaneMessage.Field.CHANNEL, new LinkedHashSet<String>() {{ add(channel);}});
        return new Scope(scopeMap);
    }
}
