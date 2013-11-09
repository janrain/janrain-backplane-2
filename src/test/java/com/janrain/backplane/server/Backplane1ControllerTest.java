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

package com.janrain.backplane.server;

import com.janrain.backplane.server1.dao.BP1DAOs;
import com.janrain.backplane.server1.model.Backplane1Message;
import com.janrain.backplane.server1.model.BusConfig1;
import com.janrain.backplane.server1.model.BusConfig1Fields;
import com.janrain.backplane.server1.model.BusUser;
import org.apache.catalina.util.Base64;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Tom Raney
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/spring/app-config.xml", "classpath:/spring/mvc-config.xml" })
public class Backplane1ControllerTest {

    private static final Logger logger = Logger.getLogger(Backplane1ControllerTest.class);

    @Inject
	private ApplicationContext applicationContext;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerAdapter handlerAdapter;

    private static final int DEFAULT_MESSAGE_RETENTION_SECONDS = 60;
    private static final int MAX_MESSAGE_RETENTION_SECONDS = 300;

    @Inject
	private Backplane1Controller controller;

    private static final String TEST_MSG = "{\n" +
            "        \"source\": \"ftp://bla_source/\",\n" +
            "        \"type\": \"bla_type\",\n" +
            "        \"sticky\": \"false\",\n" +
            "        \"payload\": {\n" +
            "            \"payload1\": \"bla1\",\n" +
            "            \"payload2\": \"bla2\"\n" +
            "        }\n" +
            "    }\n";

    /**
	 * Initialize before every individual test method
	 */
	@Before
	public void init() {
        assertNotNull(applicationContext);
        handlerAdapter = applicationContext.getBean("handlerAdapter", HandlerAdapter.class);
		refreshRequestAndResponse();
	}


    private void refreshRequestAndResponse() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    public void testGetChannel() throws Exception {
        String[] versions = new String[]{"1.1", "1.2", "1.3"};
        for (String version : versions) {
            refreshRequestAndResponse();
            request.setRequestURI("/" + version + "/bus/test/channel/test1");
            request.setMethod("GET");

            handlerAdapter.handle(request, response, controller);
            logger.debug("testGetChannel() => " + response.getContentAsString());
            assertTrue(response.getContentAsString().contains("[]"));
        }
    }

    @Test
    public void testGetBus() throws Exception {

        String[] versions = new String[]{"1.1", "1.2", "1.3"};
        for (String version : versions) {
            refreshRequestAndResponse();
            Backplane1Message message = null;

            try {

                BP1DAOs.userDao().store(new BusUser("testBusOwner", "busOwnerSecret"));


                scala.collection.immutable.Map$.MODULE$.empty();

                Map<String,String> busConfigData = new HashMap<String,String>() {{
                    put(BusConfig1Fields.BUS_NAME().name(), "test");
                    put(BusConfig1Fields.POST_USERS().name(), "testBusOwner");
                    put(BusConfig1Fields.GETALL_USERS().name(), "testBusOwner");
                    put(BusConfig1Fields.RETENTION_TIME_SECONDS().name(), "60");
                    put(BusConfig1Fields.RETENTION_STICKY_TIME_SECONDS().name(), "28800");
                }};
                BP1DAOs.busDao().store(new BusConfig1(BP1DAOs.asScalaImmutableMap(busConfigData)));

                // encode un:pw
                String credentials = "testBusOwner:busOwnerSecret";

                Map<String,Object> msg = new ObjectMapper().readValue(TEST_MSG, new TypeReference<Map<String,Object>>() {});
                message = new Backplane1Message("test", "test2", DEFAULT_MESSAGE_RETENTION_SECONDS, MAX_MESSAGE_RETENTION_SECONDS, msg);
                BP1DAOs.messageDao().store(message);

                Thread.sleep(1000);

                String encodedCredentials = new String(Base64.encode(credentials.getBytes()));
                request.setAuthType("BASIC");
                request.addHeader("Authorization", "Basic " + encodedCredentials);
                request.setRequestURI("/" + version + "/bus/test/channel/test2");
                request.setMethod("GET");

                handlerAdapter.handle(request, response, controller);
                logger.info("testGetBus() => " + response.getContentAsString());
                assertFalse("passed", response.getContentAsString().contains("[]"));

            } finally {
                BP1DAOs.userDao().delete("testBusOwner");
                BP1DAOs.busDao().delete("test");
                if (message != null) {
                    com.janrain.backplane.server.redisdao.BP1DAOs.getMessageDao().delete(message.id());
                }
            }
        }
    }
}
