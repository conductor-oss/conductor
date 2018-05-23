package com.netflix.conductor.jedis;

import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class InMemoryJedisProvider implements Provider<JedisCommands> {
    @Override
    public JedisCommands get() {
        return new JedisMock();
    }
}
