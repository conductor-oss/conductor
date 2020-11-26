/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.config.utils;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.contrib.EurekaHostsSupplier;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.shard.DynoShardSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

public class RedisQueuesDiscoveryProvider implements Provider<RedisQueues> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisQueuesDiscoveryProvider.class);

    private final DiscoveryClient discoveryClient;
    private final RedisProperties properties;

    RedisQueuesDiscoveryProvider(DiscoveryClient discoveryClient, RedisProperties properties) {
        this.discoveryClient = discoveryClient;
        this.properties = properties;
    }

    @Override
    public RedisQueues get() {

        LOGGER.info("DynoQueueDAO::INIT");

        String domain = properties.getDomain();
        String cluster = properties.getCluster();
        final int readConnPort = properties.getNonQuorumPort();

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
            .withApplicationName(properties.getAppId())
            .withDynomiteClusterName(cluster)
            .withDiscoveryClient(discoveryClient)
            .build();

        DynoJedisClient dynoClientRead = new DynoJedisClient
            .Builder()
            .withApplicationName(properties.getAppId())
            .withDynomiteClusterName(cluster)
            .withHostSupplier(hostSupplier)
            .withConnectionPoolConsistency("DC_ONE")
            .build();

        String region = properties.getRegion();
        String localDC = properties.getAvailabilityZone();

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
            properties.getQueuePrefix(),
            ss,
            60_000,
            60_000
        );

        LOGGER.info("DynoQueueDAO initialized with prefix " + properties.getQueuePrefix() + "!");

        return queues;
    }
}
