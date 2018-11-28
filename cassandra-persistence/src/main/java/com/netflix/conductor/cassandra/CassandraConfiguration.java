/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.cassandra;

import com.netflix.conductor.core.config.Configuration;

public interface CassandraConfiguration extends Configuration {

    String CASSANDRA_HOST_ADDRESS_PROPERTY_NAME = "workflow.cassandra.host";
    String CASSANDRA_HOST_ADDRESS_DEFAULT_VALUE = "127.0.0.1";

    String CASSANDRA_PORT_PROPERTY_NAME = "workflow.cassandra.port";
    int CASSANDRA_PORT_DEFAULT_VALUE = 9142;

    String CASSANDRA_CLUSTER_PROPERTY_NAME = "workflow.cassandra.cluster";
    String CASSANDRA_CLUSTER_DEFAULT_VALUE = "";

    String CASSANDRA_KEYSPACE_PROPERTY_NAME = "workflow.cassandra.keyspace";
    String CASSANDRA_KEYSPACE_DEFAULT_VALUE = "conductor";

    String CASSANDRA_REPLICATION_STRATEGY_PROPERTY_NAME = "workflow.cassandra.replication.strategy";
    String CASSANDRA_REPLICATION_STRATEGY_DEFAULT_VALUE = "SimpleStrategy";

    String CASSANDRA_REPLICATION_FACTOR_KEY_PROPERTY_NAME = "workflow.cassandra.replication.factor.key";
    String CASSANDRA_REPLICATION_FACTOR_KEY_DEFAULT_VALUE = "replication_factor";

    String CASSANDRA_REPLICATION_FACTOR_VALUE_PROPERTY_NAME = "workflow.cassandra.replicaton.factor.value";
    int CASSANDRA_REPLICATION_FACTOR_VALUE_DEFAULT_VALUE = 3;

    String CASSANDRA_SHARD_SIZE_PROPERTY_KEY = "workflow.cassandra.shard.size";
    int CASSANDRA_SHARD_SIZE_DEFAULT_VALUE = 100;

    default String getHostAddress() {
        return getProperty(CASSANDRA_HOST_ADDRESS_PROPERTY_NAME, CASSANDRA_HOST_ADDRESS_DEFAULT_VALUE);
    }

    default int getPort() {
        return getIntProperty(CASSANDRA_PORT_PROPERTY_NAME, CASSANDRA_PORT_DEFAULT_VALUE);
    }

    default String getCassandraCluster() {
        return getProperty(CASSANDRA_CLUSTER_PROPERTY_NAME, CASSANDRA_CLUSTER_DEFAULT_VALUE);
    }

    default String getCassandraKeyspace() {
        return getProperty(CASSANDRA_KEYSPACE_PROPERTY_NAME, CASSANDRA_KEYSPACE_DEFAULT_VALUE);
    }

    default int getShardSize() {
        return getIntProperty(CASSANDRA_SHARD_SIZE_PROPERTY_KEY, CASSANDRA_SHARD_SIZE_DEFAULT_VALUE);
    }

    default String getReplicationStrategy() {
        return getProperty(CASSANDRA_REPLICATION_STRATEGY_PROPERTY_NAME, CASSANDRA_REPLICATION_STRATEGY_DEFAULT_VALUE);
    }

    default String getReplicationFactorKey() {
        return getProperty(CASSANDRA_REPLICATION_FACTOR_KEY_PROPERTY_NAME, CASSANDRA_REPLICATION_FACTOR_KEY_DEFAULT_VALUE);
    }

    default int getReplicationFactorValue() {
        return getIntProperty(CASSANDRA_REPLICATION_FACTOR_VALUE_PROPERTY_NAME, CASSANDRA_REPLICATION_FACTOR_VALUE_DEFAULT_VALUE);
    }
}
