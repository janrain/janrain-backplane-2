package com.janrain.backplane.server1.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.BpSerialUtils;
import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.redis.Redis;
import com.janrain.backplane.server1.BusConfig1;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisBusConfig1DAO implements DAO<BusConfig1> {

    public static byte[] getKey(String busId) {
        return ("v1_bus_" + busId).getBytes();
    }

    private static final Logger logger = Logger.getLogger(RedisBusConfig1DAO.class);

    @Override
    public void persist(BusConfig1 busConfig1) throws BackplaneServerException {
        logger.info("writing key to redis: " + new String(getKey(busConfig1.getIdValue())));
        byte[] bytes = BpSerialUtils.serialize(busConfig1);
        Redis.getInstance().set(getKey(busConfig1.getIdValue()), bytes);
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
                    logger.warn("could not delete v1 bus " + new String(getKey(id)) + " from list " + new String(getKey("list")));
                }
                if (del2.get() == 0) {
                    logger.warn("could not delete v1 bus " + new String(getKey(id)));
                }
            }
            logger.info("removed v1 bus " + id);

        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    @Override
    public BusConfig1 get(String bus) {
        byte[] bytes = Redis.getInstance().get(getKey(bus));
        if (bytes != null) {
            return (BusConfig1) BpSerialUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<BusConfig1> getAll() throws BackplaneServerException {
        List<BusConfig1> users = new ArrayList<BusConfig1>();
        List<byte[]> bytesList = Redis.getInstance().lrange(getKey("list"), 0, -1);
        for (byte[] bytes : bytesList) {
            if (bytes != null) {
                users.add((BusConfig1) BpSerialUtils.deserialize(bytes));
            }
        }
        return users;
    }

}
