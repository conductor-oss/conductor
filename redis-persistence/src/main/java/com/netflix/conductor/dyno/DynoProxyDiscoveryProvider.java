package com.netflix.conductor.dyno;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.jedis.DynoJedisClient;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

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
