package com.janrain.backplane.server2;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.oauth2.TokenException;
import com.janrain.commons.message.MessageException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public static @NotNull Map<Scope,Set<Grant>>
    retrieveClientGrants(final String clientId, @Nullable Scope scope) throws BackplaneServerException, TokenException {

        List<Grant> clientActiveGrants = BP2DAOs.getGrantDao().getByClientId(clientId);

        Map<Scope,Set<Grant>> result = new LinkedHashMap<Scope, Set<Grant>>();

        if (scope == null || ! scope.isAuthorizationRequired()) {
            Set<Grant> selectedGrants = new LinkedHashSet<Grant>();
            Map<BackplaneMessage.Field,LinkedHashSet<String>> authorizedScopesMap = new LinkedHashMap<BackplaneMessage.Field, LinkedHashSet<String>>();
            for (Grant grant : clientActiveGrants) {
                if (grant.isExpired()) continue;
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
                if (authReqScope.getScopeMap().containsKey(BackplaneMessage.Field.CHANNEL)) {
                    Channel channel = BP2DAOs.getChannelDao().get(authReqScope.getScopeMap().get(BackplaneMessage.Field.CHANNEL).iterator().next());
                    String boundBus = channel == null ? null : channel.get(Channel.ChannelField.BUS);
                    testScope = new Scope(BackplaneMessage.Field.BUS, boundBus);
                } else {
                    testScope = authReqScope;
                }
                for(Grant grant : clientActiveGrants) {
                    if (grant.isExpired()) continue;
                    if (grant.getAuthorizedScope().containsScope(testScope)) {
                        Set<Grant> backingGrants = result.get(authReqScope);
                        if (backingGrants == null) {
                            backingGrants = new LinkedHashSet<Grant>();
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
     * @throws com.janrain.backplane.server2.oauth2.TokenException if:
     *          1) no grant was found for the supplied code, or
     *          2) the grant found was not an authorization_code grant, or
     *          3) the grant was not issued  to the supplied authenticatedClientId, or
     *          4) the grant/grant code has expired, or
     *          5) the grant's code had already been used (in this case the grant is also invalidated/revoked), or
     *
     * @throws
     */
      public static Grant getAndActivateCodeGrant(final String codeId, String authenticatedClientId) throws BackplaneServerException, TokenException {

          Grant existing = BP2DAOs.getGrantDao().get(codeId);

          GrantState updatedState = GrantState.ACTIVE;
          if ( existing == null) {
              logger.warn("No grant found for code: " + codeId);
              throw new TokenException("Invalid code: " + codeId);
          } else if ( GrantType.AUTHORIZATION_CODE != existing.getType() || existing.isExpired() ||
                  StringUtils.isBlank(authenticatedClientId) ||
                  ! authenticatedClientId.equals(existing.get(Grant.GrantField.ISSUED_TO_CLIENT_ID)) ||
                  GrantState.INACTIVE != existing.getState()) {
              logger.error("Invalid grant for code: " + codeId);
              updatedState = GrantState.REVOKED;
          }

          Grant updated; // no expiration
          try {
              updated = new Grant.Builder(existing, updatedState).buildGrant();
          } catch (MessageException e) {
              throw new BackplaneServerException(e.getMessage());
          }

          BP2DAOs.getGrantDao().update(existing, updated);

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
