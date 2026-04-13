/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.redis.jedis;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.RedisProperties;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import static org.junit.jupiter.api.Assertions.*;

class RetryingJedisCommandsTest {

    @Test
    void wrapReturnsDelegateWhenRetriesDisabled() {
        JedisCommands delegate = delegate(Map.of("get", args -> "value"));

        JedisCommands wrapped = RetryingJedisCommands.wrap(delegate, redisProperties(0));

        assertSame(delegate, wrapped);
    }

    @Test
    void retriesConnectionExceptionUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        JedisCommands delegate =
                delegate(
                        Map.of(
                                "get",
                                args -> {
                                    if (attempts.getAndIncrement() == 0) {
                                        throw new JedisConnectionException("socket closed");
                                    }
                                    return "value";
                                }));

        JedisCommands wrapped = RetryingJedisCommands.wrap(delegate, redisProperties(1));

        assertEquals("value", wrapped.get("key"));
        assertEquals(2, attempts.get());
    }

    @Test
    void retriesFailoverDataExceptionUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        JedisCommands delegate =
                delegate(
                        Map.of(
                                "set",
                                args -> {
                                    if (attempts.getAndIncrement() == 0) {
                                        throw new JedisDataException(
                                                "READONLY You can't write against a read only replica.");
                                    }
                                    return "OK";
                                }));

        JedisCommands wrapped = RetryingJedisCommands.wrap(delegate, redisProperties(1));

        assertEquals("OK", wrapped.set("key", "value"));
        assertEquals(2, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableDataException() {
        AtomicInteger attempts = new AtomicInteger();
        JedisCommands delegate =
                delegate(
                        Map.of(
                                "get",
                                args -> {
                                    attempts.incrementAndGet();
                                    throw new JedisDataException(
                                            "WRONGTYPE Operation against a key holding the wrong kind of value");
                                }));

        JedisCommands wrapped = RetryingJedisCommands.wrap(delegate, redisProperties(3));

        JedisDataException exception =
                assertThrows(JedisDataException.class, () -> wrapped.get("key"));
        assertTrue(exception.getMessage().contains("WRONGTYPE"));
        assertEquals(1, attempts.get());
    }

    @Test
    void retriesEvalshaPath() {
        AtomicInteger attempts = new AtomicInteger();
        JedisCommands delegate =
                delegate(
                        Map.of(
                                "evalsha",
                                args -> {
                                    if (attempts.getAndIncrement() == 0) {
                                        throw new JedisConnectionException("failover in progress");
                                    }
                                    return List.of("message-1");
                                }));

        JedisCommands wrapped = RetryingJedisCommands.wrap(delegate, redisProperties(1));

        assertEquals(
                List.of("message-1"),
                wrapped.evalsha("sha1", List.of("queue"), List.of("1", "2", "3")));
        assertEquals(2, attempts.get());
    }

    private static RedisProperties redisProperties(int maxRetryAttempts) {
        RedisProperties redisProperties = new RedisProperties(new ConductorProperties());
        redisProperties.setMaxRetryAttempts(maxRetryAttempts);
        return redisProperties;
    }

    private static JedisCommands delegate(Map<String, Invocation> methods) {
        return (JedisCommands)
                Proxy.newProxyInstance(
                        JedisCommands.class.getClassLoader(),
                        new Class<?>[] {JedisCommands.class},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> "RetryingJedisCommandsTestDelegate";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default -> null;
                                };
                            }
                            Invocation invocation = methods.get(method.getName());
                            if (invocation == null) {
                                throw new UnsupportedOperationException(method.getName());
                            }
                            return invocation.invoke(args);
                        });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args) throws Throwable;
    }
}
