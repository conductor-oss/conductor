package com.netflix.conductor.bootstrap;

import com.google.inject.AbstractModule;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;

public class BootstrapModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Configuration.class).to(SystemPropertiesConfiguration.class);
    }
}
