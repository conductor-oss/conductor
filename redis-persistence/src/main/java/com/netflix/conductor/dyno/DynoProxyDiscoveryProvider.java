package com.netflix.conductor.dyno;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.jedis.DynoJedisClient;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class DynoProxyDiscoveryProvider implements Provider<JedisCommands> {
    private final DiscoveryClient discoveryClient;
    private final Configuration configuration;

    @Inject
    public DynoProxyDiscoveryProvider(DiscoveryClient discoveryClient, Configuration configuration) {
        this.discoveryClient = discoveryClient;
        this.configuration = configuration;
    }

    @Override
    public JedisCommands get() {
        String cluster = configuration.getProperty("workflow.dynomite.cluster", null);
        String applicationName = configuration.getAppId();
        return new DynoJedisClient
                .Builder()
                .withApplicationName(applicationName)
                .withDynomiteClusterName(cluster)
                .withDiscoveryClient(discoveryClient)
                .build();
    }
}
