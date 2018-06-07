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
    private final QueueDAO dao;

    @Inject
    public DynoQueueDAOProvider(Configuration config, JedisCommands dynomiteConnection, HostSupplier hostSupplier) {
        this.config = config;
        this.dynomiteConnection = dynomiteConnection;
        this.hostSupplier = hostSupplier;

        // FIXME: This a hacky way to force a single instance.  It would be better for Guice to enforce this by using
        // @Inject constructor on DynoQueueDAO and a binding rather than a Provider.

        String localDC = config.getAvailabilityZone();
        localDC = localDC.replaceAll(config.getRegion(), "");
        DynoShardSupplier ss = new DynoShardSupplier(hostSupplier, config.getRegion(), localDC);

        this.dao = new DynoQueueDAO(dynomiteConnection, dynomiteConnection, ss, config);
    }

    @Override
    public QueueDAO get() {
        return this.dao;
    }
}
