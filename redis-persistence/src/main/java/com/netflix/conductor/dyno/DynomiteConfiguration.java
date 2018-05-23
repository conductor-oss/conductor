package com.netflix.conductor.dyno;

import com.netflix.conductor.core.config.Configuration;

public interface DynomiteConfiguration extends Configuration {
    String CLUSTER_NAME_PROPERTY_NAME = "workflow.dynomite.cluster.name";
    String HOSTS_PROPERTY_NAME = "workflow.dynomite.cluster.hosts";
    String MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME = "workflow.dynomite.connection.maxConnsPerHost";
    int MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE = 10;

    String getClusterName();

    String getHosts();

    int getMaxConnectionsPerHost();
}
