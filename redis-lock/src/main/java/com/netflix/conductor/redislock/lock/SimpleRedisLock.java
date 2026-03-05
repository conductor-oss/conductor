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
package com.netflix.conductor.redislock.lock;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.sync.Lock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SimpleRedisLock implements Lock {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleRedisLock.class);

    private ObjectMapper objectMapper = new ObjectMapper();
    private ExecutionDAOFacade facade = null;
    private ConductorProperties properties = null;

    // this thread local has the lockId. If the same thread is asking for the lock, this thread
    // local has the lockid and returns true with out
    // going to redis
    private ThreadLocal<AtomicInteger> reentrantThreadLocal = new ThreadLocal();

    private Integer waitObject = Integer.valueOf(1);

    public SimpleRedisLock(ExecutionDAOFacade facade, ConductorProperties properties) {
        this.facade = facade;
        this.properties = properties;
        LOGGER.info("SimpleRedisLock is configured and initialized");
    }

    /**
     * acquireLock for the lockId with lockTimeToTry configured in properties
     *
     * @param lockId resource to lock on
     */
    public void acquireLock(String lockId) {
        acquireLock(lockId, properties.getLockTimeToTry().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * acquireLock acquireLock with the given lockId and timeToTry in TimeUnit
     *
     * @param lockId resource to lock on
     * @param timeToTry blocks up to timeToTry duration in attempt to acquire the lock
     * @param unit time unit
     * @return
     */
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return acquireLock(
                lockId, timeToTry, properties.getLockLeaseTime().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * acquireLock with the given lockId, timeToTry and leaseTime. Till timeToTry, for each 100
     * milliseconds, it tries to acquire the lock. It holds the lock for a max of leaseTime unless
     * otherwise someone releases/deletes the lock
     *
     * @param lockId resource to lock on
     * @param timeToTry blocks up to timeToTry duration in attempt to acquire the lock
     * @param leaseTime Lock lease expiration duration.
     * @param unit time unit
     * @return
     */
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        // if thread local is having the value, then same thread is requiring the lock second time.
        // return true for that.
        AtomicInteger lockCount = reentrantThreadLocal.get();
        if (lockCount != null) {
            lockCount.set(lockCount.get() + 1);
            return true;
        }
        int leaseTimeInSeconds = getLeaseTimeInSeconds(leaseTime, unit);
        String lockValue = getLockValue(lockId);
        boolean redisLock = tryLock(lockId, lockValue, timeToTry, leaseTimeInSeconds);
        if (redisLock) {
            // if lock is acquired then update the thread local so that it will be useful for
            // subsequent requests from the same thread.
            lockCount = new AtomicInteger();
            lockCount.set(1);
            reentrantThreadLocal.set(lockCount);
        }
        return redisLock;
    }

    /**
     * converts TimeUnit leaseTime to leaseTimeInSeconds as redis accepts leasetime in seconds
     *
     * @param leaseTime
     * @param unit
     * @return
     */
    private int getLeaseTimeInSeconds(long leaseTime, TimeUnit unit) {
        int leaseTimeSeconds = 0;
        if (unit.equals(TimeUnit.SECONDS)) {
            leaseTimeSeconds = (int) leaseTime;
        } else if (unit.equals(TimeUnit.MILLISECONDS)) {
            leaseTimeSeconds = (int) leaseTime / 1000;
        } else {
            throw new IllegalArgumentException(
                    "TimeUnit needs to be either SECONDS or MILLISECONDS");
        }
        return leaseTimeSeconds;
    }

    /** Gets the lock value in json. currently only startTime is added. */
    private String getLockValue(String lockId) {
        String value = "{}";
        try {
            ObjectNode valueNode = objectMapper.createObjectNode();
            long currentTime = System.currentTimeMillis();
            valueNode.put("lockStartTime", new Date(currentTime).toString());
            valueNode.put("lockStartTimeMillis", currentTime);

            value = objectMapper.writeValueAsString(valueNode);
        } catch (Exception ee) {
            LOGGER.error("Error while serializing value node {}", lockId, ee);
        }
        return value;
    }

    /**
     * Tries to get the lock till timeToTry. If it gets the lock in any of the try, it returns
     * immediately. Else it tries till timeToTry. Returns false once the time reaches beyond
     * timeToTry.
     *
     * @param lockId
     * @param lockValue
     * @param timeToTry
     * @param leaseTimeSeconds
     * @return
     */
    private boolean tryLock(String lockId, String lockValue, long timeToTry, int leaseTimeSeconds) {
        long tryStartTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - tryStartTime) <= timeToTry) {
            String result = facade.addLock(lockId, lockValue, leaseTimeSeconds);
            if ("OK".equals(result)) {
                return true;
            } else {
                synchronized (waitObject) {
                    try {
                        waitObject.wait(50);
                    } catch (Exception ee) {
                        LOGGER.error("Error while waiting for lock {}", lockId, ee);
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * releases the lock immediately. deletes the lock entry in redis
     *
     * @param lockId resource to lock on
     */
    public void releaseLock(String lockId) {
        AtomicInteger lockCount = reentrantThreadLocal.get();
        if (lockCount != null) {
            lockCount.set(lockCount.get() - 1);
            if (lockCount.get() <= 0) {
                facade.removeLock(lockId);
                reentrantThreadLocal.remove();
            } else {
                return;
            }
        } else {
            facade.removeLock(lockId);
        }
    }

    /**
     * releases the lock immediately
     *
     * @param lockId resource to lock on
     */
    public void deleteLock(String lockId) {
        releaseLock(lockId);
    }
}
