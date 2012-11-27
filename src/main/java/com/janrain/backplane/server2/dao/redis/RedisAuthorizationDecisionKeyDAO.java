package com.janrain.backplane.server2.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.BpSerialUtils;
import com.janrain.backplane.redis.Redis;
import com.janrain.backplane.server2.dao.AuthorizationDecisionKeyDAO;
import com.janrain.backplane.server2.oauth2.AuthorizationDecisionKey;
import org.apache.commons.lang.NotImplementedException;

import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisAuthorizationDecisionKeyDAO implements AuthorizationDecisionKeyDAO {

    public static byte[] getKey(String id) {
        return ("v2_auth_decision_" + id).getBytes();
    }

    @Override
    public AuthorizationDecisionKey get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
        if (bytes != null) {
            return (AuthorizationDecisionKey) BpSerialUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<AuthorizationDecisionKey> getAll() throws BackplaneServerException {
        throw new NotImplementedException();
    }

    @Override
    public void persist(AuthorizationDecisionKey authorizationDecisionKey) throws BackplaneServerException {
        Redis.getInstance().set(getKey(authorizationDecisionKey.getIdValue()), BpSerialUtils.serialize(authorizationDecisionKey));
    }

    @Override
    public void delete(String id) throws BackplaneServerException {
        throw new NotImplementedException();
    }

    @Override
    public void deleteExpiredAuthorizationDecisionKeys() {
        throw new NotImplementedException();
    }
}
