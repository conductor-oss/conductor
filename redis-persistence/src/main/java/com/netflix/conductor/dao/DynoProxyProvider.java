package com.netflix.conductor.dao;


import com.netflix.conductor.dao.dynomite.DynoProxy;

import javax.inject.Inject;
import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class DynoProxyProvider implements Provider<DynoProxy> {

    private final JedisCommands dynomiteConnection;

    @Inject
    public DynoProxyProvider(JedisCommands dynomiteConnection) {
        this.dynomiteConnection = dynomiteConnection;
    }

    @Override
    public DynoProxy get() {
        return new DynoProxy(dynomiteConnection);
    }
}
