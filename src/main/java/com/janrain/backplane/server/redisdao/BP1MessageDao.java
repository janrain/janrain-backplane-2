package com.janrain.backplane.server.redisdao;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DAOLegacy;
import com.janrain.backplane.server.BackplaneMessage;
import com.janrain.commons.supersimpledb.SimpleDBException;

import java.util.List;

/**
 * @author Johnny Bufu
 */
public interface BP1MessageDao extends DAOLegacy<BackplaneMessage> {

    List<BackplaneMessage> getMessagesByBus(String bus, String since, String sticky)
            throws SimpleDBException, BackplaneServerException;

    List<BackplaneMessage> getMessagesByChannel(String bus, String channel, String since, String sticky)
            throws SimpleDBException, BackplaneServerException;

    int getMessageCount(String bus, String channel);

    void deleteExpiredMessages();
}
