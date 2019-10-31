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
import com.netflix.dyno.jedis.DynoJedisClient;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.commands.JedisCommands;

public class DynoProxyDiscoveryProvider implements Provider<JedisCommands> {
    private final DiscoveryClient discoveryClient;
    private final DynomiteConfiguration configuration;

    @Inject
    public DynoProxyDiscoveryProvider(DiscoveryClient discoveryClient, DynomiteConfiguration configuration) {
        this.discoveryClient = discoveryClient;
        this.configuration = configuration;
    }

    @Override
    public JedisCommands get() {
        return new DynoJedisClient
                .Builder()
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(configuration.getCluster())
                .withDiscoveryClient(discoveryClient)
                .build();
    }
}
