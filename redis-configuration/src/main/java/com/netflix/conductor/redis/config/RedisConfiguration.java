/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.redis.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.metrics.Monitors;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Connection;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.util.Pool;

@Configuration(proxyBeanMethods = false)
@Slf4j
public abstract class RedisConfiguration {

    private final ScheduledExecutorService monitorExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "redis-pool-monitor");
                        t.setDaemon(true);
                        return t;
                    });

    private final List<Pool<Connection>> monitoredPools = new ArrayList<>();

    protected abstract UnifiedJedis createUnifiedJedis(RedisProperties properties);

    protected void monitorJedisPool(Pool<Connection> pool) {
        monitoredPools.add(pool);
        if (monitoredPools.size() == 1) {
            monitorExecutor.scheduleAtFixedRate(this::recordPoolMetrics, 10, 10, TimeUnit.SECONDS);
        }
    }

    private void recordPoolMetrics() {
        for (Pool<Connection> pool : monitoredPools) {
            try {
                int active = pool.getNumActive();
                int waiting = pool.getNumWaiters();
                Duration meanBorrowWaitTime = pool.getMeanBorrowWaitDuration();
                Duration maxBorrowWaitTime = pool.getMaxBorrowWaitDuration();

                log.debug("JedisPool Monitor, active = {}, waiting = {}", active, waiting);

                Monitors.recordGauge("conductor_redis_connection_active", active);
                Monitors.recordGauge("conductor_redis_connection_waiting", waiting);
                Monitors.getTimer("conductor_redis_connection_mean_borrow_wait_time")
                        .record(meanBorrowWaitTime);
                Monitors.getTimer("conductor_redis_connection_max_borrow_wait_time")
                        .record(maxBorrowWaitTime);
            } catch (Exception e) {
                log.trace("Failed to collect Redis pool metrics", e);
            }
        }
    }

    @PreDestroy
    void shutdownMonitor() {
        monitorExecutor.shutdownNow();
    }
}
