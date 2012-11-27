package com.janrain.backplane.config;

import com.janrain.backplane.common.ExternalizableCore;
import com.janrain.commons.message.MessageException;
import com.janrain.commons.message.MessageField;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tom Raney
 */
public class BpServerConfig extends ExternalizableCore {

    public BpServerConfig() {
        Map<String,String> d = new LinkedHashMap<String, String>();

        d.put(Field.ID.getFieldName(), BackplaneSystemProps.BPSERVER_CONFIG_KEY);
        d.put(Field.DEBUG_MODE.getFieldName(), Field.DEBUG_MODE_DEFAULT.toString() );
        d.put(Field.CLEANUP_INTERVAL_MINUTES.getFieldName(), Long.toString(Field.CLEANUP_INTERVAL_MINUTES_DEFAULT));
        d.put(Field.DEFAULT_MESSAGES_MAX.getFieldName(), Long.toString(Field.MESSAGES_MAX_DEFAULT));
        d.put(Field.CONFIG_CACHE_AGE_SECONDS.getFieldName(), Long.toString(Field.CONFIG_CACHE_AGE_SECONDS_DEFAULT));
        d.put(Field.TOKEN_CACHE_MAX_MB.getFieldName(), Long.toString(Field.TOKEN_CACHE_MAX_MB_DEFAULT));

        try {
            super.init(BackplaneSystemProps.BPSERVER_CONFIG_KEY, d);
        } catch (MessageException e) {
            logger.error(e);
        }

    }

    public long getMaxCacheAge() {
        return Long.parseLong(get(Field.CONFIG_CACHE_AGE_SECONDS));
    }

    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            logger.error(e);
            return null;
        }
    }

    @Override
    public String getIdValue() {
        return get(Field.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        ID,
        DEBUG_MODE,
        CONFIG_CACHE_AGE_SECONDS,
        CLEANUP_INTERVAL_MINUTES,
        DEFAULT_MESSAGES_MAX {
            @Override
            public void validate(String value) throws MessageException {
                if (isRequired() || value != null) {
                    String fieldName = getFieldName();
                    int intValue = validateInt(fieldName, value);
                }
            }},
        TOKEN_CACHE_MAX_MB;

        @Override
        public String getFieldName() {
            return name();
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void validate(String value) throws MessageException {
            if (isRequired()) validateNotBlank(name(), value);
        }

        // PRIVATE

        private static final Boolean DEBUG_MODE_DEFAULT = false;
        private static final long CONFIG_CACHE_AGE_SECONDS_DEFAULT = 10;
        private static final long CLEANUP_INTERVAL_MINUTES_DEFAULT = 2;
        private static final long MESSAGES_MAX_DEFAULT = 50;
        private static final long TOKEN_CACHE_MAX_MB_DEFAULT = 100;
    }

    // PRIVATE

    private static final long serialVersionUID = -1845727748278295636L;

    private static final Logger logger = Logger.getLogger(BpServerConfig.class);
}
