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

package com.janrain.backplane.server2.dao;

import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.server2.BackplaneMessage;
import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.server2.MessagesResponse;
import com.janrain.backplane.server2.Token;
import com.janrain.backplane.server2.oauth2.TokenException;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * @author Tom Raney, Johnny Bufu
 */
public interface BackplaneMessageDAO extends DAO<BackplaneMessage> {

    BackplaneMessage getLatestMessage() throws BackplaneServerException;

    @NotNull BackplaneMessage retrieveBackplaneMessage(@NotNull final String messageId, @NotNull Token token) throws BackplaneServerException, TokenException;

    public long getMessageCount(String channel) throws BackplaneServerException;

    long countMessages() throws BackplaneServerException;

    /**
     * Retrieve all messages by per scope in the provided bpResponse object.
     * Guaranteed to delivery results in order of message ID.
     *
     * @param bpResponse backplane message response
     * @param token access token to be used for message retrieval
     */
    void retrieveMessagesPerScope(@NotNull final MessagesResponse bpResponse, @NotNull final Token token) throws BackplaneServerException;


    List<BackplaneMessage> retrieveMessagesNoScope(String sinceIso8601timestamp) throws BackplaneServerException;

    List<BackplaneMessage> retrieveMessagesByChannel(String channel) throws BackplaneServerException;

    void deleteExpiredMessages() throws BackplaneServerException;

}
