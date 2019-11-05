/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dyno;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.contrib.EurekaHostsSupplier;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.shard.DynoShardSupplier;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisQueuesDiscoveryProvider implements Provider<RedisQueues> {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueuesDiscoveryProvider.class);

    private final DiscoveryClient discoveryClient;
    private final DynomiteConfiguration configuration;

    @Inject
    RedisQueuesDiscoveryProvider(DiscoveryClient discoveryClient, DynomiteConfiguration configuration) {
        this.discoveryClient = discoveryClient;
        this.configuration = configuration;
    }

    @Override
    public RedisQueues get() {

        logger.info("DynoQueueDAO::INIT");

        String domain = configuration.getDomain();
        String cluster = configuration.getCluster();
        final int readConnPort = configuration.getNonQuorumPort();

        EurekaHostsSupplier hostSupplier = new EurekaHostsSupplier(cluster, discoveryClient) {
            @Override
            public List<Host> getHosts() {
                List<Host> hosts = super.getHosts();
                List<Host> updatedHosts = new ArrayList<>(hosts.size());
                hosts.forEach(host -> updatedHosts.add(
                        new HostBuilder()
                                .setHostname(host.getHostName())
                                .setIpAddress(host.getIpAddress())
                                .setPort(readConnPort)
                                .setRack(host.getRack())
                                .setDatacenter(host.getDatacenter())
                                .setStatus(host.isUp() ? Host.Status.Up : Host.Status.Down)
                                .createHost()
                ));
                return updatedHosts;
            }
        };

        DynoJedisClient dynoClient = new DynoJedisClient
                .Builder()
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(cluster)
                .withDiscoveryClient(discoveryClient)
                .build();

        DynoJedisClient dynoClientRead = new DynoJedisClient
                .Builder()
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(cluster)
                .withHostSupplier(hostSupplier)
                .withConnectionPoolConsistency("DC_ONE")
                .build();

        String region = configuration.getRegion();
        String localDC = configuration.getAvailabilityZone();

        if (localDC == null) {
            throw new Error("Availability zone is not defined.  " +
                    "Ensure Configuration.getAvailabilityZone() returns a non-null and non-empty value.");
        }

        localDC = localDC.replaceAll(region, "");
        ShardSupplier ss = new DynoShardSupplier(
                dynoClient.getConnPool().getConfiguration().getHostSupplier(),
                region,
                localDC
        );

        RedisQueues queues = new RedisQueues(
                dynoClient,
                dynoClientRead,
                configuration.getQueuePrefix(),
                ss,
                60_000,
                60_000
        );

        logger.info("DynoQueueDAO initialized with prefix " + configuration.getQueuePrefix() + "!");

        return queues;
    }
}
