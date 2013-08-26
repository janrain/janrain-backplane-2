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

package com.janrain.backplane2.server.provision;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.common.HmacHashUtils;
import com.janrain.backplane.config.dao.ConfigDAOs;
import com.janrain.backplane.config.model.Admin;
import com.janrain.backplane.dao.DaoException;
import com.janrain.backplane.provision.ProvisioningController2;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.model.Backplane2MessageFields;
import com.janrain.backplane.server2.model.BusConfig2;
import com.janrain.backplane.server2.model.BusConfig2Fields;
import com.janrain.backplane.server2.oauth2.model.BusOwner;
import com.janrain.backplane.server2.oauth2.model.BusOwnerFields;
import com.janrain.backplane.server2.oauth2.model.Client;
import com.janrain.backplane.server2.oauth2.model.ClientFields;
import com.janrain.backplane2.server.Scope;
import com.janrain.oauth2.TokenException;
import com.janrain.util.RandomUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.MapType;
import org.codehaus.jackson.map.type.SimpleType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.servlet.HandlerAdapter;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Tom Raney
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/spring/app-config.xml", "classpath:/spring/mvc-config.xml" })
public class ProvisioningController2Test {


    /**
	 * Initialize before every individual test method
	 */
	@Before
    public void init() throws BackplaneServerException, DaoException {

        handlerAdapter = applicationContext.getBean("handlerAdapter", HandlerAdapter.class);
        refreshRequestAndResponse();

        // create temporary admin adminUser account to enable the tests to work
        adminpw = HmacHashUtils.hmacHash(RandomUtils.randomString(20)); 
        adminUser = new Admin(RandomUtils.randomString(20), adminpw);

        //superSimpleDB.store(bpConfig.getTableName(BackplaneConfig.SimpleDBTables.BP_ADMIN_AUTH), User.class, adminUser);
        ConfigDAOs.adminDao().store(adminUser);

        busOwner = new BusOwner(RandomUtils.randomString(20), RandomUtils.randomString(20));
        Map<String,String> clientData = new HashMap<String,String>() {{
            put(ClientFields.USER().name(), RandomUtils.randomString(20));
            put(ClientFields.PWDHASH().name(), RandomUtils.randomString(20));
            put(ClientFields.SOURCE_URL().name(), "http://source.com");
            put(ClientFields.REDIRECT_URI().name(), "http://redirect.com");
        }};
        client = new Client(clientData);
        BP2DAOs.clientDao().store(client);
        logger.info("Created test client: " + client.id());
        bus1 = "qa-test-bus1";
        bus2 = "qa-test-bus2";

        BusConfig2 busConfig1 = new BusConfig2(new HashMap<String,String>() {{
            put(BusConfig2Fields.BUS_NAME().name(), bus1);
            put(BusConfig2Fields.OWNER().name(), busOwner.id());
            put(BusConfig2Fields.RETENTION_TIME_SECONDS().name(), "100");
            put(BusConfig2Fields.RETENTION_STICKY_TIME_SECONDS().name(), "50000");
        }});
        BusConfig2 busConfig2 = new BusConfig2(new HashMap<String,String>() {{
            put(BusConfig2Fields.BUS_NAME().name(), bus2);
            put(BusConfig2Fields.OWNER().name(), busOwner.id());
            put(BusConfig2Fields.RETENTION_TIME_SECONDS().name(), "100");
            put(BusConfig2Fields.RETENTION_STICKY_TIME_SECONDS().name(), "50000");
        }});
        BP2DAOs.busDao().store(busConfig1);
        logger.info("Created test bus: " + bus1);
        BP2DAOs.busDao().store(busConfig2);
        logger.info("Created test bus: " + bus2);
    }

    @After
    public void cleanup() throws BackplaneServerException, TokenException, DaoException {
        //superSimpleDB.delete(bpConfig.getTableName(BackplaneConfig.SimpleDBTables.BP_ADMIN_AUTH), adminUser.getIdValue());
        ConfigDAOs.adminDao().delete(adminUser.id());
        //superSimpleDB.delete(bpConfig.getTableName(BackplaneConfig.SimpleDBTables.BP_CLIENTS), client.getIdValue());
        BP2DAOs.clientDao().delete(client.id());
    }

    @Test
    public void testBusOwnerCRUD() throws Exception {

        refreshRequestAndResponse();
        // create bus owner
        String jsonUpdateBusOwner = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ { \"USER\":\"" + busOwner.id() + "\", \"PWDHASH\":\"" + busOwner.get(BusOwnerFields.PWDHASH()).get() + "\"} ] }";
        logger.info("passing in json " + jsonUpdateBusOwner);
        request.setContent(jsonUpdateBusOwner.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/update");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientUpdate -> " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);

        refreshRequestAndResponse();
        String listJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [] }";
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains(busOwner.id()));

        refreshRequestAndResponse();
        String deleteJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", " +
                            "\"entities\": [\"" + busOwner.id() + "\"] }";
        logger.info("passing in json " + deleteJson);
        request.setContent(deleteJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/delete");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains(busOwner.id()));

        refreshRequestAndResponse();
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertFalse(response.getContentAsString().contains(busOwner.id()));
    }

    @Test
    public void testClientCRUD() throws Exception {

        refreshRequestAndResponse();

        // delete the default client
        BP2DAOs.clientDao().delete(client.id());

        // create client
        String jsonUpdateClient = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ { \"USER\":\"" + client.id() + "\", \"PWDHASH\":\"" + client.get(ClientFields.PWDHASH()).get() + "\"," +
                "\"SOURCE_URL\":\"" + client.get(ClientFields.SOURCE_URL()).get() + "\"," +
                "\"REDIRECT_URI\":\"" + client.get(ClientFields.REDIRECT_URI()).get() + "\"} ] }";
        logger.info("passing in json " + jsonUpdateClient);
        request.setContent(jsonUpdateClient.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/client/update");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientUpdate -> " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue("client create failed: " + response.getContentAsString(), response.getContentAsString().matches("\\{\\s*\"" + client.id() + "\"\\s*:\\s*\"BACKPLANE_UPDATE_SUCCESS\"\\s*\\}"));

        refreshRequestAndResponse();
        String listJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [] }";
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/client/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue(response.getContentAsString().contains(client.id()));

        refreshRequestAndResponse();
        String deleteJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", " +
                            "\"entities\": [\"" + client.id()+ "\"] }";
        logger.info("passing in json " + deleteJson);
        request.setContent(deleteJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/client/delete");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue("delete failed: " + response.getContentAsString(), response.getContentAsString().matches("\\{\\s*\"" + client.id() + "\"\\s*:\\s*\"BACKPLANE_DELETE_SUCCESS\"\\s*\\}"));

        refreshRequestAndResponse();
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/client/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientList() => " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertFalse(response.getContentAsString().contains(client.id()));
    }

    @Test
    public void testBusCRUDinvalidBusOwner() throws Exception {
        // create client
        refreshRequestAndResponse();
        String jsonUpdateBus = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ {\n" +
                "            \"BUS_NAME\": \"customer1\",\n" +
                "            \"OWNER\": \"busowner1\",\n" +
                "            \"RETENTION_TIME_SECONDS\": \"60\",\n" +
                "            \"RETENTION_STICKY_TIME_SECONDS\": \"28800\"\n" +
                "        } ] }";
        logger.info("passing in json " + jsonUpdateBus);
        request.setContent(jsonUpdateBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/update");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testBusUpdate -> " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue(response.getContentAsString().contains("Invalid bus owner: busowner1"));

        BP2DAOs.busDao().delete("customer1");
    }

    @Test
    public void testBusCRUD() throws Exception {

        // create bus owner
        refreshRequestAndResponse();

        String jsonUpdateBusOwner = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ { \"USER\":\"" + busOwner.id() + "\", \"PWDHASH\":\"" + busOwner.get(BusOwnerFields.PWDHASH()).get() + "\"} ] }";
        logger.info("passing in json " + jsonUpdateBusOwner);
        request.setContent(jsonUpdateBusOwner.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/update");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testClientUpdate -> " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue(response.getContentAsString().contains("{\"" + busOwner.id() + "\":\"BACKPLANE_UPDATE_SUCCESS\"}"));

        // create bus
        refreshRequestAndResponse();
        String jsonUpdateBus = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ {\n" +
                "            \"BUS_NAME\": \"customer1\",\n" +
                "            \"OWNER\": \"" + busOwner.id() + "\",\n" +
                "            \"RETENTION_TIME_SECONDS\": \"600\",\n" +
                "            \"RETENTION_STICKY_TIME_SECONDS\": \"28800\"\n" +
                "        } ] }";
        logger.info("passing in json " + jsonUpdateBus);
        request.setContent(jsonUpdateBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/update");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testBusUpdate -> " + response.getContentAsString());
        assertTrue(response.getStatus() == HttpServletResponse.SC_OK);
        assertTrue("Failed: " + response.getContentAsString(), response.getContentAsString().contains("{\"customer1\":\"BACKPLANE_UPDATE_SUCCESS\"}"));

        refreshRequestAndResponse();
        String listJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [] }";
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testBusList() => " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("customer1"));
        assertTrue(response.getContentAsString().contains(busOwner.id()));

        refreshRequestAndResponse();
        String deleteJson = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", " +
                            "\"entities\": [\"customer1\"] }";
        logger.info("passing in json " + deleteJson);
        request.setContent(deleteJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/delete");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testBusCRUD() => " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("customer1"));

        refreshRequestAndResponse();
        logger.info("passing in json " + listJson);
        request.setContent(listJson.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testBusCRUD() => " + response.getContentAsString());
        assertFalse(response.getContentAsString().contains("customer1"));

        BP2DAOs.busOwnerDao().delete(busOwner.id());


    }

    @Test
    public void testProvisioningCreateDelete() throws Exception {
        refreshRequestAndResponse();
        String jsonUpdateClient = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"configs\": [ { \"USER\":\"" + client.id() + "\", \"PWDHASH\":\"" + client.get(ClientFields.PWDHASH()).get() + "\"} ] }";

        String addBusOwner = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\"," +
                " \"entities\": [ \"" + client.id() + "\", \"PWDHASH\":\"" + client.get(ClientFields.PWDHASH()).get() + "\"} ] }";
    }

    @Test
    public void testProvisioningDeleteNonExisting() throws Exception {

        refreshRequestAndResponse();

        String delete = "{ \"entities\":[\"does\", \"not\", \"exist\"], \"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(delete.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/user/delete");
        request.setMethod("POST");

        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningDelete() -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().equals("{\"does\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"not\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"exist\":\"BACKPLANE_ENTRY_NOT_FOUND\"}"));

        refreshRequestAndResponse();
        request.setContent(delete.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/bus/delete");
        request.setMethod("POST");

        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningDelete() -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().equals("{\"does\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"not\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"exist\":\"BACKPLANE_ENTRY_NOT_FOUND\"}"));

        refreshRequestAndResponse();
        request.setContent(delete.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/client/delete");
        request.setMethod("POST");

        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningDelete() -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().equals("{\"does\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"not\":\"BACKPLANE_ENTRY_NOT_FOUND\",\"exist\":\"BACKPLANE_ENTRY_NOT_FOUND\"}"));

    }

    @Test
    public void testProvisioningGrant() throws Exception {

        refreshRequestAndResponse();
        String addGrant = "{\"grants\":{\"" + client.id() + "\":\"" + bus1 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(addGrant.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/add");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/add -> " + response.getContentAsString());
        assertTrue("Invalid response", response.getContentAsString().equals("{\"" + client.id() + "\":\"GRANT_UPDATE_SUCCESS\"}"));

        refreshRequestAndResponse();
        String listGrants = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [ \"" + client.id() + "\" ] }";
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkExists -> " + response.getContentAsString());
        assertTrue("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
        }}));

        refreshRequestAndResponse();
        request.setContent(addGrant.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/revoke");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/revoke -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("GRANT_UPDATE_SUCCESS"));

        refreshRequestAndResponse();
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkNonExists -> " + response.getContentAsString());
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
        }}));

        refreshRequestAndResponse();
        String addGrantWithBogusBus = "{\"grants\":{\"" + client.id() + "\":\"bogus_bus\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(addGrantWithBogusBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/add");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/add -> " + response.getContentAsString());
        assertTrue("Invalid response", response.getContentAsString().equals("{\"" + client.id() + "\":\"Invalid bus: bogus_bus\"}"));

        refreshRequestAndResponse();
        request.setContent(addGrantWithBogusBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/revoke");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/revoke -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("Invalid bus"));
    }

    @Test
    public void testProvisioningGrantMultipleBuses() throws Exception {

        refreshRequestAndResponse();
        String grantRequestString = "{\"grants\":{\"" + client.id() + "\":\"" + bus1 + " " + bus2 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(grantRequestString.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/add");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/add -> " + response.getContentAsString());
        assertTrue("Invalid response", response.getContentAsString().equals("{\"" + client.id() + "\":\"GRANT_UPDATE_SUCCESS\"}"));

        refreshRequestAndResponse();
        String listGrants = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [ \"" + client.id() + "\" ] }";
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkExists -> " + response.getContentAsString());
        assertTrue("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
            add(bus2);
        }}));

        refreshRequestAndResponse();
        request.setContent(grantRequestString.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/revoke");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/revoke -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("GRANT_UPDATE_SUCCESS"));

        refreshRequestAndResponse();
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkNonExists -> " + response.getContentAsString());
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
        }}));
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus2);
        }}));

        refreshRequestAndResponse();
        String grantRequestStringWithBogusBus = "{\"grants\":{\"" + client.id() + "\":\"" + bus1 + " bogus_bus " + bus2 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(grantRequestStringWithBogusBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/add");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/add -> " + response.getContentAsString());
        assertTrue("Invalid response", response.getContentAsString().equals("{\"" + client.id() + "\":\"Invalid bus: bogus_bus\"}"));

        refreshRequestAndResponse();
        String listGrantsWithBogusBus = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [ \"" + client.id() + "\" ] }";
        request.setContent(listGrantsWithBogusBus.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkNoneExists -> " + response.getContentAsString());
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
            add(bus2);
        }}));
    }

    @Test
    public void testProvisioningGrantMultipleBusesRevokeOneAtATime() throws Exception {

        refreshRequestAndResponse();

        String grantRequestString = "{\"grants\":{\"" + client.id() + "\":\"" + bus1 + " " + bus2 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(grantRequestString.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/add");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/add -> " + response.getContentAsString());
        assertTrue("Invalid response", response.getContentAsString().equals("{\"" + client.id() + "\":\"GRANT_UPDATE_SUCCESS\"}"));

        refreshRequestAndResponse();
        String listGrants = "{ \"admin\": \"" + adminUser.id() + "\", \"secret\": \"" + adminpw + "\", \"entities\": [ \"" + client.id() + "\" ] }";
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkExists -> " + response.getContentAsString());
        assertTrue("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
            add(bus2);
        }}));

        refreshRequestAndResponse();
        String revoke1request = "{\"grants\":{\"" + client.id() + "\":\"" + bus1 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(revoke1request.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/revoke");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/revoke -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("GRANT_UPDATE_SUCCESS"));

        refreshRequestAndResponse();
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkNonExists -> " + response.getContentAsString());
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus1);
        }}));
        assertTrue("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{
            add(bus2);
        }}));

        refreshRequestAndResponse();
        String revoke2request = "{\"grants\":{\"" + client.id() + "\":\"" + bus2 + "\"},\"admin\":\"" + adminUser.id() + "\", \"secret\":\"" + adminpw + "\"}";
        request.setContent(revoke2request.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/revoke");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/revoke -> " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("GRANT_UPDATE_SUCCESS"));

        refreshRequestAndResponse();
        request.setContent(listGrants.getBytes());
        request.addHeader("Content-type", "application/json");
        request.setRequestURI("/v2/provision/grant/list");
        request.setMethod("POST");
        handlerAdapter.handle(request, response, controller);
        logger.info("testProvisioningGrant()/checkNonExists -> " + response.getContentAsString());
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{ add(bus1);}}));
        assertFalse("Invalid response", checkGrantExists(response, client.id(), new ArrayList<String>() {{ add(bus2);}}));
    }

    // - PRIVATE

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ProvisioningController2 controller;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerAdapter handlerAdapter;

    private static final Logger logger = Logger.getLogger(ProvisioningController2Test.class);
    private Admin adminUser;
    private String adminpw;
    private BusOwner busOwner;
    private Client client;
    private String bus1;
    private String bus2;


    private void refreshRequestAndResponse() {
		request = new MockHttpServletRequest();
        // simulate https for tests to pass
        request.addHeader("x-forwarded-proto", "https");
		response = new MockHttpServletResponse();
	}

    /** return true if clientToCheck has grants for all busesToCheck */
    private boolean checkGrantExists(MockHttpServletResponse grantListResponse, String clientToCheck, List<String> busesToCheck) throws Exception {

        Map<String,Map<String,String>> listResult = new ObjectMapper().readValue(grantListResponse.getContentAsString(), MapType.construct(Map.class, SimpleType.construct(String.class),
                MapType.construct(Map.class, SimpleType.construct(String.class), SimpleType.construct(String.class))));

        Set<String> busesGranted = new HashSet<String>();
        Map<String, String> grantsForClient = listResult.get(clientToCheck);
        if (grantsForClient != null) {
            for(String grantId : grantsForClient.keySet()) {
                Scope scope = new Scope(grantsForClient.get(grantId));
                busesGranted.addAll(scope.getScopeFieldValues(Backplane2MessageFields.BUS()));
            }
        }

        return busesGranted.containsAll(busesToCheck);
    }
}
