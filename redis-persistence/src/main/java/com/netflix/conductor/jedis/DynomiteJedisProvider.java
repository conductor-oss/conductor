package com.netflix.conductor.jedis;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.jedis.DynoJedisClient;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class DynomiteJedisProvider implements Provider<JedisCommands> {

    private final HostSupplier hostSupplier;
    private final TokenMapSupplier tokenMapSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public DynomiteJedisProvider(
            DynomiteConfiguration configuration,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier
    ){
        this.configuration = configuration;
        this.hostSupplier = hostSupplier;
        this.tokenMapSupplier = tokenMapSupplier;
    }

    @Override
    public JedisCommands get() {
        ConnectionPoolConfigurationImpl connectionPoolConfiguration =
                new ConnectionPoolConfigurationImpl(configuration.getClusterName())
                .withTokenSupplier(tokenMapSupplier)
                .setLocalRack(configuration.getAvailabilityZone())
                .setLocalDataCenter(configuration.getRegion())
                .setSocketTimeout(0)
                .setConnectTimeout(0)
                .setMaxConnsPerHost(
                        configuration.getMaxConnectionsPerHost()
                );

        return new DynoJedisClient.Builder()
                .withHostSupplier(hostSupplier)
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(configuration.getClusterName())
                .withCPConfig(connectionPoolConfiguration)
                .build();
    }
}
