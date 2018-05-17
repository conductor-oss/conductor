package com.netflix.conductor.server;

import com.netflix.conductor.redis.utils.JedisMock;

import javax.inject.Provider;

import redis.clients.jedis.JedisCommands;

public class InMemoryJedisProvider implements Provider<JedisCommands> {
    @Override
    public JedisCommands get() {
        return new JedisMock();
    }
}
