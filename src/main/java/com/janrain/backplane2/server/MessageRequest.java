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

package com.janrain.backplane2.server;

import com.janrain.servlet.InvalidRequestException;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

/**
 * @author Tom Raney
 */
public class MessageRequest {


    public MessageRequest(String callback, String since, String block)  {

        if (StringUtils.isNotEmpty(callback)) {
            if (!callback.matches("[\\._a-zA-Z0-9]*")) {
                throw new InvalidRequestException("invalid_request", "Callback parameter value is malformed");
            }
        }

        this.since = StringUtils.isBlank(since) ? "" : since;

        try {
            Integer blockSeconds = Integer.valueOf(block);
            if (blockSeconds < 0 || blockSeconds > MAX_BLOCK_SECONDS) {
                throw new InvalidRequestException("Invalid value for block parameter (" + block + "), must be between 0 and " + MAX_BLOCK_SECONDS );
            }
            this.returnBefore = new Date(System.currentTimeMillis() + blockSeconds.longValue() * 1000);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Invalid value for block parameter (" + block + "): " + e.getMessage() );
        }

    }

    public Date getReturnBefore() {
        return returnBefore;
    }

    public String getSince() {
        return since;
    }

    // - PRIVATE

    private static final int MAX_BLOCK_SECONDS = 25;

    private final Date returnBefore;
    private final String since;

}
