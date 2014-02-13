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
import com.janrain.backplane.common.model.Message;
import com.janrain.backplane.config.BackplaneConfig;
import com.janrain.backplane.config.dao.ConfigDAOs;
import com.janrain.backplane.config.model.Admin;
import com.janrain.backplane.config.model.AdminFields;
import com.janrain.backplane.dao.DaoAll;
import com.janrain.backplane.server1.dao.BP1DAOs;
import com.janrain.backplane.server1.model.BusConfig1;
import com.janrain.backplane.server1.model.BusConfig1Fields;
import com.janrain.backplane.server1.model.BusUser;
import com.janrain.backplane.server1.model.BusUserFields;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.util.Utils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import scala.collection.JavaConversions;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling the API calls for backplane customer configuration provisioning.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/v1*/provision/*")
@SuppressWarnings({"UnusedDeclaration"})
public class ProvisioningController {

    // - PUBLIC

    @RequestMapping(value = "/bus/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> busList(@RequestBody ListRequest listRequest) throws AuthException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());
        Map<String, Map<String, String>> result = doList(BusConfig1.class, listRequest.getEntities());
        logger.info(String.format("admin: %s listed buses: %s",
            Utils.getOrNull(admin.get(AdminFields.USER())),
            listRequest.getEntities())
        );
        return result;
    }

    @RequestMapping(value = "/user/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> userList(@RequestBody ListRequest listRequest) throws AuthException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(listRequest.getAdmin(), listRequest.getSecret());
        Map<String, Map<String, String>> result = doList(BusUser.class, listRequest.getEntities());
        logger.info(String.format("admin: %s listed users: %s",
                Utils.getOrNull(admin.get(AdminFields.USER())),
                listRequest.getEntities())
        );
        return result;
    }

    @RequestMapping(value = "/bus/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busDelete(@RequestBody ListRequest deleteRequest) throws AuthException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(deleteRequest.getAdmin(), deleteRequest.getSecret());
        Map<String, String> result = doDelete(BusConfig1.class, deleteRequest.getEntities());
        logger.info(String.format("admin: %s deleted buses: %s",
                Utils.getOrNull(admin.get(AdminFields.USER())),
                deleteRequest.getEntities())
        );
        return result;
    }

    @RequestMapping(value = "/user/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userDelete(@RequestBody ListRequest deleteRequest) throws AuthException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(deleteRequest.getAdmin(), deleteRequest.getSecret());
        Map<String, String> result = doDelete(BusUser.class, deleteRequest.getEntities());
        logger.info(String.format("admin: %s deleted users: %s",
                Utils.getOrNull(admin.get(AdminFields.USER())),
                deleteRequest.getEntities())
        );
        return result;
    }

    @RequestMapping(value = "/bus/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busUpdate(@RequestBody UpdateRequest updateRequest) throws AuthException, SimpleDBException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(updateRequest.getAdmin(), updateRequest.getSecret());
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Map<String,String> config : updateRequest.getConfigs()) {
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                BP1DAOs.busDao().store(new BusConfig1(config));
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            String requestEntryId = config.get(BusConfig1Fields.BUS_NAME().name().toUpperCase());
            result.put(requestEntryId != null ? requestEntryId : "<unknown>", updateStatus);
        }
        logger.info(String.format("admin: %s updated buses: %s",
                Utils.getOrNull(admin.get(AdminFields.USER())),
                result.keySet())
        );
        return result;
    }

    @RequestMapping(value = "/user/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userUpdate(@RequestBody UpdateRequest updateRequest) throws AuthException, SimpleDBException {
        Admin admin = ConfigDAOs.adminDao().getAuthenticated(updateRequest.getAdmin(), updateRequest.getSecret());
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Map<String,String> config : updateRequest.getConfigs()) {
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                BP1DAOs.userDao().store(new BusUser(config));
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            String requestEntryId = config.get(BusUserFields.USER().name().toUpperCase());
            result.put(requestEntryId != null ? requestEntryId : "<unknown>", updateStatus);
        }
        logger.info(String.format("admin: %s updated users: %s",
                Utils.getOrNull(admin.get(AdminFields.USER())),
                result.keySet())
        );
        return result;
    }

    /**
     * Handle auth errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final AuthException e, HttpServletResponse response) {
        logger.error("Provisioning authentication error: " + e.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    /**
     * Handle auth SimpleDB errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final SimpleDBException e, HttpServletResponse response) {
        logger.error("Provisioning authentication error: " + e.getMessage());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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

    private static final Logger logger = Logger.getLogger(ProvisioningController.class);

    private static final String BACKPLANE_UPDATE_SUCCESS = "BACKPLANE_UPDATE_SUCCESS";
    private static final String BACKPLANE_DELETE_SUCCESS = "BACKPLANE_DELETE_SUCCESS";
    private static final String ERR_MSG_FIELD = "ERR_MSG";
    private static final String CONFIG_NOT_FOUND = "CONFIG_NOT_FOUND";

    @Inject
    private BackplaneConfig bpConfig;

    private <T extends Message> Map<String, Map<String, String>> doList(Class<T> entityType, List<String> entityNames) {

        if (entityNames.size() == 0) return doListAll(entityType);

        final Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();
        for(String entityName : entityNames) {
            T config = null;
            Exception thrown = null;
            try {
                config = (T) Utils.getOrNull(getDaoByObjectType(entityType).get(entityName));
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
            for(Object config : items) {
                result.put( ((T)config).id(), JavaConversions.mapAsJavaMap((T)config));
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
                getDaoByObjectType(entityType).delete(entityName);
            } catch (Exception e) {
                deleteStatus = e.getMessage();
            }
            result.put(entityName, deleteStatus);
        }
        return result;
    }

    private static DaoAll getDaoByObjectType(Class<?> obj) {
        if (BusConfig1.class.isAssignableFrom(obj)) {
            return BP1DAOs.busDao();
        } else if (BusUser.class.isAssignableFrom(obj)) {
            return BP1DAOs.userDao();
        }

        return null;
    }
}
