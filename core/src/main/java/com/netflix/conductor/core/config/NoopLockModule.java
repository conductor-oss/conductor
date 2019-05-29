package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.core.utils.NoopLock;

import javax.inject.Provider;

public class NoopLockModule extends AbstractModule {

    private static class LockProvider implements Provider<Lock> {

        @Override
        public Lock get() {
            return new NoopLock();
        }
    }

    @Override
    protected void configure() {
        bind(Lock.class)
                .annotatedWith(Names.named("executionLock"))
                .toProvider(LockProvider.class);
    }

}
