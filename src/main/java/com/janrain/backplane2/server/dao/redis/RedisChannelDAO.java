package com.janrain.backplane2.server.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane2.server.Channel;
import com.janrain.backplane2.server.dao.ChannelDAO;
import com.janrain.redis.Redis;
import org.apache.commons.lang.SerializationUtils;

import java.util.List;

/**
 * @author Johnny Bufu
 */
public class RedisChannelDAO implements ChannelDAO {

    public static byte[] getKey(String id) {
        return ("v2_channel_" + id).getBytes();
    }

    @Override
    public Channel get(String id) throws BackplaneServerException {
        byte[] bytes = Redis.getInstance().get(getKey(id));
        if (bytes != null) {
            return (Channel) SerializationUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<Channel> getAll() throws BackplaneServerException {
        throw new UnsupportedOperationException("List all channels not supported");
    }

    @Override
    public void persist(Channel channelId) throws BackplaneServerException {
        Redis.getInstance().set(
                getKey(channelId.getIdValue()),
                SerializationUtils.serialize(channelId),
                Integer.parseInt(channelId.get(Channel.ChannelField.EXPIRE_SECONDS))
        );
    }

    @Override
    public void delete(String channelId) throws BackplaneServerException {
        Redis.getInstance().del(getKey(channelId));
    }
}
