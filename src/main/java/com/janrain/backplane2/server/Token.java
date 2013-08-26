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

import com.janrain.backplane.common.DateTimeUtils;
import com.janrain.backplane.server.ExternalizableCore;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.AbstractMessage;
import com.janrain.commons.supersimpledb.message.MessageField;
import com.janrain.oauth2.TokenException;
import com.janrain.servlet.InvalidRequestException;
import com.janrain.util.RandomUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.*;

import static com.janrain.oauth2.OAuth2.*;

/**
 * @author Tom Raney, Johnny Bufu
 */

public class Token extends ExternalizableCore {

    /**
     * Empty default constructor for AWS to use.
     * Don't call directly.
     */
    public Token() {}

    /**
     * Copy constructor (to help with migration).
     */
    public Token(Map<String,String> data) throws SimpleDBException {
        this(data.get(TokenField.ID.getFieldName()), data);
    }

    @Override
    public String getIdValue() {
        return get(TokenField.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(TokenField.class);
    }

    @Override
    public void validate() throws SimpleDBException {
        super.validate();
        if (getType().isPrivileged()) {
            AbstractMessage.validateNotBlank(TokenField.ISSUED_TO_CLIENT_ID.getFieldName(), get(TokenField.ISSUED_TO_CLIENT_ID));
            AbstractMessage.validateNotBlank(TokenField.CLIENT_SOURCE_URL.getFieldName(), get(TokenField.CLIENT_SOURCE_URL));
            AbstractMessage.validateNotBlank(TokenField.BACKING_GRANTS.getFieldName(), get(Token.TokenField.BACKING_GRANTS));
        } else {
            Scope anonScope = getScope();
            LinkedHashSet<String> buses = anonScope.getScopeMap().get(Backplane2MessageFields.BUS());
            LinkedHashSet<String> channels = anonScope.getScopeMap().get(Backplane2MessageFields.CHANNEL());
            if (buses == null || buses.size() > 1 || channels == null || channels.size() > 1) {
                throw new SimpleDBException("invalid scope for anonymous token, must have exactly one bus and one channel specified: " + anonScope);
            }
        }
    }

    public GrantType getType() {
        try {
            return GrantType.valueOf(this.get(TokenField.TYPE));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid GrantType on for TokenField.Type, should have been validated on token creation: " + this.get(TokenField.TYPE));
        }
    }

    public Scope getScope()  {
        try {
            return new Scope(this.get(TokenField.SCOPE));
        } catch (TokenException e) {
            throw new IllegalStateException("Invalid scope on get(), should have been validated on token creation: " + this.get(TokenField.SCOPE));
        }
    }

    /**
     * @return the token's expiration date, or null if the token never expires
     */
    public Date getExpirationDate() {
        String value = this.get(TokenField.EXPIRES);
        try {
            return StringUtils.isNotEmpty(value) ? DateTimeUtils.ISO8601.get().parse(value) : null;
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid ISO8601 date for TokenField.EXPIRES, should have been validated on token creation: " + value);
        } catch (NumberFormatException nfe) {
            logger.error("Error parsing token date: " + value, nfe);
            throw new IllegalStateException("Invalid ISO8601 date for TokenField.EXPIRES, should have been validated on token creation: " + value);
        }
    }

    public String getScopeString() {
        return get(TokenField.SCOPE);
    }

    public boolean isExpired() {
        Date expires = getExpirationDate();
        return expires != null && new Date().getTime() > expires.getTime();
    }

    public static boolean looksLikeOurToken(String tokenString) {
        GrantType grantType = GrantType.fromTokenString(tokenString);
        if (grantType == null) return false;
        String tokenNoPrefix = tokenString.substring(grantType.getTokenPrefix().length());
        return tokenNoPrefix.length() == TOKEN_LENGTH;
    }

    public Map<String, Object> response(final String refreshToken) {
        final Date expires = getExpirationDate();
        return new LinkedHashMap<String, Object>() {{
            put(OAUTH2_TOKEN_TYPE_PARAM_NAME, OAUTH2_TOKEN_TYPE_BEARER);
            put(OAUTH2_ACCESS_TOKEN_PARAM_NAME, getIdValue());
            if (expires != null) put(OAUTH2_TOKEN_RESPONSE_EXPIRES, (expires.getTime() - new Date().getTime()) / 1000);
            put(OAUTH2_SCOPE_PARAM_NAME, getScopeString());
            if (refreshToken != null) put(OAUTH2_REFRESH_TOKEN_PARAM_NAME, refreshToken);
        }};
    }

    public @NotNull List<String> getBackingGrants() {
        String grants = get(TokenField.BACKING_GRANTS);
        return StringUtils.isNotEmpty(grants) ? Arrays.asList(grants.split(GRANTS_SEPARATOR)) : new ArrayList<String>();
    }

    public static enum TokenField implements MessageField {

        ID("id", true),

        TYPE("type", true) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    GrantType.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new SimpleDBException("Invalid token type: " + value);
                }
            }
        },

        EXPIRES("expires", false) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    if (StringUtils.isNotEmpty(value)) {
                        DateTimeUtils.ISO8601.get().parse(value);
                    }
                } catch (ParseException e) {
                    throw new SimpleDBException("Invalid token expiration date: " + value, e);
                }
            }
        },

        SCOPE("scope", true) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    new Scope(value);
                } catch (TokenException e) {
                    throw new InvalidRequestException("Invalid scope: " + value);
                }
            }
        },

        ISSUED_TO_CLIENT_ID("issued_to_client", false),
        
        CLIENT_SOURCE_URL("client_source_url", false) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                if (StringUtils.isNotEmpty(value)) {
                    AbstractMessage.validateUrl(getFieldName(), value);
                }
            }
        },

        BACKING_GRANTS("backing_grants", false);

        @Override
        public String getFieldName() {
            return fieldName;
        }

        @Override
        public boolean isRequired() {
            return required;
        }

        @Override
        public void validate(String value) throws SimpleDBException {
            if (isRequired()) validateNotBlank(getFieldName(), value);
        }

        // - PRIVATE

        private String fieldName;
        private boolean required = true;

        private TokenField(String fieldName, boolean required) {
            this.fieldName = fieldName;
            this.required = required;
        }
    }

    public static class Builder {

        public Builder(GrantType type, String scope) {
            this.type = type;
            data.put(TokenField.TYPE.getFieldName(), type.toString());
            data.put(TokenField.SCOPE.getFieldName(), scope);
        }

        public Builder expires(Date expires) {
            data.put(TokenField.EXPIRES.getFieldName(), DateTimeUtils.ISO8601.get().format(expires));
            return this;
        }
        
        public Builder issuedToClient(String clientId) {
            data.put(TokenField.ISSUED_TO_CLIENT_ID.getFieldName(), clientId);
            return this;
        }
        
        public Builder clientSourceUrl(String clientSourceUrl) {
            data.put(TokenField.CLIENT_SOURCE_URL.getFieldName(), clientSourceUrl);
            return this;
        }

        public Builder grants(List<String> grants) {
            data.put(TokenField.BACKING_GRANTS.getFieldName(),
                     org.springframework.util.StringUtils.collectionToDelimitedString(grants, GRANTS_SEPARATOR));
            return this;
        }
        
        public Token buildToken() throws SimpleDBException {
            String id = type.getTokenPrefix() + RandomUtils.randomString(TOKEN_LENGTH);
            data.put(TokenField.ID.getFieldName(), id);
            return new Token(id, data);
        }
        
        // - PRIVATE
        
        private final Map<String,String> data = new HashMap<String, String>();
        private final GrantType type;
    }
    
    // - PRIVATE

    private static final long serialVersionUID = -5883124096459804032L;
    
    private static final Logger logger = Logger.getLogger(Token.class);

    private static final int TOKEN_LENGTH = 20;

    private static final String GRANTS_SEPARATOR = " ";

    private Token(String id, Map<String,String> data) throws SimpleDBException {
        super.init(id, data);
        logger.debug("created token: " + this.toString());
    }
}
