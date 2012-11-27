package com.janrain.backplane.server1.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.BpSerialUtils;
import com.janrain.backplane.common.User;
import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.redis.Redis;
import com.janrain.backplane.server1.BP1User;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisBP1UserDAO implements DAO<BP1User> {

    public static byte[] getKey(String userId) {
        return ("v1_user_" + userId).getBytes();
    }

    @Override
    public void persist(BP1User user) {
        logger.info("writing key to redis: " + new String(getKey(user.getIdValue())));
        byte[] bytes = BpSerialUtils.serialize(user);
        Redis.getInstance().set(getKey(user.getIdValue()), bytes);
        Redis.getInstance().rpush(getKey("list"), bytes);
    }

    @Override
    public void delete(String id) throws BackplaneServerException {
        Jedis jedis = null;
        try {
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = jedis.get(getKey(id));
            if (bytes != null) {
                Transaction t = jedis.multi();
                Response<Long> del1 = t.lrem(getKey("list"), 0, bytes);
                Response<Long> del2 = t.del(getKey(id));

                t.exec();

                if (del1.get() == 0) {
                    logger.warn("could not delete user " + new String(getKey(id)) + " from list " + new String(getKey("list")));
                }
                if (del2.get() == 0) {
                    logger.warn("could not delete user key " + new String(getKey(id)));
                }
            }
            logger.info("removed user " + id);

        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public BP1User get(String key) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(key));
        if (bytes != null) {
            return User.asDaoType(BpSerialUtils.<User>deserialize(bytes), BP1User.class);
        } else {
            return null;
        }
    }

    @Override
    public List<BP1User> getAll() throws BackplaneServerException {
        List<BP1User> users = new ArrayList<BP1User>();
        List<byte[]> bytesList = Redis.getInstance().lrange(getKey("list"), 0, -1);
        for (byte[] bytes : bytesList) {
            if (bytes != null) {
                users.add(User.asDaoType(BpSerialUtils.<User>deserialize(bytes), BP1User.class));
            }
        }
        return users;
    }

    private static final Logger logger = Logger.getLogger(RedisBP1UserDAO.class);
}
