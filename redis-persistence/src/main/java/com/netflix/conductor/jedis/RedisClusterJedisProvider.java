package com.netflix.conductor.jedis;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

public class RedisClusterJedisProvider implements Provider<JedisCommands> {

    private final HostSupplier hostSupplier;

    @Inject
    public RedisClusterJedisProvider(HostSupplier hostSupplier){
        this.hostSupplier = hostSupplier;
    }

    @Override
    public JedisCommands get() {
        // FIXME This doesn't seem very safe, but is how it was in the code this was moved from.
        Host host = new ArrayList<Host>(hostSupplier.getHosts()).get(0);
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(1000);
        return new JedisCluster(new HostAndPort(host.getHostName(), host.getPort()), poolConfig);
    }
}
