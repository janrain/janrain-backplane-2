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

package com.janrain.backplane.provision;

import com.janrain.backplane.common.AuthException;
import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.model.Message;
import com.janrain.backplane.common.model.MessageField;
import com.janrain.backplane.config.BackplaneConfig;
import com.janrain.backplane.config.dao.ConfigDAOs;
import com.janrain.backplane.dao.DaoAll;
import com.janrain.backplane.dao.DaoException;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.backplane.server2.model.BusConfig2;
import com.janrain.backplane.server2.model.BusConfig2Fields;
import com.janrain.backplane.server2.oauth2.model.*;
import com.janrain.backplane2.server.*;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.oauth2.TokenException;
import com.janrain.util.ServletUtil;
import com.janrain.util.Utils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import scala.Option;
import scala.collection.JavaConversions;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;


/**
 * Controller handling the API calls for backplane customer configuration provisioning.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/v2/provision/*")
@SuppressWarnings({"UnusedDeclaration"})
public class ProvisioningController2 {

    // - PUBLIC

    @RequestMapping(value = "/bus/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> busList(HttpServletRequest request, @RequestBody ListRequest listRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());
        return doList(BusConfig2.class, listRequest.getEntities(), BusConfig2Fields.BUS_NAME());
    }

    @RequestMapping(value = "/user/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> userList(HttpServletRequest request, @RequestBody ListRequest listRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());
        return doList(BusOwner.class, listRequest.getEntities(), BusOwnerFields.USER());
    }

    @RequestMapping(value = "/client/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> clientList(HttpServletRequest request, @RequestBody ListRequest listRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());
        return doList(Client.class, listRequest.getEntities(), ClientFields.USER());
    }

    @RequestMapping(value = "/bus/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busDelete(HttpServletRequest request, @RequestBody ListRequest deleteRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(deleteRequest.getAdmin(), deleteRequest.getSecret());
        return doDelete(BusConfig2.class, deleteRequest.getEntities());
    }

    @RequestMapping(value = "/user/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userDelete(HttpServletRequest request, @RequestBody ListRequest deleteRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(deleteRequest.getAdmin(), deleteRequest.getSecret());
        return doDelete(BusOwner.class, deleteRequest.getEntities());
    }

    @RequestMapping(value = "/client/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> clientDelete(HttpServletRequest request, @RequestBody ListRequest deleteRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(deleteRequest.getAdmin(), deleteRequest.getSecret());
        return doDelete(Client.class, deleteRequest.getEntities());
    }

    @RequestMapping(value = "/bus/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busUpdate(HttpServletRequest request, @RequestBody UpdateRequest updateRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(updateRequest.getAdmin(), updateRequest.getSecret());
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Map<String,String> config : updateRequest.getConfigs()) {
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                BusConfig2 busConfig = new BusConfig2(config);
                Option<BusOwner> ownerOption = BP2DAOs.busOwnerDao().get((String)config.get(BusConfig2Fields.OWNER().name().toUpperCase()));
                BusOwner owner = ownerOption.isDefined() ? ownerOption.get() : null;
                if (owner == null) {
                    throw new BackplaneServerException("Invalid bus owner: " + config.get(BusConfig2Fields.OWNER().name().toUpperCase()));
                }
                BP2DAOs.busDao().store(busConfig);
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            String requestEntryId = config.get(BusConfig2Fields.BUS_NAME().name().toUpperCase());
            result.put(requestEntryId != null ? requestEntryId : "<unknown>", updateStatus);
        }
        return result;
    }

    @RequestMapping(value = "/user/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userUpdate(HttpServletRequest request, @RequestBody UpdateRequest updateRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(updateRequest.getAdmin(), updateRequest.getSecret());
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Map<String,String> config : updateRequest.getConfigs()) {
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                BP2DAOs.busOwnerDao().store(new BusOwner(config));
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            String requestEntryId = config.get(BusOwnerFields.USER().name().toUpperCase());
            result.put(requestEntryId != null ? requestEntryId : "<unknown>", updateStatus);
        }
        return result;
    }

    @RequestMapping(value = "/client/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> clientUpdate(HttpServletRequest request, @RequestBody UpdateRequest updateRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        logger.debug("client updateRequest: '" + updateRequest + "'");
        ConfigDAOs.adminDao().getAuthenticated(updateRequest.getAdmin(), updateRequest.getSecret());
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Map<String,String> config : updateRequest.getConfigs()) {
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                BP2DAOs.clientDao().store(new Client(config));
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            String requestEntryId = config.get(ClientFields.USER().name().toUpperCase());
            result.put(requestEntryId != null ? requestEntryId : "<unknown>", updateStatus);
        }
        return result;
    }

    @RequestMapping(value = "/grant/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> grantList(HttpServletRequest request, @RequestBody ListRequest listRequest) throws AuthException, DaoException {
        ServletUtil.checkSecure(request);
        ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());

        Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();

        for(String clientId : listRequest.getEntities()) {
            try {
                Map<String,String> grantsList = new HashMap<String, String>();
                Map<Scope, Set<Grant2>> scopeGrants = GrantLogic.retrieveClientGrants(clientId, null);
                for (Set<Grant2> grantSets : scopeGrants.values()) {
                    for(Grant2 grant : grantSets) {
                        grantsList.put(grant.id(), grant.getAuthorizedScope().toString());
                    }
                    result.put(clientId, grantsList);
                }
            } catch (final BackplaneServerException e) {
                logger.error("Error looking up grants for client " + clientId, e);
                result.put(clientId, new HashMap<String, String>() {{put("error", e.getMessage());}});
            } catch (final TokenException te) {
                logger.error("token (unexpected scope processing?) error: " + te.getMessage(), te);
                result.put(clientId, new HashMap<String, String>() {{
                    put("error", te.getMessage());
                }});
            }
        }
        return result;
    }

    @RequestMapping(value = "/grant/add", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> grantAdd(HttpServletRequest request, @RequestBody GrantRequest grantRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        logger.debug("grant add request: '" + grantRequest + "'");
        return doGrant(grantRequest, true);
    }

    @RequestMapping(value = "/grant/revoke", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> gantRevoke(HttpServletRequest request, @RequestBody GrantRequest grantRequest) throws AuthException {
        ServletUtil.checkSecure(request);
        logger.debug("grant revoke request: '" + grantRequest + "'");
        return doGrant(grantRequest, false);
    }

    /**
     * Handle auth errors as part of normal application flow
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final AuthException e, HttpServletResponse response) {
        logger.info("Provisioning authentication error: " + e.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    /**
     * Handle all other errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final Exception e, HttpServletResponse response) {
        logger.error("Error handling provisioning request", bpConfig.getDebugException(e));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(ProvisioningController2.class);

    private static final String BACKPLANE_UPDATE_SUCCESS = "BACKPLANE_UPDATE_SUCCESS";
    private static final String BACKPLANE_DELETE_SUCCESS = "BACKPLANE_DELETE_SUCCESS";
    private static final String GRANT_UPDATE_SUCCESS = "GRANT_UPDATE_SUCCESS";
    private static final String BACKPLANE_ENTRY_NOT_FOUND = "BACKPLANE_ENTRY_NOT_FOUND";
    private static final String ERR_MSG_FIELD = "ERR_MSG";
    private static final String CONFIG_NOT_FOUND = "CONFIG_NOT_FOUND";

    @Inject
    private BackplaneConfig bpConfig;

    private <T extends Message, F extends MessageField> Map<String, Map<String, String>> doList(Class<T> entityType, List<String> entityNames, F orderField) {

        if (entityNames.size() == 0) return doListAll(entityType);

        final Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();
        for(String entityName : entityNames) {
            T config = null;
            Exception thrown = null;
            try {
                config = (T) Utils.getOrNull( getDaoByObjectType(entityType).get(entityName) );
            } catch (Exception e) {
                thrown = e;
            }
            final String errMgs = thrown != null ? thrown.getMessage() : config == null ? CONFIG_NOT_FOUND : null;

            result.put(entityName,
                errMgs != null ? new HashMap<String, String>() {{ put(ERR_MSG_FIELD, errMgs); }} :
                JavaConversions.mapAsJavaMap(config));
        }
        return result;
    }

    private <T extends Message> Map<String, Map<String, String>> doListAll(Class<T> entityType) {
        Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();
        try {
            List items = JavaConversions.seqAsJavaList(getDaoByObjectType(entityType).getAll());
            for(Object config :  items) {
                result.put(((T)config).id(), JavaConversions.mapAsJavaMap((T)config));
            }
        } catch (final Exception e) {
            result.put(ERR_MSG_FIELD, new HashMap<String, String>() {{ put(ERR_MSG_FIELD, e.getMessage()); }});
        }
        return result;
    }

    private <T extends Message> Map<String, String> doDelete(Class<T> entityType, List<String> entityNames) {
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(String entityName : entityNames) {
            String deleteStatus = BACKPLANE_DELETE_SUCCESS;
            try {
                if (! getDaoByObjectType(entityType).get(entityName).isDefined()) {
                    deleteStatus = BACKPLANE_ENTRY_NOT_FOUND;
                } else {
                    getDaoByObjectType(entityType).delete(entityName);
                }
            } catch (Exception e) {
                deleteStatus = e.getMessage();
            }
            result.put(entityName, deleteStatus);
        }
        return result;
    }

    private Map<String, String> doGrant(GrantRequest grantRequest, boolean addRevoke) throws AuthException {
        Map<String,String> result = new LinkedHashMap<String, String>();
        ConfigDAOs.adminDao().getAuthenticated(grantRequest.getAdmin(), grantRequest.getSecret());

        for(Map.Entry<String,String> newGrantEntry : grantRequest.getGrants().entrySet()) {
            String clientId = newGrantEntry.getKey();
            String buses = newGrantEntry.getValue();
            try {
                if ( ! BP2DAOs.clientDao().get(clientId).isDefined() ) {
                    result.put(clientId, "invalid client_id");
                } else {
                    List<String> busesAsList = Scope.getScopesAsList(buses);
                    validateBuses(busesAsList);
                    if (addRevoke) {
                        addGrant(grantRequest.getAdmin(), clientId, busesAsList);
                        result.put(clientId, "GRANT_UPDATE_SUCCESS");
                    } else {
                        revokeBuses(clientId, busesAsList);
                        result.put(clientId, "GRANT_UPDATE_SUCCESS");
                    }
                }
            } catch (Exception e) {
                result.put(clientId, e.getMessage());
            }
        }
        return result;
    }

    private void validateBuses(List<String> buses) throws BackplaneServerException, DaoException {
        for(String bus : buses) {
            if ( ! BP2DAOs.busDao().get(bus).isDefined() ) {
                throw new BackplaneServerException("Invalid bus: " + bus);
            }
        }
    }

    private void addGrant(String issuer, String clientId, List<String> buses) throws SimpleDBException, BackplaneServerException, DaoException {
        Grant2 grant = new GrantBuilder(
                GrantType.CLIENT_CREDENTIALS,
                GrantState.ACTIVE,
                issuer,
                clientId,
                Scope.getEncodedScopesAsString(Backplane2MessageFields.BUS(), buses))
                .buildGrant();
        BP2DAOs.grantDao().store(grant);
    }

    private void revokeBuses(String clientId, List<String> buses) throws TokenException, SimpleDBException, BackplaneServerException {
        boolean updated = false;
        Scope busesToRevoke = new Scope(Scope.getEncodedScopesAsString(Backplane2MessageFields.BUS(), buses));
        if ( ! BP2DAOs.grantDao().revokeBuses(
                BP2DAOs.grantDao().getByClientId(clientId),
                JavaConversions.collectionAsScalaIterable(buses).toList() )) {
            throw new BackplaneServerException("No grants found to revoke for buses: " + buses);
        }
    }

    private static DaoAll getDaoByObjectType(Class<?> obj) {
        if (BusConfig2.class.isAssignableFrom(obj)) {
            return BP2DAOs.busDao();
        } else if (BusOwner.class.isAssignableFrom(obj)) {
            return BP2DAOs.busOwnerDao();
        } else if (Client.class.isAssignableFrom(obj)) {
            return BP2DAOs.clientDao();
        } else if (Grant.class.isAssignableFrom(obj)) {
            return BP2DAOs.grantDao();
        }

        return null;
    }
}
