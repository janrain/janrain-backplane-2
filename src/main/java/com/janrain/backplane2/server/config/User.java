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

package com.janrain.backplane2.server.config;

import com.janrain.backplane.server.ExternalizableCore;
import com.janrain.commons.message.MessageException;
import com.janrain.commons.message.MessageField;
import com.janrain.crypto.HmacHashUtils;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class User extends ExternalizableCore {

    // - PUBLIC

    public User(String userName, String password) {
        put(Field.USER.getFieldName(), "testBusOwner");
        put(Field.PWDHASH.getFieldName(), HmacHashUtils.hmacHash("busOwnerSecret"));
    }

    public User() {}

    @Override
    public String getIdValue() {
        return get(Field.USER);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public void setUserNamePassword(String userName, String password) {
        this.put(Field.USER.getFieldName(), userName);
        this.put(Field.PWDHASH.getFieldName(), password);
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
        public void validate(String value) throws MessageException {
            if (isRequired()) validateNotBlank(name(), value);
        }
    }

    // PRIVATE

    private static final long serialVersionUID = -366817690860670465L;
}
