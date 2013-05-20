/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.server;

import com.janrain.backplane.DateTimeUtils;
import com.janrain.backplane.server.config.Backplane1Config;
import com.janrain.backplane.server.dao.DaoFactory;
import com.janrain.backplane.server.dao.redis.RedisBackplaneMessageDAO;
import com.janrain.commons.util.Pair;
import com.janrain.redis.Redis;
import com.janrain.utils.BackplaneSystemProps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener;
import com.netflix.curator.framework.state.ConnectionState;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.MetricName;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Raney
 */
public class MessageProcessor implements LeaderSelectorListener {

    // - PUBLIC

    public MessageProcessor() {}

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
        setLeader(true);
        logger.info("[" + BackplaneSystemProps.getMachineName() + "] v1 leader elected for message processing");

        ScheduledFuture<?> cleanupTask = scheduledExecutor.scheduleAtFixedRate(cleanupRunnable, 2, 2, TimeUnit.HOURS);
        insertMessages();
        cleanupTask.cancel(false);

        logger.info("[" + BackplaneSystemProps.getMachineName() + "] v1 leader ended message processing");
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
        logger.info("v1 leader selector state changed to " + connectionState);
        if (isLeader() && (ConnectionState.LOST == connectionState || ConnectionState.SUSPENDED == connectionState)) {
            setLeader(false);
            logger.info("v1 leader lost connection, giving up leadership");
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(MessageProcessor.class);

    private static final String V1_LAST_ID = "v1_last_id";

    private static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    static {
        Backplane1Config.addToBackgroundServices(scheduledExecutor);
    }

    private static Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                DaoFactory.getBackplaneMessageDAO().deleteExpiredMessages();
            } catch (Exception e) {
                logger.warn(e);
            }
        }
    };

    private final Histogram timeInQueue = Metrics.newHistogram(new MetricName("v1", this.getClass().getName().replace(".","_"), "time_in_queue"));

    private synchronized void setLeader(boolean leader) {
        this.leader = leader;
    }

    private synchronized boolean isLeader() {
        return leader && ! Backplane1Config.isLeaderDisabled();
    }

    private boolean leader = false;

    /**
     * Processor to pull messages off queue and make them available
     *
     */
    private void insertMessages() {

        try {
            logger.info("v1 message processor started");

            List<String> insertionTimes = new ArrayList<String>();
            do {

                logger.debug("beginning message processor loop");

                Jedis jedis = null;

                try {

                    jedis = Redis.getInstance().getWriteJedis();
                    logger.debug("retrieved jedis connection: " + jedis.toString());

                    // set watch on V1_LAST_ID
                    // needs to be set before retrieving the value stored at this key
                    jedis.watch(V1_LAST_ID);
                    Pair<String,Date> lastIdAndDate = getLastMessageId(jedis);
                    String newId = lastIdAndDate.getLeft();

                    // retrieve a handful of messages (ten) off the queue for processing
                    List<byte[]> messagesToProcess = jedis.lrange(RedisBackplaneMessageDAO.V1_MESSAGE_QUEUE.getBytes(), 0, 9);

                    logger.debug("number of messages to process: " + messagesToProcess.size());

                    // only enter the next block if we have messages to process
                    if (messagesToProcess.size() > 0) {

                        Transaction transaction = jedis.multi();

                        insertionTimes.clear();

                        // <ATOMIC> - redis transaction
                        for (byte[] messageBytes : messagesToProcess) {

                            if (messageBytes != null) {
                                BackplaneMessage backplaneMessage = (BackplaneMessage) SerializationUtils.deserialize(messageBytes);

                                if (backplaneMessage != null) {

                                    // retrieve the expiration config per the bus
                                    BusConfig1 busConfig1 = DaoFactory.getBusDAO().get(backplaneMessage.getBus());
                                    int retentionTimeSeconds = 60;
                                    int retentionTimeStickySeconds = 3600;
                                    if (busConfig1 != null) {
                                        // should be here in normal flow
                                        retentionTimeSeconds = busConfig1.getRetentionTimeSeconds();
                                        retentionTimeStickySeconds = busConfig1.getRetentionTimeStickySeconds();
                                    }

                                    String oldId = backplaneMessage.getIdValue();
                                    insertionTimes.add(oldId);

                                    // TOTAL ORDER GUARANTEE
                                    // verify that the date portion of the new message ID is greater than all existing message ID dates
                                    // if not, uptick id by 1 ms and insert
                                    // this means that all message ids have unique time stamps, even if they
                                    // arrived at the same time.

                                    lastIdAndDate = backplaneMessage.updateId(lastIdAndDate);
                                    newId = backplaneMessage.getIdValue();

                                    // messageTime is guaranteed to be a unique identifier of the message
                                    // because of the TOTAL ORDER mechanism above
                                    long messageTime = BackplaneMessage.getDateFromId(newId).getTime();

                                    // save the individual message by key
                                    transaction.set(RedisBackplaneMessageDAO.getKey(newId), SerializationUtils.serialize(backplaneMessage));
                                    // set the message TTL
                                    if (backplaneMessage.isSticky()) {
                                        transaction.expire(RedisBackplaneMessageDAO.getKey(newId), retentionTimeStickySeconds);
                                    } else {
                                        transaction.expire(RedisBackplaneMessageDAO.getKey(newId), retentionTimeSeconds);
                                    }

                                    // add message id to channel list
                                    transaction.rpush(RedisBackplaneMessageDAO.getChannelKey(backplaneMessage.getChannel()), newId.getBytes());

                                    // add message id to sorted set of all message ids as an index
                                    String metaData = backplaneMessage.getBus() + " " + backplaneMessage.getChannel() + " " + newId;
                                    transaction.zadd(RedisBackplaneMessageDAO.V1_MESSAGES.getBytes(), messageTime, metaData.getBytes());

                                    // add message id to sorted set keyed by bus as an index
                                    transaction.zadd(RedisBackplaneMessageDAO.getBusKey(backplaneMessage.getBus()), messageTime, newId.getBytes());

                                    // make sure all subscribers get the update
                                    transaction.publish("alerts", newId);

                                    // pop one message off the queue - which will only happen if this transaction is successful
                                    transaction.lpop(RedisBackplaneMessageDAO.V1_MESSAGE_QUEUE);

                                    logger.info("pipelined message " + oldId + " -> " + newId);
                                }
                            }
                        } // for messages

                        transaction.set(V1_LAST_ID, newId);

                        logger.info("processing transaction with " + insertionTimes.size() + " message(s)");
                        if (transaction.exec() == null) {
                            // the transaction failed
                            continue;
                        }
                        // </ATOMIC> - redis transaction

                        logger.info("flushed " + insertionTimes.size() + " messages");
                        long now = System.currentTimeMillis();
                        for (String insertionId : insertionTimes) {
                            Date oldIdDate = BackplaneMessage.getDateFromId(insertionId);
                            long diff = now - oldIdDate.getTime();
                            timeInQueue.update(diff);
                            if (diff < 0 || diff > 2880000) {
                                logger.warn("time diff is bizarre at: " + diff
                                        + "; now: " + DateTimeUtils.ISO8601.get().format(new Date(now))
                                        + ", oldId: " + DateTimeUtils.ISO8601.get().format(oldIdDate));
                            }
                        }

                        Thread.sleep(150);

                    } // messagesToProcess > 0

                } catch (Exception e) {
                    try {
                        logger.warn("error " + e.getMessage());
                        if (jedis != null) {
                            Redis.getInstance().releaseBrokenResourceToPool(jedis);
                            jedis = null;
                        }
                    } catch (Exception e1) {
                        //ignore
                    }
                    Thread.sleep(2000);
                } finally {
                    try {
                        if (jedis != null) {
                            jedis.unwatch();
                            Redis.getInstance().releaseToPool(jedis);
                        }

                    } catch (Exception e) {
                        Redis.getInstance().releaseBrokenResourceToPool(jedis);
                    }
                }

            } while (isLeader());

        } catch (Exception e) {
            logger.warn("exception thrown in message processor thread: " + e.getMessage());
        }
    }

    private Pair<String,Date> getLastMessageId(Jedis jedis) {
        // retrieve the latest 'live' message ID
        String latestMessageId = jedis.get(V1_LAST_ID);
        Date dateFromId = BackplaneMessage.getDateFromId(latestMessageId);
        return StringUtils.isEmpty(latestMessageId) || null == dateFromId ?
                getLastMessageIdLegacy(jedis) :
                new Pair<String, Date>(latestMessageId, dateFromId);
    }

    private Pair<String,Date> getLastMessageIdLegacy(Jedis jedis) {
        // retrieve the latest 'live' message ID
        // old/legacy method, used as fallback with the deployment of the replacement method
        // todo: remove after transition is completed
        String latestMessageId = null;
        Set<String> latestMessageMetaSet = jedis.zrange(RedisBackplaneMessageDAO.V1_MESSAGES, -1, -1);

        if (latestMessageMetaSet != null && !latestMessageMetaSet.isEmpty()) {
            String[] segs = latestMessageMetaSet.iterator().next().split(" ");
            if (segs.length == 3) {
                latestMessageId = segs[2];
            }
        }

        try {
            return StringUtils.isEmpty(latestMessageId) ?
                    new Pair<String, Date>("", new Date(0)) :
                    new Pair<String, Date>(latestMessageId, DateTimeUtils.ISO8601.get().parse(latestMessageId.substring(0, latestMessageId.indexOf("Z") + 1)));
        } catch (Exception e) {
            logger.warn("error retrieving last message ID and date from V1_MESSAGES: " + e.getMessage(), e);
            return new Pair<String, Date>("", new Date(0));
        }
    }
}
