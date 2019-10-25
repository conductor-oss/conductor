package com.netflix.conductor.locking.redis;

import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.locking.redis.config.RedisLockConfiguration;
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
    private static String LOCK_LEASE_TIME;

    @Inject
    public RedisLock(RedisLockConfiguration configuration) {
        LOCK_NAMESPACE = configuration.getProperty("decider.locking.namespace", "");
        LOCK_LEASE_TIME = configuration.getProperty("locking.leaseTimeInSeconds", "60");
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
    public boolean acquireLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        return lock.tryLock();
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, LOCK_LEASE_TIME, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        try {
            return lock.tryLock(timeToTry, leaseTime, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean releaseLock(String lockId) {
        RLock lock = redisson.getLock(parseLockId(lockId));
        return lock.forceUnlock();
    }

    /**
     * Redlock deletes the lock on release.
     * @param lockId
     * @return
     */
    @Override
    public boolean deleteLock(String lockId) {
        return true;
    }

    private String parseLockId(String lockId) {
        if (StringUtils.isEmpty(lockId)) {
            throw new IllegalArgumentException("lockId cannot be NULL or empty: lockId=" + lockId);
        }
        return LOCK_NAMESPACE + "." + lockId;
    }
}
