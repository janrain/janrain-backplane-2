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

package com.janrain.backplane.server1;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.ExternalizableCore;
import com.janrain.commons.message.MessageException;
import com.janrain.commons.message.MessageField;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class BusConfig1 extends ExternalizableCore {

    // - PUBLIC

    public BusConfig1() {}

    public BusConfig1(String busName, String owner, String retentionTimeSeconds, String retentionTimeStickySeconds) throws BackplaneServerException {
        Map<String,String> d = new LinkedHashMap<String, String>();
        d.put(Field.BUS_NAME.getFieldName(), busName);
        d.put(Field.RETENTION_TIME_SECONDS.getFieldName(), retentionTimeSeconds);
        d.put(Field.RETENTION_STICKY_TIME_SECONDS.getFieldName(), retentionTimeStickySeconds);
        d.put(owner, BUS_PERMISSION.GETALL.name());
        try {
            init(busName, d);
        } catch (MessageException e) {
            throw new BackplaneServerException(e.getMessage(), e);
        }
    }

    public enum BUS_PERMISSION { GETALL, POST, GETPAYLOAD, IDENTITY }

    @Override
    public String getIdValue() {
        return get(Field.BUS_NAME);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public String getBusName() {
        return getIdValue();
    }

    public int getRetentionTimeSeconds() {
        return Integer.valueOf(get(Field.RETENTION_TIME_SECONDS));
    }

    public int getRetentionTimeStickySeconds() {
        return Integer.valueOf(get(Field.RETENTION_STICKY_TIME_SECONDS));
    }

    public Collection<BUS_PERMISSION> getPermissions(String user) {
        if (isBusConfigField(user)) {
            throw new IllegalArgumentException("Invalid user name: " + user);
        }

        String perms = get(user);
        EnumSet<BUS_PERMISSION> result = EnumSet.noneOf(BUS_PERMISSION.class);
        if (StringUtils.isNotBlank(perms)) {
            for(String perm : perms.split(",")) {
                result.add(BUS_PERMISSION.valueOf(perm));
            }
        }
        return result;
    }

    public static enum Field implements MessageField {

        BUS_NAME,

        RETENTION_TIME_SECONDS {
            @Override
            public void validate(String value) throws MessageException {
                if (isRequired() || value != null) {
                    String fieldName = getFieldName();
                    int intValue = validateInt(fieldName, value);
                    if (intValue < RETENTION_MIN_SECONDS || intValue > RETENTION_MAX_SECONDS) {
                        throw new IllegalArgumentException("Value of " + fieldName + " = " + intValue + " but must be between " + RETENTION_MIN_SECONDS + " and " + RETENTION_MAX_SECONDS);
                    }
                }
            }},

        RETENTION_STICKY_TIME_SECONDS {
            @Override
            public void validate(String value) throws MessageException {
                if (isRequired() || value != null) {
                    String fieldName = getFieldName();
                    validateInt(fieldName, value);
                    int intValue = validateInt(fieldName, value);
                    if (intValue < RETENTION_STICKY_MIN_SECONDS || intValue > RETENTION_STICKY_MAX_SECONDS) {
                        throw new IllegalArgumentException("Value of " + fieldName + " = " + intValue + " but must be between " + RETENTION_STICKY_MIN_SECONDS + " and " + RETENTION_STICKY_MAX_SECONDS);
                    }
                }
            }};




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

        // - PRIVATE

        private static final int RETENTION_MIN_SECONDS = 60;
        private static final int RETENTION_MAX_SECONDS = 604800; // one week
        private static final int RETENTION_STICKY_MIN_SECONDS = 28800; // eight hours
        private static final int RETENTION_STICKY_MAX_SECONDS = 604800; // one week

    }


    // - PRIVATE

    private static final long serialVersionUID = 2634562172424519254L;

    private static final Logger logger = Logger.getLogger(BusConfig1.class);

    private boolean isBusConfigField(String name) {
        try {
            Field.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
