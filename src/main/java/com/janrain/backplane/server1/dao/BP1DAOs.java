package com.janrain.backplane.server1.dao;

import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.server1.BP1User;
import com.janrain.backplane.server1.BackplaneMessage;
import com.janrain.backplane.server1.BusConfig1;
import com.janrain.backplane.server1.dao.redis.RedisBP1UserDAO;
import com.janrain.backplane.server1.dao.redis.RedisBackplaneMessageDAO;
import com.janrain.backplane.server1.dao.redis.RedisBusConfig1DAO;

/**
 * @author Johnny Bufu
 */
public class BP1DAOs {

    // - PUBLIC

    public static DAO<BusConfig1> getBusDao() {
        return busDao;
    }

    public static DAO<BP1User> getUserDao() {
        return userDao;
    }

    public static BP1MessageDao getMessageDao() {
        return messageDao;
    }

    public static DAO getDaoByObjectType(Class<?> obj) {
        if (BP1User.class.isAssignableFrom(obj)) {
            return getUserDao();
        } else if (BusConfig1.class.isAssignableFrom(obj)) {
            return getBusDao();
        } else if (BackplaneMessage.class.isAssignableFrom(obj)) {
            return getMessageDao();
        }

        return null;
    }

    // - PRIVATE

    private BP1DAOs() {}

    private static final DAO<BusConfig1> busDao = new RedisBusConfig1DAO();
    private static final DAO<BP1User> userDao = new RedisBP1UserDAO();
    private static final BP1MessageDao messageDao = new RedisBackplaneMessageDAO();

}
