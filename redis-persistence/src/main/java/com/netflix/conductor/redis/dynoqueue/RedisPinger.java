/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.redis.dynoqueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import com.netflix.dyno.connectionpool.Host;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisPinger {

    private static final Logger logger = LoggerFactory.getLogger(RedisPinger.class);
    private static final int CONNECTION_TIMEOUT = 3000;

    private static int MAX_RETRY_COUNT = 3;

    private Integer waitObject = Integer.valueOf(1);

    /**
     * Does a redis ping to the host. If pings returns true, this function returns true. If ping
     * fails 2 more times ping will be done. Time delay between each ping is decided with
     * exponential backoff algorithm.* If fails in all retries returns false. If succeeds in any of
     * the ping, then returns true
     *
     * @param host
     * @return boolean ping result
     */
    public boolean pingWithRetry(Host host) {
        ExponentialBackOff backOff = new ExponentialBackOff(3000, 1.5);
        BackOffExecution backOffExecution = backOff.start();
        int retryCount = 0;
        while (retryCount < MAX_RETRY_COUNT) {
            retryCount = retryCount + 1;
            boolean pingResponse = ping(host);
            if (pingResponse) {
                return pingResponse;
            } else {
                try {
                    if (retryCount < MAX_RETRY_COUNT) {
                        synchronized (waitObject) {
                            long waitTime = backOffExecution.nextBackOff();
                            waitObject.wait(waitTime);
                        }
                    }
                } catch (Exception ee) {
                    logger.error("Error on waiting for retry ", ee);
                }
                continue;
            }
        }
        return false;
    }

    /**
     * Direct redis ping with no retries. Returns true if redis ping returns true false otherwise.
     *
     * @param host
     * @return boolean ping result
     */
    public boolean ping(Host host) {
        JedisPool jedisPool = null;
        Jedis jedis = null;
        try {
            JedisPoolConfig config = new JedisPoolConfig();
            // set the number of connections to 1 to limit resource usage
            config.setMinIdle(1);
            config.setMaxTotal(1);
            if (host.getPassword() == null) {
                jedisPool =
                        new JedisPool(
                                config, host.getHostName(), host.getPort(), CONNECTION_TIMEOUT);
            } else {
                jedisPool =
                        new JedisPool(
                                config,
                                host.getHostName(),
                                host.getPort(),
                                CONNECTION_TIMEOUT,
                                host.getPassword());
            }
            // in case of connection problem it getResource() method throws exception
            jedis = jedisPool.getResource();
            String pingResponse = jedis.ping();
            // should return pong in case of successful redis connection
            if ("PONG".equalsIgnoreCase(pingResponse)) {
                return true;
            } else {
                logger.error("Ping failed for host {} pingResponse {}", host, pingResponse);
                return false;
            }
        } catch (Exception ee) {
            logger.error("Error while pinging dynomite host {}", host, ee);
            return false;
        } finally {
            if (jedisPool != null) {
                jedisPool.close();
            }
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
