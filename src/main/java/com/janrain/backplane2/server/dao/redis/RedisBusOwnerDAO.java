package com.janrain.backplane2.server.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane2.server.config.User;
import com.janrain.backplane2.server.dao.BusDAO;
import com.janrain.backplane2.server.dao.BusOwnerDAO;
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
public class RedisBusOwnerDAO implements BusOwnerDAO {

    public static byte[] getKey(String id) {
        return ("v2_bus_owner_" + id).getBytes();
    }

    public RedisBusOwnerDAO(BusDAO busDao) {
        this.busDao = busDao;
    }

    @Override
    public User get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
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
        for (byte [] bytes : bytesList) {
            if (bytes != null) {
                users.add((User) SerializationUtils.deserialize(bytes));
            }
        }
        return users;

    }

    @Override
    public void persist(User obj) throws BackplaneServerException {
        Jedis jedis = null;

        try {
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = SerializationUtils.serialize(obj);
            Transaction t = jedis.multi();

            t.set(getKey(obj.getIdValue()), bytes);
            t.rpush(getKey("list"), bytes);
            t.exec();

        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public void delete(String id) throws BackplaneServerException {
        Jedis jedis = null;

        try {
            logger.info("=== BEGIN BUS OWNER " + id + " DELETE ===");
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = jedis.get(getKey(id));
            if (bytes != null) {
                Transaction t= jedis.multi();
                Response<Long> del1 = t.del(getKey(id));
                Response<Long> del2 = t.lrem(getKey("list"), 0, bytes);
                t.exec();

                if (del1.get() == 0) {
                    logger.warn("failed to remove " + new String(getKey(id)));
                }
                if (del2.get() == 0) {
                    logger.warn("failed to remove " + new String(getKey(id)) + " from list " + new String(getKey("list")));
                }
            } else {
                logger.warn("could not locate value for key " + new String(getKey(id)));
            }

            // delete all associated buses (and their dependencies)
            busDao.deleteByOwner(id);
            logger.info("Bus owner " + id + " deleted successfully");
            logger.info("=== END BUS OWNER DELETE ===");
        } catch (Exception e) {
            logger.error("An exception occurred during an atomic operation.  Corruption may have occurred while removing bus owner: " + id);
            throw new BackplaneServerException(e.getMessage());
        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    // PRIVATE

    private static final Logger logger = Logger.getLogger(RedisBusOwnerDAO.class);

    private final BusDAO busDao;

}
