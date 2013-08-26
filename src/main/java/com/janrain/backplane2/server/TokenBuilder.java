package com.janrain.backplane2.server;

import com.janrain.backplane.common.DateTimeUtils;
import com.janrain.backplane.server2.oauth2.model.Token;
import com.janrain.backplane.server2.oauth2.model.TokenFields;
import com.janrain.util.RandomUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Johnny Bufu
 */
public class TokenBuilder {

    public TokenBuilder(GrantType type, String scope) {
        this.type = type;
        data.put(TokenFields.TYPE().name(), type.toString());
        data.put(TokenFields.SCOPE().name(), scope);
    }

    public TokenBuilder expires(Date expires) {
        data.put(TokenFields.EXPIRES().name(), DateTimeUtils.ISO8601.get().format(expires));
        return this;
    }

    public TokenBuilder issuedToClient(String clientId) {
        data.put(TokenFields.ISSUED_TO_CLIENT().name(), clientId);
        return this;
    }

    public TokenBuilder clientSourceUrl(String clientSourceUrl) {
        data.put(TokenFields.CLIENT_SOURCE_URL().name(), clientSourceUrl);
        return this;
    }

    public TokenBuilder grants(List<String> grants) {
        data.put(TokenFields.BACKING_GRANTS().name(),
                org.springframework.util.StringUtils.collectionToDelimitedString(grants, Token.GRANTS_SEPARATOR()));
        return this;
    }

    public Token buildToken() {
        String id = type.getTokenPrefix() + RandomUtils.randomString(Token.TOKEN_LENGTH());
        data.put(TokenFields.ID().name(), id);
        return new Token(data);
    }

    // - PRIVATE

    private final Map<String,String> data = new HashMap<String, String>();
    private final GrantType type;
}
