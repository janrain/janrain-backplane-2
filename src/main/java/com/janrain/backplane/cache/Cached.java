package com.janrain.backplane.cache;

/**
 * @author Tom Raney
 */
public interface Cached {

    Object getObject(String key);
    void setObject(String key, int expiration, Object obj);
    boolean isEnabled();
}
