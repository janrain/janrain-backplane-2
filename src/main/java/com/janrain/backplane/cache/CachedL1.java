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

package com.janrain.backplane.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Map;

/**
 * @author Tom Raney
 */
public class CachedL1 implements Cached {

    private static CachedL1 instance = new CachedL1();
    private boolean isEnabled = true;

    private CachedL1() {
        try {
            CacheManager.create();

            //Create a Cache specifying its configuration.
            Cache testCache = new Cache(
                    new CacheConfiguration("memory", 5000)
                            .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LFU)
                            .overflowToDisk(false)
                            .eternal(false)
                            .timeToLiveSeconds(3600)
                            .timeToIdleSeconds(30)
                            .diskPersistent(false)
                            .diskExpiryThreadIntervalSeconds(0));


            CacheManager.getInstance().addCache(testCache);
            String[] cacheNames = CacheManager.getInstance().getCacheNames();
            System.out.println("created L1 cache");
        } catch (Exception e) {
            logger.warn("could not create L1 cache");
            isEnabled = false;
        }
    }

    public static CachedL1 getInstance() {
       return instance;
    }

    public Map<Object,Element> getAll(Collection<String> keys) {
        if (!isEnabled) {
            return null;
        }
        try {
            Cache cache = CacheManager.getInstance().getCache("memory");
            return cache.getAll(keys);
        } catch (Exception e) {
            logger.warn("L1 cached failed to getAll");
            return null;
        }
    }



    @Override
    public Object getObject(String key) {
        if (!isEnabled) {
            return null;
        }

        try {
            Cache cache = CacheManager.getInstance().getCache("memory");
            Element element = cache.get(key);
            if (element != null) {
                return element.getObjectValue();
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("L1 cache failed to get", e);
            return null;
        }
    }

    @Override
    public void setObject(String key, int expiration, Object obj) {
        if (!isEnabled) {
            return;
        }
        try {
            Cache cache = CacheManager.getInstance().getCache("memory");
            cache.put(new Element(key, obj));
        } catch (Exception e) {
            logger.warn("L1 cache failed to set", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(CachedL1.class);
}
