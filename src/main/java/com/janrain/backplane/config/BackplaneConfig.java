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

package com.janrain.backplane.config;

import com.janrain.backplane.BuildInfo;
import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.config.dao.ConfigDAOs;
import com.janrain.backplane.config.model.ServerConfigFields;
import com.janrain.backplane.dao.redis.RedisPingTask;
import com.janrain.backplane.server1.dao.BP1DAOs;
import com.janrain.backplane.server1.dao.redis.RedisBackplane1DualFormatMessageProcessor;
import com.janrain.backplane.server2.dao.BP2DAOs;
import com.janrain.backplane.server2.dao.redis.RedisBackplane2MessageProcessor;
import com.janrain.commons.util.AwsUtility;
import com.janrain.commons.util.Pair;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.leader.LeaderSelector;
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener;
import com.netflix.curator.retry.ExponentialBackoffRetry;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.reporting.ConsoleReporter;
import com.yammer.metrics.reporting.GraphiteReporter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Holds configuration settings for the Backplane server
 * 
 * @author Jason Cowley, Johnny Bufu
 */
@Scope(value="singleton")
public class BackplaneConfig {

    // - PUBLIC

    public static String buildVersion = BuildInfo.name() + "-" + BuildInfo.version();

    /**
	 * @return the debugMode
	 */
	public static boolean isDebugMode() {
        return ConfigDAOs.serverConfigDao().oneServerConfig().get().isDebugMode();
	}

    /**
     * @return the server default max message value per channel
     * @throws SimpleDBException
     */
    public static long getDefaultMaxMessageLimit() {
        Long max = Long.valueOf(ConfigDAOs.serverConfigDao().oneServerConfig().get().get(ServerConfigFields.DEFAULT_MESSAGES_MAX()).get());
        return max == null ? BackplaneConfig.BP_MAX_MESSAGES_DEFAULT : max;
    }

    /**
     * Allows administrative configuration for excluding specific nodes from keeping the leader selection
     * and becoming the active message processor. Configuration is done by adding an entry to the serverConfigDao
     * object ('backplane_server_config' redis entry), with the key equal to the EC2 instance id of the node
     * (value is ignored).
     *
     * @return true if the node with the supplied EC2 instance id is disabled from message processing
     */
    public static boolean isLeaderDisabled() {
        return ConfigDAOs.serverConfigDao().oneServerConfig().get().get(EC2InstanceId).isDefined();
    }

    public static Throwable getDebugException(Throwable e) {
        return isDebugMode() ? e: null;
    }

    /**
     * Retrieve the server instance id Amazon assigned
     * @return
     */
    public static String getEC2InstanceId() {
        return EC2InstanceId;
    }

    public static void addToBackgroundServices(String workerName, ScheduledExecutorService messageWorkerTask) {
        backgroundServices.put(workerName, messageWorkerTask);
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneConfig.class);

    private static final long BP_MAX_MESSAGES_DEFAULT = 100;

    private static final Map<String, ExecutorService> backgroundServices = new HashMap<String, ExecutorService>();

    // Amazon specific instance-id value
    private static String EC2InstanceId = AwsUtility.retrieveEC2InstanceId();

    @SuppressWarnings({"UnusedDeclaration"})
    private BackplaneConfig() throws BackplaneServerException {
        ConsoleReporter.enable(10, TimeUnit.MINUTES);

        // Dump metrics to graphite server
        String graphiteServer = System.getProperty(SystemProperties.GRAPHITE_SERVER());
        if (StringUtils.isNotBlank(graphiteServer)) {
            try {
                String args[] = graphiteServer.split(":");
                String server = args[0];
                int port = Integer.parseInt(args[1]);
                GraphiteReporter.enable(10, TimeUnit.SECONDS, server, port, SystemProperties.machineName().replace(".","_") + "_" + SystemProperties.INSTANCE_ID());
                logger.info("Graphite server enabled at " + graphiteServer);
            } catch (Exception e) {
                logger.warn("could not enable Graphite from " + graphiteServer + " must be in the form SERVER:PORT");
            }
        }

        ArrayList<LeaderSelector> leaderSelectors = new ArrayList<LeaderSelector>() {{
            // todo: replace with this after transition to new serialization is complete
            //initZk("/v1_worker", new RedisMessageProcessor<Backplane1MessageFields.EnumVal, Backplane1Message>(BP1DAOs.messageDao()));
            add( initZk("/v1_worker", new RedisBackplane1DualFormatMessageProcessor(BP1DAOs.messageDao())) );
            add( initZk("/v2_worker", new RedisBackplane2MessageProcessor(BP2DAOs.messageDao())) );
        }};

        addTask(backgroundServices, RedisPingTask.scalaObject().apply(leaderSelectors));

        logger.info("Configured Backplane Server instance: " + SystemProperties.INSTANCE_ID());
    }

    private void addTask(Map<String, ExecutorService> backgroundServices, Pair<String, ExecutorService> nameAndService) {
        backgroundServices.put(nameAndService.getLeft(), nameAndService.getRight());
    }

    private LeaderSelector initZk(String leaderPath, LeaderSelectorListener listener) throws BackplaneServerException {
        try {
            String zkServerConfig = System.getProperty(SystemProperties.ZOOKEEPER_SERVERS());
            if (StringUtils.isEmpty(zkServerConfig)) {
                String errMsg = "ZooKeeper server system or environment property ('" + SystemProperties.ZOOKEEPER_SERVERS() + "') not configured";
                logger.error( errMsg );
                throw new BackplaneServerException(errMsg);
            }
            CuratorFramework client = CuratorFrameworkFactory.newClient(zkServerConfig, new ExponentialBackoffRetry(50, 20));
            client.start();
            LeaderSelector leaderSelector = new LeaderSelector(client, leaderPath, listener);
            leaderSelector.start();
            com.janrain.redis.Redis.getInstance().setActiveRedisInstance(client);
            return leaderSelector;
        } catch (Exception e) {
            String errMsg = ExceptionUtils.getRootCauseMessage(e);
            logger.error(errMsg, e);
            throw new BackplaneServerException(errMsg, e);
        }
    }

    @PreDestroy
    private void cleanup() {
        Metrics.shutdown();
        for (Map.Entry<String, ExecutorService> serviceEntry : backgroundServices.entrySet()) {
            shutdownExecutor(serviceEntry.getKey(), serviceEntry.getValue());
        }
    }

    private void shutdownExecutor(String serviceName, ExecutorService executor) {
        try {
            executor.shutdownNow();
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.info(serviceName + " background thread shutdown properly");
            } else {
                executor.shutdownNow();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error(serviceName + " background thread did not terminate");
                }
            }
        } catch (InterruptedException e) {
            logger.error(serviceName + " termination threw an exception", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
