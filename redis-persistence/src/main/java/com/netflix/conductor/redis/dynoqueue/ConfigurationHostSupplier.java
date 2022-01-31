/*
 * Copyright 2022 Netflix, Inc.
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

    public ConfigurationHostSupplier(RedisProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Host> getHosts() {
        return parseHostsFromConfig();
    }

    private List<Host> parseHostsFromConfig() {
        String hosts = properties.getHosts();
        if (hosts == null) {
            // FIXME This type of validation probably doesn't belong here.
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
