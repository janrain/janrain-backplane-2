package com.janrain.backplane.server1.dao;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.server1.BackplaneMessage;
import com.janrain.commons.message.MessageException;

import java.util.List;

/**
 * @author Johnny Bufu
 */
public interface BP1MessageDao extends DAO<BackplaneMessage> {

    public List<BackplaneMessage> getMessagesByBus(String bus, String since, String sticky)
            throws MessageException, BackplaneServerException;

    public List<BackplaneMessage> getMessagesByChannel(String bus, String channel, String since, String sticky)
            throws MessageException, BackplaneServerException;

    public int getMessageCount(String bus, String channel);

    public void deleteExpiredMessages();
}
