package com.netflix.conductor.dao;


import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.redis.DynoShardSupplier;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class DynoQueueDAOProvider implements Provider<QueueDAO> {
    private final Configuration config;
    private final JedisCommands dynomiteConnection;
    private final HostSupplier hostSupplier;

    @Inject
    public DynoQueueDAOProvider(Configuration config, JedisCommands dynomiteConnection, HostSupplier hostSupplier) {
        this.config = config;
        this.dynomiteConnection = dynomiteConnection;
        this.hostSupplier = hostSupplier;
    }

    @Override
    public QueueDAO get() {
        String localDC = config.getAvailabilityZone();
        localDC = localDC.replaceAll(config.getRegion(), "");
        DynoShardSupplier ss = new DynoShardSupplier(hostSupplier, config.getRegion(), localDC);

        return new DynoQueueDAO(dynomiteConnection, dynomiteConnection, ss, config);
    }
}
