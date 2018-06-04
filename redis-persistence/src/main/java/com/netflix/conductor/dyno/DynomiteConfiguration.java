package com.netflix.conductor.dyno;

import com.netflix.conductor.core.config.Configuration;

public interface DynomiteConfiguration extends Configuration {
    // FIXME Are cluster and cluster name really different things?
    String CLUSTER_PROPERTY_NAME = "workflow.dynomite.cluster";
    String CLUSTER_DEFAULT_VALUE = null;

    String CLUSTER_NAME_PROPERTY_NAME = "workflow.dynomite.cluster.name";
    String HOSTS_PROPERTY_NAME = "workflow.dynomite.cluster.hosts";

    String MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME = "workflow.dynomite.connection.maxConnsPerHost";
    int MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE = 10;

    String ROOT_NAMESPACE_PROPERTY_NAME = "workflow.namespace.queue.prefix";
    String ROOT_NAMESPACE_DEFAULT_VALUE = null;

    String DOMAIN_PROPERTY_NAME = "workflow.dyno.keyspace.domain";
    String DOMAIN_DEFAULT_VALUE = null;

    String NON_QUORUM_PORT_PROPERTY_NAME = "queues.dynomite.nonQuorum.port";
    int NON_QUORUM_PORT_DEFAULT_VALUE = 22122;

    default String getCluster() {
        return getProperty(CLUSTER_PROPERTY_NAME, CLUSTER_DEFAULT_VALUE);
    }

    default String getClusterName() {
        return getProperty(CLUSTER_NAME_PROPERTY_NAME, "");
    }

    default String getHosts() {
        return getProperty(HOSTS_PROPERTY_NAME, null);
    }

    default String getRootNamespace() {
        return getProperty(ROOT_NAMESPACE_PROPERTY_NAME, ROOT_NAMESPACE_DEFAULT_VALUE);
    }

    default String getDomain() {
        return getProperty(DOMAIN_PROPERTY_NAME, DOMAIN_DEFAULT_VALUE);
    }

    default int getMaxConnectionsPerHost() {
        return getIntProperty(
                MAX_CONNECTIONS_PER_HOST_PROPERTY_NAME,
                MAX_CONNECTIONS_PER_HOST_DEFAULT_VALUE
        );
    }

    default int getNonQuorumPort() {
        return getIntProperty(NON_QUORUM_PORT_PROPERTY_NAME, NON_QUORUM_PORT_DEFAULT_VALUE);
    }

    default String getQueuePrefix() {
        String prefix = getRootNamespace() + "." + getStack();

        if (getDomain() != null) {
            prefix = prefix + "." + getDomain();
        }

        return prefix;
    }
}
