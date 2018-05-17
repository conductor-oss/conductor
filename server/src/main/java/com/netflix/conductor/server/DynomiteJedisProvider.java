package com.netflix.conductor.server;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.jedis.DynoJedisClient;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class DynomiteJedisProvider implements Provider<JedisCommands> {
    public static final String DYNOMITE_CLUSTER_NAME_PROPERTY_NAME = "workflow.dynomite.cluster.name";
    public static final String DYNOMITE_MAX_CONNECTIONS_PROPERTY_NAME = "workflow.dynomite.connection.maxConnsPerHost";
    public static final int DYNOMITE_MAX_CONNECTIONS_DEFAULT_VALUE = 10;

    private final HostSupplier hostSupplier;
    private final TokenMapSupplier tokenMapSupplier;
    private final Configuration configuration;
    private final String clusterName;

    @Inject
    public DynomiteJedisProvider(
            Configuration configuration,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier
    ){
        this.configuration = configuration;
        this.hostSupplier = hostSupplier;
        this.tokenMapSupplier = tokenMapSupplier;
        this.clusterName = configuration.getProperty(DYNOMITE_CLUSTER_NAME_PROPERTY_NAME, "");
    }

    @Override
    public JedisCommands get() {
        ConnectionPoolConfigurationImpl connectionPoolConfiguration = new ConnectionPoolConfigurationImpl(clusterName)
                .withTokenSupplier(tokenMapSupplier)
                .setLocalRack(configuration.getAvailabilityZone())
                .setLocalDataCenter(configuration.getRegion())
                .setSocketTimeout(0)
                .setConnectTimeout(0)
                .setMaxConnsPerHost(
                        configuration.getIntProperty(
                                DYNOMITE_MAX_CONNECTIONS_PROPERTY_NAME,
                                DYNOMITE_MAX_CONNECTIONS_DEFAULT_VALUE
                        )
                );

        return new DynoJedisClient.Builder()
                .withHostSupplier(hostSupplier)
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(clusterName)
                .withCPConfig(connectionPoolConfiguration)
                .build();
    }
}
