package com.janrain.backplane2.server.dao;

import com.janrain.backplane2.server.dao.redis.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * @author Tom Raney
 */

@Service(value="redisDaoFactory")
@Scope(value="singleton")
public class BP2DAOs {

    public static AdminDAO getAdminDao() {
        return adminDao;
    }

    public static BusDAO getBusDao() {
        return busDao;
    }

    public static ChannelDAO getChannelDao() {
        return channelDao;
    }

    public static TokenDAO getTokenDao() {
        return tokenDao;
    }

    public static GrantDAO getGrantDao() {
        return grantDao;
    }

    public static BusOwnerDAO getBusOwnerDAO() {
        return busOwnerDao;
    }

    public static ClientDAO getClientDAO() {
        return clientDao;
    }

    // - PRIVATE

    private static final TokenDAO tokenDao = new RedisTokenDAO();
    private static final GrantDAO grantDao = new RedisGrantDAO(tokenDao);
    private static final BusDAO busDao = new RedisBusDAO(grantDao);
    private static final ChannelDAO channelDao = new RedisChannelDAO();
    private static final BusOwnerDAO busOwnerDao = new RedisBusOwnerDAO(busDao);
    private static final ClientDAO clientDao = new RedisClientDAO();
    private static final AdminDAO adminDao = new RedisAdminDAO();

}
