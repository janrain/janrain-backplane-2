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

package com.janrain.backplane2.server;

import com.janrain.backplane.common.MessageException;
import com.janrain.backplane.server2.model.Backplane2Message;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.commons.util.Pair;
import com.janrain.oauth2.OAuth2;
import com.janrain.oauth2.TokenException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import scala.Option;

import java.util.*;

/**
 * @author Tom Raney, Johnny Bufu
 */
public class Scope {

    // scope type associated with a backplane message field
    public enum ScopeType {
        NONE,       // message field cannot be used as a scope key
        FILTER,     // message field is used as a filter, allowed for anonymous token requests
        AUTHZ_REQ   // message field can only be used with privileged, authenticated and authorized token requests
    }

    /**
     * @param scopeString String representation of the scope as defined in the Backplane 2.0 spec
     */
    public Scope(String scopeString) throws TokenException {
        this.scopes = parseScopeString(scopeString);
    }

    public Scope(final Backplane2MessageFields.EnumVal scopeField, final String value) {
        this.scopes = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>() {{
            put(scopeField, new LinkedHashSet<String>() {{ add(value); }});
        }};
    }

    public Scope(Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> scopeMap) {
        this.scopes = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>(scopeMap);
    }

    /**
     * @return a copy of this scope's internal map of scope key-values
     */
    public Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> getScopeMap() {
        Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> mapCopy = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();
        mapCopy.putAll(scopes);
        return mapCopy;
    }

    public Set<String> getScopeFieldValues(Backplane2MessageFields.EnumVal field) {
        return scopes.get(field);
    }

    public boolean isAuthorizationRequired() {
        for(Backplane2MessageFields.EnumVal scopeKey : scopes.keySet()) {
            LinkedHashSet<String> values = scopes.get(scopeKey);
            if (scopeKey.scopeType() == ScopeType.AUTHZ_REQ && values != null && ! values.isEmpty()) return true;
        }
        return false;
    }

    /**
     * @return multiple, individual scopes for which authorization is required (one value per returned Scope)
     */
    public List<Scope> getAuthReqScopes() {
        List<Scope> authReqScopes = new ArrayList<Scope>();
        for(Backplane2MessageFields.EnumVal scopeKey : scopes.keySet()) {
            LinkedHashSet<String> values = scopes.get(scopeKey);
            if (scopeKey.scopeType() == ScopeType.AUTHZ_REQ && values != null && ! values.isEmpty()) {
                for(String value : values) {
                    authReqScopes.add(new Scope(scopeKey, value));
                }
            }
        }
        return authReqScopes;
    }


    public boolean isMessageInScope(@NotNull Backplane2Message message) {
        for(Backplane2MessageFields.EnumVal scopeField : scopes.keySet()) {
            LinkedHashSet<String> scopeValues = scopes.get(scopeField);
            if (scopeValues == null || ! scopeValues.contains((String)message.get(scopeField).getOrElse(null))) return false;
        }
        return true;
    }

    public boolean containsScope(Scope testScope) {
        for(Backplane2MessageFields.EnumVal scopeKey : testScope.scopes.keySet()) {
            if (scopeKey.scopeType() == ScopeType.AUTHZ_REQ ) {
                if (! scopes.containsKey(scopeKey) || ! scopes.get(scopeKey).containsAll(testScope.scopes.get(scopeKey))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return String representation of the scope as defined in the Backplane 2.0 spec
     */
    @Override
    public String toString() {
        StringBuilder scopeString = new StringBuilder();
        for (Map.Entry<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> entry : scopes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                logger.info("empty scope values for key: " + entry.getKey()); // shouldn't happen
                continue;
            }
            for(String scopeValue : entry.getValue()) {
                if (scopeString.length() > 0) scopeString.append(SEPARATOR);
                scopeString.append(entry.getKey().name()).append(DELIMITER).append(scopeValue);
            }
        }
        return scopeString.toString();
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) return true;
        if (otherObject == null || getClass() != otherObject.getClass()) return false;
        Scope scope = (Scope) otherObject;
        return !(scopes != null ? !scopes.equals(scope.scopes) : scope.scopes != null);
    }

    @Override
    public int hashCode() {
        return scopes != null ? scopes.hashCode() : 0;
    }

    /**
     * Add the scopes in the second map to the first.
     */
    public static void addScopes(Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> first, Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> second) {
        for (Backplane2MessageFields.EnumVal field : second.keySet()) {
            LinkedHashSet<String> values = first.get(field);
            if (values == null) {
                values = new LinkedHashSet<String>();
                first.put(field, values);
            }
            values.addAll(second.get(field));
        }
    }

    /**
     * Retrieve list of authorized buses
     *
     * @param scopesString a space-sparated String of buses
     * @return a valid list which may be empty
     */
    public static @NotNull List<String> getScopesAsList(String scopesString) {
        if (StringUtils.isEmpty(scopesString)) {
            return new ArrayList<String>();
        } else {
            return Arrays.asList(scopesString.split(SEPARATOR));
        }
    }

    /**
     * @param field scope field
     * @param scopeValues space separated scope values
     *
     * @return  an encoded space delimited string of scopes e.g.:  "bus:thisbus.com bus:andthatbus.com ..."
     */
    public static String getEncodedScopesAsString(Backplane2MessageFields.EnumVal field, String scopeValues) {
        return getEncodedScopesAsString(field, getScopesAsList(scopeValues));
    }

    public static String getEncodedScopesAsString(Backplane2MessageFields.EnumVal field, @NotNull List<String> scopeValues) {
        StringBuilder sb = new StringBuilder();
        for (String value: scopeValues) {
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(field.name()).append(DELIMITER).append(value);
        }
        return sb.toString();
    }

    // - LEGACY DAO SUPPORT to be removed

    /**
     * @param field scope field
     * @param scopeValues space separated scope values
     *
     * @return  an encoded space delimited string of scopes e.g.:  "bus:thisbus.com bus:andthatbus.com ..."
     */
    public static String getEncodedScopesAsString(BackplaneMessage.Field field, String scopeValues) {
        return getEncodedScopesAsString(field, getScopesAsList(scopeValues));
    }

    public static String getEncodedScopesAsString(BackplaneMessage.Field field, @NotNull List<String> scopeValues) {
        StringBuilder sb = new StringBuilder();
        for (String value: scopeValues) {
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(field.getFieldName()).append(DELIMITER).append(value);
        }
        return sb.toString();
    }

    public Set<String> getScopeFieldValues(BackplaneMessage.Field field) {
        return scopes.get(field);
    }

    // - LEGACY DAO SUPPORT to be removed

    /**
     * @return a new Scope consisting of all scope values present in the first one, less the auth-req scope values in 'revoke'
     */
    public static Scope revoke(@NotNull Scope scope, @NotNull Scope revoke) {
        Map<Backplane2MessageFields.EnumVal,LinkedHashSet<String>> newScope = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();

        for(Backplane2MessageFields.EnumVal scopeKey : scope.getScopeMap().keySet()) {
            Set<String> revokeValues = revoke.getScopeFieldValues(scopeKey);
            if(scopeKey.scopeType() != ScopeType.AUTHZ_REQ || revokeValues == null || revokeValues.isEmpty()) {
                newScope.put(scopeKey, scope.getScopeMap().get(scopeKey));
            } else {
                LinkedHashSet<String> newValues = new LinkedHashSet<String>();
                for(String scopeValue : scope.getScopeFieldValues(scopeKey)) {
                    if( ! revokeValues.contains(scopeValue)) {
                        newValues.add(scopeValue);
                    }
                }
                newScope.put(scopeKey, newValues);
            }
        }

        return new Scope(newScope);
    }

    /**
     * Combines the auth-req scope fields from the authorized scope with the filter-only scopes from the request scope.
     * If request scope contains auth-req scope fields, then request scope is returned
     *
     * @throws TokenException if the auth-req fields in request scope are not contained in the authorized scope
     */
    public static Scope checkCombine(@NotNull final Scope authorized, @Nullable final Scope request) throws TokenException {
        if ( ! authorized.isAuthorizationRequired() ) {
            throw new TokenException("invalid scope/grant: authorized scope has no auth-req fields: " + authorized);
        } else if (request == null) {
            return new Scope(authorized.getScopeMap());
        } else if ( ! authorized.containsScope(request) ) {
            throw new TokenException("unauthorized scope: " + request);
        } else if (request.isAuthorizationRequired()) {
            return new Scope(request.getScopeMap());
        } else { // combine
            Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> result = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();
            Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> authorizedMap = authorized.getScopeMap();
            Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> requestMap = request.getScopeMap();
            for (Backplane2MessageFields.EnumVal authorizedField : authorizedMap.keySet()) {
                if (authorizedField.scopeType() == ScopeType.AUTHZ_REQ) {
                    result.put(authorizedField, authorizedMap.get(authorizedField));
                }
            }
            for (Backplane2MessageFields.EnumVal filterField : requestMap.keySet()) {
                if (filterField.scopeType() == ScopeType.FILTER) {
                    result.put(filterField, requestMap.get(filterField));
                }
            }
            return new Scope(result);
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(Scope.class);

    private static final int MAX_PARAMETERS = 100;

    private static final String SEPARATOR = " ";
    private static final String DELIMITER = ":";

    private Map<Backplane2MessageFields.EnumVal,LinkedHashSet<String>> scopes;

    private static Map<Backplane2MessageFields.EnumVal, LinkedHashSet<String>> parseScopeString(String scopeString) throws TokenException {

        Map<Backplane2MessageFields.EnumVal,LinkedHashSet<String>> scopes = new LinkedHashMap<Backplane2MessageFields.EnumVal, LinkedHashSet<String>>();
        logger.debug("parsing scopeString = '" + scopeString + "' ...");

        if (StringUtils.isNotBlank(scopeString)) {
            // TODO: is there a maximum length for the scope string?
            for (String token : scopeString.split(Scope.SEPARATOR, Scope.MAX_PARAMETERS)) {
                if (StringUtils.isEmpty(token)) continue;

                Pair<Backplane2MessageFields.EnumVal, String> keyValue = parseScopeToken(token);

                if (!scopes.containsKey(keyValue.getLeft())) {
                    scopes.put(keyValue.getLeft(), new LinkedHashSet<String>());
                }
                scopes.get(keyValue.getLeft()).add(keyValue.getRight());
                logger.debug("added " + keyValue.getLeft() + ":" + keyValue.getRight());
            }
        }
        logger.debug("parsed scopes: " + scopes);
        return scopes;
    }

    private static Pair<Backplane2MessageFields.EnumVal, String> parseScopeToken(String token) throws TokenException {
        // all scope tokens need to have the ":" key/value delimiter
        // if they have the ":" in the value (like the source field MUST have)
        // we will use the first ":" as the key/value delimiter.
        if (StringUtils.isEmpty(token)) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid, empty scope token: " + token);
        }

        int delimiterIndex = token.indexOf(Scope.DELIMITER);
        if (delimiterIndex == -1 || delimiterIndex >= token.length()-1 ) {
            String errMsg = "Malformed scope, token: '" + token + "' not in format: '<key>" + DELIMITER + "<value>'";
            logger.debug(errMsg);
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, errMsg);
        }

        Option<Backplane2MessageFields.EnumVal> key = Backplane2Message.scopeKeys().get(token.substring(0, delimiterIndex));
        if ( ! key.isDefined() ) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid scope key / message field in token: " + token);
        }

        Option<String> value = Option.apply(token.substring(delimiterIndex + 1));
        try {
            key.get().validate(value, null);
        } catch (MessageException e) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid scope value in token: " + token);
        }
        if (StringUtils.isBlank((String)value.getOrElse(null))) {
            throw new TokenException(OAuth2.OAUTH2_TOKEN_INVALID_SCOPE, "invalid scope value in token: " + token);
        }
        
        return new Pair<Backplane2MessageFields.EnumVal, String>(key.get(), value.get());
    }
}
