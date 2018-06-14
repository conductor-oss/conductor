package com.netflix.conductor.jedis;

import javax.inject.Provider;
import javax.inject.Singleton;

import redis.clients.jedis.JedisCommands;

@Singleton
public class InMemoryJedisProvider implements Provider<JedisCommands> {
    private final JedisCommands mock = new JedisMock();

    @Override
    public JedisCommands get() {
        return mock;
    }
}
