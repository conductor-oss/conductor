package com.netflix.conductor.dyno;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;

import javax.inject.Inject;

public class SystemPropertiesDynomiteConfiguration extends SystemPropertiesConfiguration
        implements DynomiteConfiguration {

    private final Configuration configuration;

    @Inject
    public SystemPropertiesDynomiteConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getClusterName() {
        return configuration.getProperty(CLUSTER_NAME_PROPERTY_NAME, "");
    }

    @Override
    public String getHosts() {
        return configuration.getProperty(HOSTS_PROPERTY_NAME, null);
    }

    @Override
    public int getMaxConnectionsPerHost() {
        return configuration.getIntProperty(
                MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME,
                MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE
        );
    }
}
