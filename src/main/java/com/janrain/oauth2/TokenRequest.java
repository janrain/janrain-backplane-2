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

package com.janrain.oauth2;

import com.janrain.backplane.dao.DaoException;

import java.util.Map;

/**
 * Token requests rely on a grant type that is responsible for generating tokens and token responses
 * from specific token request subclases.
 *
 * @author Tom Raney, Johnny Bufu
 */
public interface TokenRequest {
    /**
     * @return the token response appropriate for this token (and its grant type)
     */
    Map<String,Object> tokenResponse() throws TokenException, DaoException;
}
