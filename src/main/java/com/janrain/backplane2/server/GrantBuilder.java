package com.janrain.backplane2.server;

import com.janrain.backplane.common.DateTimeUtils;
import com.janrain.backplane.server2.oauth2.model.Grant2;
import com.janrain.backplane.server2.oauth2.model.GrantFields;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.util.RandomUtils;
import scala.collection.JavaConversions;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Johnny Bufu
 */
public class GrantBuilder {

    public GrantBuilder(GrantType type, GrantState state, String issuedById, String issuedToClientId, String scopes) {
        data.put(GrantFields.TYPE().name(), type.toString());
        if (GrantType.AUTHORIZATION_CODE == type) {
            expireSeconds = CODE_EXPIRATION_SECONDS_DEFAULT;
        }
        data.put(GrantFields.STATE().name(), state.toString());
        data.put(GrantFields.ISSUED_BY_USER().name(), issuedById);
        data.put(GrantFields.ISSUED_TO_CLIENT().name(), issuedToClientId);
        data.put(GrantFields.AUTHORIZED_SCOPES().name(), scopes);
    }

    public GrantBuilder(Grant2 other, GrantState state) {
        data.putAll(JavaConversions.mapAsJavaMap(other));
        data.put(GrantFields.STATE().name(), state.toString());
    }

    public GrantBuilder expires(int seconds) {
        expireSeconds = seconds;
        return this;
    }

    public GrantBuilder scope(Scope updatedScope) {
        data.put(GrantFields.AUTHORIZED_SCOPES().name(), updatedScope.toString());
        return this;
    }

    public Grant2 buildGrant() throws SimpleDBException {
        String id = data.get(GrantFields.ID().name());
        if ( id == null) {
            id = RandomUtils.randomString(CODE_ID_LENGTH);
            data.put(GrantFields.ID().name(), id);
        }

        // grant is issued/updated now
        Date now = new Date();
        data.put(GrantFields.TIME_UPDATE().name(), DateTimeUtils.ISO8601.get().format(now));

        // ignore expireSeconds fields overrides data entry
        if (expireSeconds != null) {
            data.put(GrantFields.TIME_EXPIRE().name(), DateTimeUtils.ISO8601.get().format(new Date(now.getTime() + expireSeconds.longValue() * 1000 )));
        } else {
            data.remove(GrantFields.TIME_EXPIRE().name());
        }

        return new Grant2(data);
    }

    private Map<String,String> data = new HashMap<String, String>();

    private Integer expireSeconds = null;

    private static final int CODE_ID_LENGTH = 20;

    private static final int CODE_EXPIRATION_SECONDS_DEFAULT = 600; // 10 minutes

}

