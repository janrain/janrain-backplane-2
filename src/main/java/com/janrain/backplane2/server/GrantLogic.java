package com.janrain.backplane2.server;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.model.Message;
import com.janrain.backplane.dao.DaoException;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.backplane.server2.model.ChannelFields;
import com.janrain.backplane.server2.oauth2.model.Grant2;
import com.janrain.backplane.server2.oauth2.model.GrantFields;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.oauth2.TokenException;
import com.janrain.util.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import scala.collection.JavaConversions;

import java.util.*;

/**
 * Grant logic utility class.
 * 
 * @author Tom Raney
 */
public class GrantLogic {

   /**
     * Retrieve all active grants issued to the supplied clientId
     * that match any of the fields that require authorization in the supplied scope.
     *
     * If none of the fields in the supplied scope require authorization, or if the supplied scope is null,
     * returns all grants issued to clientId, keyed on a single map entry representing the full scope
     * that includes the filter-only, no-authorization-required supplied scope.
     *
     * @return  map of authorized scopes backed-by grants
     * @throws
     */
    public static @NotNull Map<Scope,Set<Grant2>>
    retrieveClientGrants(final String clientId, @Nullable Scope scope) throws BackplaneServerException, TokenException, DaoException {

        List<Grant2> clientActiveGrants = JavaConversions.seqAsJavaList(BP2DAOs.grantDao().getByClientId(clientId));

        Map<Scope,Set<Grant2>> result = new LinkedHashMap<Scope, Set<Grant2>>();

        if (scope == null || ! scope.isAuthorizationRequired()) {
            Set<Grant2> selectedGrants = new LinkedHashSet<Grant2>();
            Map<Backplane2MessageFields.EnumVal,LinkedHashSet<String>> authorizedScopesMap = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();
            for (Grant2 grant : clientActiveGrants) {
                if (Message.isExpired(grant.get(GrantFields.TIME_EXPIRE()))) continue;
                selectedGrants.add(grant);
                Scope.addScopes(authorizedScopesMap, grant.getAuthorizedScope().getScopeMap());
            }
            if (scope != null) {
                Scope.addScopes(authorizedScopesMap, scope.getScopeMap()); // add request filter-only scopes
            }
            result.put(new Scope(authorizedScopesMap), selectedGrants);
        } else {
            for(Scope authReqScope : scope.getAuthReqScopes()) {
                final Scope testScope;
                if (authReqScope.getScopeMap().containsKey(Backplane2MessageFields.CHANNEL())) {
                    com.janrain.backplane.server2.model.Channel channel = Utils.getOrNull(BP2DAOs.channelDao().get(
                        authReqScope.getScopeMap().get(Backplane2MessageFields.CHANNEL()).iterator().next()
                    ));
                    String boundBus = channel == null ? null : Utils.getOrNull(channel.get(ChannelFields.BUS()));
                    testScope = new Scope(Backplane2MessageFields.BUS(), boundBus);
                } else {
                    testScope = authReqScope;
                }
                for(Grant2 grant : clientActiveGrants) {
                    if (Message.isExpired(grant.get(GrantFields.TIME_EXPIRE()))) continue;
                    if (grant.getAuthorizedScope().containsScope(testScope)) {
                        Set<Grant2> backingGrants = result.get(authReqScope);
                        if (backingGrants == null) {
                            backingGrants = new LinkedHashSet<Grant2>();
                            result.put(authReqScope, backingGrants);
                        }
                        backingGrants.add(grant);
                    }
                }
            }
        }

        return result;

    }

    /**
     * Activate authorization_code grant, mark code used, return updated grant.
     *
     * @return the updated grant, never null
     *
     * @throws com.janrain.oauth2.TokenException if:
     *          1) no grant was found for the supplied code, or
     *          2) the grant found was not an authorization_code grant, or
     *          3) the grant was not issued  to the supplied authenticatedClientId, or
     *          4) the grant/grant code has expired, or
     *          5) the grant's code had already been used (in this case the grant is also invalidated/revoked), or
     *
     * @throws
     */
      public static Grant2 getAndActivateCodeGrant(final String codeId, String authenticatedClientId) throws BackplaneServerException, TokenException, DaoException {

          Grant2 existing = Utils.getOrNull(BP2DAOs.grantDao().get(codeId));

          GrantState updatedState = GrantState.ACTIVE;
          if ( existing == null) {
              logger.warn("No grant found for code: " + codeId);
              throw new TokenException("Invalid code: " + codeId);
          } else if ( GrantType.AUTHORIZATION_CODE != existing.getType() || Message.isExpired(existing.get(GrantFields.TIME_EXPIRE())) ||
                  StringUtils.isBlank(authenticatedClientId) ||
                  ! authenticatedClientId.equals(Utils.getOrNull(existing.get(GrantFields.ISSUED_TO_CLIENT()))) ||
                  GrantState.INACTIVE != existing.getState()) {
              logger.error("Invalid grant for code: " + codeId);
              updatedState = GrantState.REVOKED;
          }

          Grant2 updated; // no expiration
          try {
              updated = new GrantBuilder(existing, updatedState).buildGrant();
          } catch (SimpleDBException e) {
              throw new BackplaneServerException(e.getMessage());
          }

          BP2DAOs.grantDao().update(updated);

          logger.info( "Grant status: " + updatedState.toString().toLowerCase() + " for code: " + codeId + ", client_id: " + authenticatedClientId);

          if (updatedState.isActive()) {
              return updated;
          } else {
              throw new TokenException("Invalid code: " + codeId);
          }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(GrantLogic.class);

    private GrantLogic() {}
}
