package com.janrain.backplane.server2.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.BpSerialUtils;
import com.janrain.backplane.redis.Redis;
import com.janrain.backplane.server2.AuthSession;
import com.janrain.backplane.server2.dao.AuthSessionDAO;
import org.apache.commons.lang.NotImplementedException;

import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisAuthSessionDAO implements AuthSessionDAO {

    public static byte[] getKey(String id) {
        return ("v2_auth_session_" + id).getBytes();
    }

    @Override
    public AuthSession get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
        if (bytes != null) {
            return (AuthSession) BpSerialUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<AuthSession> getAll() throws BackplaneServerException {
        throw new NotImplementedException();
    }

    @Override
    public void persist(AuthSession authSession) throws BackplaneServerException {
        Redis.getInstance().set(getKey(authSession.getIdValue()), BpSerialUtils.serialize(authSession));
    }

    @Override
    public void delete(String id) throws BackplaneServerException {
        throw new NotImplementedException();
    }

    @Override
    public void deleteExpiredAuthSessions() {
        throw new NotImplementedException();
    }
}
