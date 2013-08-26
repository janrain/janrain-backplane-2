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
import com.janrain.backplane2.server.Grant;
import com.janrain.oauth2.TokenException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tom Raney
 */

public interface GrantDAO extends DAOLegacy<Grant> {

    void update(Grant existing, Grant grant) throws BackplaneServerException, TokenException;
    List<Grant> getByClientId(String clientId) throws BackplaneServerException;
    void deleteByBuses(@NotNull List<String> busesToDelete) throws BackplaneServerException, TokenException;

    /**
     *  Revokes buses across the provided grants.
     *  Not atomic, best effort.
     *  Stops on first error and reports error, even though some grants may have been updated.
     *
     *  @return true if any of the existing grants were updated, false if nothing was updated
     */
    boolean revokeBuses(List<Grant> grants, List<String> buses) throws BackplaneServerException, TokenException;
}
