package com.janrain.backplane2.server.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane2.server.config.Client;
import com.janrain.backplane2.server.dao.ClientDAO;
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
public class RedisClientDAO implements ClientDAO {

    public static byte[] getKey(String id) {
        return ("v2_client_" + id).getBytes();
    }

    @Override
    public Client get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
        if (bytes != null) {
            return (Client) SerializationUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<Client> getAll() throws BackplaneServerException {
        List<Client> clients = new ArrayList<Client>();
        List<byte[]> byteList = Redis.getInstance().lrange(getKey("list"), 0, -1);
        for (byte [] bytes: byteList) {
            if (bytes != null) {
                clients.add((Client) SerializationUtils.deserialize(bytes));
            }
        }
        return clients;
    }

    @Override
    public void persist(Client obj) throws BackplaneServerException {
        Jedis jedis = null;
        try {
            byte[] bytes = SerializationUtils.serialize(obj);
            jedis = Redis.getInstance().getWriteJedis();

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
            jedis = Redis.getInstance().getWriteJedis();
            byte[] bytes = jedis.get(getKey(id));
            if (bytes != null) {
                Transaction t = jedis.multi();
                Response<Long> del1 = t.lrem(getKey("list"), 0, bytes);
                Response<Long> del2 = t.del(getKey(id));

                t.exec();

                if (del1.get() == 0) {
                    logger.warn("could not delete client " + new String(getKey(id)) + " from list " + new String(getKey("list")));
                }
                if (del2.get() == 0) {
                    logger.warn("could not delete client key " + new String(getKey(id)));
                }
            } else {
                logger.warn("could not locate value for key " + new String(getKey(id)));
            }
        } finally {
            Redis.getInstance().releaseToPool(jedis);
        }
    }

    // PRIVATE

    private static final Logger logger = Logger.getLogger(RedisClientDAO.class);

}
