package com.netflix.conductor.jedis;

import redis.clients.jedis.JedisCommands;

import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class InMemoryJedisProvider implements Provider<JedisCommands> {
    private final JedisCommands mock = new JedisMock();

    @Override
    public JedisCommands get() {
        return mock;
    }
}
