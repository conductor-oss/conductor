package com.netflix.conductor.core.utils;

import java.util.concurrent.TimeUnit;

public class NoopLock implements Lock {
    @Override
    public boolean acquireLock(String lockId) {
        return true; // Always acquire lock.
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return true;
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        return true;
    }

    @Override
    public boolean releaseLock(String lockId) {
        return true;
    }

    @Override
    public boolean deleteLock(String lockId) {
        return true;
    }
}
