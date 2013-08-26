package com.janrain.backplane.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DAOLegacy;
import com.janrain.backplane2.server.config.User;
import com.janrain.redis.Redis;
import org.apache.commons.lang.SerializationUtils;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisUserDAO implements DAOLegacy<User> {

    public static byte[] getKey(String userId) {
        return ("v1_user_" + userId).getBytes();
    }

    @Override
    public void persist(User user) {
        logger.info("writing key to redis: " + new String(getKey(user.getIdValue())));
        byte[] bytes = SerializationUtils.serialize(user);
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
    public User get(String key) {
        byte[] bytes = Redis.getInstance().get(getKey(key));
        if (bytes != null) {
            return (User) SerializationUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<User> getAll() throws BackplaneServerException {
        List<User> users = new ArrayList<User>();
        List<byte[]> bytesList = Redis.getInstance().lrange(getKey("list"), 0, -1);
        for (byte[] bytes : bytesList) {
            if (bytes != null) {
                users.add((User) SerializationUtils.deserialize(bytes));
            }
        }
        return users;
    }

    private static final Logger logger = Logger.getLogger(RedisUserDAO.class);
}
