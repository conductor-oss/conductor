package com.netflix.conductor.locking.redis;

import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.config.RedisLockConfiguration;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class RedisLock implements Lock {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLock.class);

    private Config config;
    private RedissonClient redisson;
    private final int connectionTimeout = 10000;
    private static String LOCK_NAMESPACE = "";

    @Inject
    public RedisLock(RedisLockConfiguration configuration) {
        LOCK_NAMESPACE = configuration.getProperty("decider.locking.namespace", "");
        RedisLockConfiguration.REDIS_SERVER_TYPE redisServerType = configuration.getRedisServerType();
        try {
            redisServerType = configuration.getRedisServerType();
        } catch (IllegalArgumentException ie) {
            final String message = "Invalid Redis server type: " + configuration.getRedisServerType()
                    + ", supported values are: " + Arrays.toString(configuration.getRedisServerType().values());
            LOGGER.error(message);
            throw new ProvisionException(message, ie);
        }
        String redisServerAddress = configuration.getRedisServerAddress();

        config = new Config();

        switch (redisServerType) {
            case SINGLE:
                config.useSingleServer().setAddress(redisServerAddress).setTimeout(connectionTimeout);
                break;
            case CLUSTER:
                config.useClusterServers().addNodeAddress(redisServerAddress).setTimeout(connectionTimeout);
                break;
            case SENTINEL:
                config.useSentinelServers().addSentinelAddress(redisServerAddress).setTimeout(connectionTimeout);
                break;
        }

        redisson = Redisson.create(config);
    }

    @Override
    public void acquireLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        lock.lock();
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, unit);
        } catch (Exception e) {
            LOGGER.error("Failed in acquireLock: ", e);
            Monitors.recordAcquireLockFailure(e.getClass().getName());
        }
        return false;
    }

    /**
     *
     * @param lockId resource to lock on
     * @param timeToTry blocks up to timeToTry duration in attempt to acquire the lock
     * @param leaseTime Lock lease expiration duration. Redisson default is -1, meaning it holds the lock until explicitly unlocked.
     * @param unit time unit
     * @return
     */
    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, leaseTime, unit);
        } catch (Exception e) {
            LOGGER.error("Failed in acquireLock: ", e);
            Monitors.recordAcquireLockFailure(e.getClass().getName());
        }
        return false;
    }

    @Override
    public void releaseLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            // Releasing a lock twice using Redisson can cause this exception, which can be ignored.
        }
    }

    /**
     * Redlock deletes the lock on release.
     * @param lockId
     * @return
     */
    @Override
    public void deleteLock(String lockId) {
        // Noop for Redlock algorithm as releaseLock / unlock deletes it.
    }

    private String parseLockId(String lockId) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        return LOCK_NAMESPACE + "." + lockId;
    }
}
