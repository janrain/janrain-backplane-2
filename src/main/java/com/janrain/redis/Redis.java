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

package com.janrain.redis;

import com.janrain.utils.BackplaneSystemProps;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.cache.ChildData;
import com.netflix.curator.framework.recipes.cache.PathChildrenCache;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheEvent;
import com.netflix.curator.framework.recipes.cache.PathChildrenCacheListener;
import com.netflix.curator.framework.recipes.locks.InterProcessMutex;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Raney
 */
public class Redis implements PathChildrenCacheListener {

    public static Redis getInstance() {
        return instance;
    }

    /**
     * Be sure to return to pool!
     * @return
     */

    public Jedis getReadJedis() {
        return getJedisFromPool(getReadPool());
    }

    public Jedis getWriteJedis() {
        return getJedisFromPool(getWritePool());
    }

    public void releaseToPool(Jedis jedis) {
        releaseToPool(jedis, false);
    }

    public void releaseBrokenResourceToPool(Jedis jedis) {
        releaseToPool(jedis, true);
    }

    public void releaseToPool(Jedis jedis, boolean isBroken) {
        if (jedis == null) return;
        logger.debug("returning jedis: " + jedis.toString() + " to pool -> isBroken: " + isBroken);
        JedisPool pool = checkedOutJedises.get(jedis);
        if (pool != null) {
            synchronized (jedis) {
                if (isBroken) {
                    pool.returnBrokenResource(jedis);
                } else {
                    pool.returnResource(jedis);
                }
                checkedOutJedises.remove(jedis);
            }
        } else {
            logger.warn("attempted to return a broken jedis: " + jedis.toString() + " that wasn't checked out");
        }

    }

    public void set(byte[] key, byte[] value) {
        Jedis jedis = getWritePool().getResource();

        try {
            jedis.set(key,value);
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public void del(byte[] key) {
        Jedis jedis = getWritePool().getResource();
        try {
            jedis.del(key);
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public void set(byte[] key, byte[] value, int seconds) {
        Jedis jedis = getWritePool().getResource();
        try {
            jedis.setex(key, seconds, value);
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public void set(String key, String value) {
        set(key, value, null);
    }

    public void set(String key, String value, @Nullable Integer seconds) {
        Jedis jedis = getWritePool().getResource();
        try {
            if (seconds == null) {
                jedis.set(key, value);
            } else {
                jedis.setex(key, seconds, value);
            }
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public void append(byte[] key, byte[] value) {
        Jedis jedis = getWritePool().getResource();
        try {
            jedis.append(key, value);
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public Long rpush(final byte[] key, final byte[] string) {
        Jedis jedis = getWritePool().getResource();
        try {
            return jedis.rpush(key, string);
        } finally {
            getWritePool().returnResource(jedis);
        }
    }

    public long llen(byte[] key) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.llen(key);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public byte[] get(byte[] key) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.get(key);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public String get(String key) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.get(key);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public List<byte[]> mget(byte[]... keys) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.mget(keys);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public byte[] lpop(byte[] key) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.lpop(key);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public List<byte[]> lrange(final byte[] key, final int start, final int end) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.lrange(key, start, end);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public Set<byte[]> zrangebyscore(final byte[] key, double min, double max) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.zrangeByScore(key, min, max);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public long zcard(final byte[] key) {
        Jedis jedis = getReadPool().getResource();
        try {
            return jedis.zcard(key);
        } finally {
            getReadPool().returnResource(jedis);
        }
    }

    public void setActiveRedisInstance(CuratorFramework client) {
        this.curatorFramework = client;
        InterProcessMutex lock = null;

        try {
            lock = new InterProcessMutex(client, REDIS_LOCK);
            lock.acquire();

            PathChildrenCache pathChildrenCache = new PathChildrenCache(client, REDIS, true);
            pathChildrenCache.getListenable().addListener(this);
            pathChildrenCache.start(true);

            ChildData childData = pathChildrenCache.getCurrentData(REDIS);

            String redisServer = null;
            if (childData != null) {
                byte[] bytes = childData.getData();
                redisServer = new String(bytes);
            }

            if (redisServer == null) {
                // set the node to the default redis server
                setRedisServer(BackplaneSystemProps.REDIS_SERVER_PRIMARY);
            } else {
                // accept the cluster wide redis server
                currentRedisServerForWrites = redisServer;
            }

        } catch (Exception e) {
            logger.error(e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception e) {
                    logger.error("could not release lock " + e);
                }
            }
        }

    }

    @Override
    public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {
        try {
            if ( pathChildrenCacheEvent.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED
                    || pathChildrenCacheEvent.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {

                ChildData childData = pathChildrenCacheEvent.getData();
                if (childData != null && childData.getData() != null)  {
                    String newValue = new String(childData.getData());
                    currentRedisServerForWrites = newValue;
                    logger.info("redis server changed: " + newValue);
                }
            }
        } catch (Exception e) {
            logger.warn("failure with childEvent");
            throw e;
        }

    }

    public synchronized void setRedisServer(String server) {

        if (curatorFramework != null) {
            try {
                if (this.curatorFramework.checkExists().forPath(REDIS_SERVER) == null) {
                    this.curatorFramework.create().forPath(REDIS_SERVER);
                }
                this.curatorFramework.setData().forPath(REDIS_SERVER, server.getBytes());
            } catch (Exception e) {
                logger.error(e);
            }
        }
        logger.info("redis server set to " + server);
    }

    public void ping() {

        Jedis jedisWrite = null;
        Jedis jedisRead = null;

        String replyJedisWrite = "ERROR";
        String replyJedisRead = "ERROR";

        try {
            jedisWrite = this.getWriteJedis();
            replyJedisWrite = jedisWrite.ping();
        } catch (Exception e) {
            // something bad
            logger.warn("error during ping");
        } finally {
            logger.info("PING " + System.getProperty(BackplaneSystemProps.REDIS_SERVER_PRIMARY) + " (" + BackplaneSystemProps.REDIS_SERVER_PRIMARY + ") -> " + replyJedisWrite);
            releaseToPool(jedisWrite);
        }

        try {
            jedisRead = this.getReadJedis();
            replyJedisRead = jedisRead.ping();
        }   catch (Exception e) {
            // something bad
            logger.warn("error during ping");
        } finally {
            logger.info("PING " + System.getProperty(BackplaneSystemProps.REDIS_SERVER_READS) + " (" + BackplaneSystemProps.REDIS_SERVER_READS + ") -> " + replyJedisRead);
            releaseToPool(jedisRead);
        }

    }

    // PRIVATE

    private static final Logger logger = Logger.getLogger(Redis.class);

    private String[] currentRedisServerForReads;
    private String currentRedisServerForWrites;

    private final JedisPool poolForWrites;
    private final ArrayList<JedisPool> poolForReads = new ArrayList<JedisPool>();

    private static Redis instance = new Redis();
    private final String REDIS_LOCK = "/redislock";
    private final String REDIS = "/redis";
    private final String REDIS_SERVER = "/redis/server";

    private static final long REDIS_MAX_WAIT_SECONDS = 2l;

    private CuratorFramework curatorFramework;

    private Redis() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxActive(50);
        jedisPoolConfig.setTestWhileIdle(true);
        //jedisPoolConfig.setTestOnBorrow(true);
        jedisPoolConfig.setMaxWait(REDIS_MAX_WAIT_SECONDS*1000l);
        jedisPoolConfig.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK);
        jedisPoolConfig.setMaxIdle(-1);
        jedisPoolConfig.setMinIdle(20);

        String redisServerConfig = System.getProperty(BackplaneSystemProps.REDIS_SERVER_PRIMARY);
        if (StringUtils.isEmpty(redisServerConfig)) {
            logger.error("cannot find configuration entry for " + BackplaneSystemProps.REDIS_SERVER_PRIMARY);
            System.exit(1);
        }
        String[] args = redisServerConfig.split(":");
        int port = 6379;
        if (args.length == 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                logger.error("port for Redis server is malformed: " + redisServerConfig);
            }
        }

        poolForWrites = new JedisPool(jedisPoolConfig, args[0], port);

        redisServerConfig = System.getProperty(BackplaneSystemProps.REDIS_SERVER_READS);

        if (StringUtils.isEmpty(redisServerConfig)) {
            logger.error("cannot find configuration entry for " + BackplaneSystemProps.REDIS_SERVER_READS);
            System.exit(1);
        }

        String[] readServers = redisServerConfig.split(",");
        for (int i=0; i< readServers.length; i++) {
            args = readServers[i].split(":");
            port = 6379;
            if (args.length == 2) {
                try {
                    port = Integer.parseInt(args[1]);
                    currentRedisServerForReads[i] = args[0];
                    poolForReads.add(new JedisPool(jedisPoolConfig, args[0], port));
                } catch (NumberFormatException e) {
                    logger.error("invalid Redis server configuration: " + redisServerConfig);
                    System.exit(1);
                }
            }
        }

    }

    
    private JedisPool getWritePool() {
        return poolForWrites;
    }

    private JedisPool getReadPool() {
        Random random = new Random();
        return poolForReads.get(random.nextInt(poolForReads.size()));
    }

    private Jedis getJedisFromPool(JedisPool pool) {
        try {
            logger.debug("attempting to get resource from pool");
            Jedis jedis = pool.getResource();
            synchronized (jedis) {
                checkedOutJedises.put(jedis, pool);
                logger.debug("jedis " + jedis.toString() + " checked out from pool " + pool.toString());
            }
            return jedis;
        } catch (RuntimeException e) {
            logger.warn("an error occurred while trying to retrieve connection to redis");
            throw e;
        }
    }

    private ConcurrentHashMap<Jedis, JedisPool> checkedOutJedises = new ConcurrentHashMap<Jedis, JedisPool>();
    private final Gauge checkedOutJedisesCounterGauge = Metrics.newGauge(new MetricName("redis", this.getClass().getName().replace(".","_"), "map_db_connections"), new Gauge<Integer>() {
        public Integer value() {
            return checkedOutJedises.size();
        }
    });

}
