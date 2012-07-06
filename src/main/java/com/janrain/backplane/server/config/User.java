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

package com.janrain.backplane.server.config;

import com.janrain.backplane.server.ExternalizableCore;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.MessageField;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public final class User extends ExternalizableCore {

    // - PUBLIC

    /** For AWS use only */
    public User() { }

    public User(HashMap<String, Object> data) throws SimpleDBException {
        Map<String,String> d = new LinkedHashMap<String, String>(toStringMap(data));
        super.init(d.get(Field.USER.getFieldName()), d);
    }

    @Override
    public String getIdValue() {
        return get(Field.USER);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        // - PUBLIC

        USER,
        PWDHASH;

        @Override
        public String getFieldName() {
            return name();
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void validate(String value) throws SimpleDBException {
            if (isRequired()) validateNotBlank(name(), value);
        }
    }

    // - PRIVATE

    private static final long serialVersionUID = 5437572571979441366L;

}