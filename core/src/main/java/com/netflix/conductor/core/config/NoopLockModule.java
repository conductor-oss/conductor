package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.utils.Lock;
import com.netflix.conductor.core.utils.NoopLock;

public class NoopLockModule extends AbstractModule {

    @Override
    protected void configure() {}

    @Provides
    protected Lock provideLock() {
        return new NoopLock();
    }

}
