/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.redis.dynoqueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.connectionpool.HostSupplier;

public class ConfigurationHostSupplier implements HostSupplier {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationHostSupplier.class);

    private final RedisProperties properties;
    private final RedisPinger pinger;

    public ConfigurationHostSupplier(RedisProperties properties, RedisPinger pinger) {
        this.properties = properties;
        this.pinger = pinger;
    }

    private List<Host> loadHosts() {
        List<Host> hostList = parseHostsFromConfig();
        List<Host> successfulHosts = new ArrayList<>();
        for (Host host : hostList) {
            if (pinger.pingWithRetry(host)) {
                successfulHosts.add(host);
            }
        }
        log.debug("Successful redis hosts after ping {}", successfulHosts);
        return checkForMajority(hostList, successfulHosts);
    }

    @Override
    /**
     * This method is invoked periodically by dynoclient. Should return successful hosts whenever
     * invoked. In case of no majority, throws exception and hence subsequent calls to redis will
     * not be made.
     */
    public List<Host> getHosts() {
        return loadHosts();
    }

    /**
     * checks for majority. in case of n/2+1 nodes are not up, throws exception. if n/2+1 nodes are
     * up, return successful hosts
     *
     * @param allHostsList
     * @param successfulHosts
     * @return
     */
    private List<Host> checkForMajority(List<Host> allHostsList, List<Host> successfulHosts) {
        if (allHostsList.isEmpty()) {
            return allHostsList;
        }
        int majority = allHostsList.size() / 2 + 1;
        log.debug(
                "Successful dynomite hosts size {} allHostSize {} majority {}",
                successfulHosts.size(),
                allHostsList.size(),
                majority);
        if (successfulHosts.size() >= majority) {
            return successfulHosts;
        } else {
            log.info("Successful dynomite hosts {}", successfulHosts);
            log.info("Configured dynomite hosts {}", allHostsList);
            throw new RuntimeException(
                    "Successful dynomite hosts size is less than majority. Hence conductor will not connect to redis");
        }
    }

    private List<Host> parseHostsFromConfig() {
        String hosts = properties.getHosts();
        if (hosts == null) {
            String message =
                    "Missing dynomite/redis hosts. Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.";
            log.error(message);
            throw new RuntimeException(message);
        }
        return parseHostsFrom(hosts);
    }

    private List<Host> parseHostsFrom(String hostConfig) {
        List<String> hostConfigs = Arrays.asList(hostConfig.split(";"));

        return hostConfigs.stream()
                .map(
                        hc -> {
                            String[] hostConfigValues = hc.split(":");
                            String host = hostConfigValues[0];
                            int port = Integer.parseInt(hostConfigValues[1]);
                            String rack = hostConfigValues[2];

                            if (hostConfigValues.length >= 4) {
                                String password = hostConfigValues[3];
                                return new HostBuilder()
                                        .setHostname(host)
                                        .setPort(port)
                                        .setRack(rack)
                                        .setStatus(Host.Status.Up)
                                        .setPassword(password)
                                        .createHost();
                            }
                            return new HostBuilder()
                                    .setHostname(host)
                                    .setPort(port)
                                    .setRack(rack)
                                    .setStatus(Host.Status.Up)
                                    .createHost();
                        })
                .collect(Collectors.toList());
    }
}
