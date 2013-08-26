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

package com.janrain.backplane2.server.dao;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.dao.DAOLegacy;
import com.janrain.backplane2.server.Token;
import com.janrain.commons.supersimpledb.SimpleDBException;

import java.util.List;

/**
 * @author Tom Raney
 */

public interface TokenDAO extends DAOLegacy<Token> {

    List<Token> retrieveTokensByGrant(String grantId) throws BackplaneServerException;
    void revokeTokenByGrant(String grantId) throws BackplaneServerException;

    void deleteExpiredTokens() throws BackplaneServerException;
    void cacheRevokedCleanup() throws SimpleDBException;
}
