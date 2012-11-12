package com.janrain.backplane2.server.dao;

import com.janrain.commons.message.Message;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Johnny Bufu
 */
public class ConfigLRUCache<T extends Message> {

    // - PUBLIC

    /**
     * @param maxCacheSizeBytes max cache size in bytes; 0 or negative values effectively disable the cache
     */
    public ConfigLRUCache(long maxCacheSizeBytes) {
        this.maxCacheSizeBytes = maxCacheSizeBytes;
    }

    public synchronized long getMaxCacheSizeBytes() {
        return maxCacheSizeBytes;
    }

    public synchronized void setMaxCacheSizeBytes(long maxCacheSizeBytes) {
        this.maxCacheSizeBytes = maxCacheSizeBytes;
    }

    public boolean isCached(String tokenId) {
        return cache.keySet().contains(tokenId);
    }
    public synchronized T get(String id) {
        return cache.get(id);
    }

    public synchronized T add(T item) {
        return cache.put(item.getIdValue(), item);
    }

    /** allows caching of null items */
    public synchronized T add(String id, T item) {
        return cache.put(id, item);
    }

    public synchronized T delete(String id) {
        return cache.remove(id);
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(ConfigLRUCache.class);

    private static final int DEFAULT_INITIAL_CAPACITY = 500;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;


    private final AtomicLong size = new AtomicLong(0);

    private long maxCacheSizeBytes;

    private final Map<String,T> cache = new LinkedHashMap<String, T>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
            int removed = 0;
            Iterator<Map.Entry<String, T>> entries = entrySet().iterator();
            while ( size.get() > maxCacheSizeBytes && entries.hasNext()) {
                Map.Entry<String, T> next = entries.next();
                entries.remove();
                size.addAndGet( -1 * next.getValue().sizeBytes());
                removed++;
            }
            if (removed > 0) {
                logger.info( "Removed " + removed + " " + eldest.getClass().getSimpleName() + " items from cache, " +
                             "new size is: " + size() + " items / " + size.get() + " bytes");
            }
            return false;
        }
    };

}
