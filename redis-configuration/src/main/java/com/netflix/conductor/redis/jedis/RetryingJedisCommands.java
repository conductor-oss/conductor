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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Set;

import com.netflix.conductor.redis.config.RedisProperties;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Adds bounded retries for transient Redis failover errors while preserving the existing {@link
 * JedisCommands} contract.
 */
@Slf4j
public final class RetryingJedisCommands implements InvocationHandler {

    private static final Set<String> RETRYABLE_MESSAGES =
            Set.of("READONLY", "MASTERDOWN", "TRYAGAIN", "LOADING");

    private static final long RETRY_DELAY_MILLIS = 100L;

    private final JedisCommands delegate;
    private final int maxRetryAttempts;

    private RetryingJedisCommands(JedisCommands delegate, int maxRetryAttempts) {
        this.delegate = delegate;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public static JedisCommands wrap(JedisCommands delegate, RedisProperties redisProperties) {
        int maxRetryAttempts = redisProperties.getMaxRetryAttempts();
        if (maxRetryAttempts <= 0) {
            return delegate;
        }
        return (JedisCommands)
                Proxy.newProxyInstance(
                        JedisCommands.class.getClassLoader(),
                        new Class<?>[] {JedisCommands.class},
                        new RetryingJedisCommands(delegate, maxRetryAttempts));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(delegate, args);
        }

        int attempt = 0;
        while (true) {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                RuntimeException failure = toRuntimeException(e);
                if (!isRetryable(failure) || attempt >= maxRetryAttempts) {
                    throw failure;
                }

                attempt++;
                log.debug(
                        "Retrying Redis command {} after transient failover error (attempt {}/{})",
                        method.getName(),
                        attempt,
                        maxRetryAttempts,
                        failure);
                sleepBeforeRetry(failure);
            }
        }
    }

    static boolean isRetryable(RuntimeException exception) {
        if (exception instanceof JedisConnectionException) {
            return true;
        }
        if (!(exception instanceof JedisDataException)) {
            return false;
        }

        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toUpperCase(Locale.ROOT);
        return RETRYABLE_MESSAGES.stream().anyMatch(normalized::contains);
    }

    private static RuntimeException toRuntimeException(InvocationTargetException exception) {
        Throwable cause = exception.getTargetException();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(
                "Unexpected checked exception invoking JedisCommands", cause);
    }

    private static void sleepBeforeRetry(RuntimeException failure) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }
}
