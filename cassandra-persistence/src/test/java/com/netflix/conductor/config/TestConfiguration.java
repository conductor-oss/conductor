/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.config;

import com.netflix.conductor.cassandra.CassandraConfiguration;

import java.util.Map;

public class TestConfiguration implements CassandraConfiguration {

    @Override
    public int getSweepFrequency() {
        return 1;
    }

    @Override
    public boolean disableSweep() {
        return false;
    }

    @Override
    public boolean disableAsyncWorkers() {
        return false;
    }

    @Override
    public String getServerId() {
        return "server_id";
    }

    @Override
    public String getEnvironment() {
        return "test";
    }

    @Override
    public String getStack() {
        return "junit";
    }

    @Override
    public String getAppId() {
        return "conductor";
    }

    @Override
    public String getRegion() {
        return "us-east-1";
    }

    @Override
    public String getAvailabilityZone() {
        return "us-east-1c";
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        return "test";
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return 0;
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return false;
    }

    @Override
    public Map<String, Object> getAll() {
        return null;
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return 1000000L;
    }

    @Override
    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return 5120L;
    }

    @Override
    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return 5120L;
    }

    @Override
    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getTaskInputPayloadSizeThresholdKB() {
        return 3072L;
    }

    @Override
    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getTaskOutputPayloadSizeThresholdKB() {
        return 3072L;
    }

    @Override
    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public String getHostAddress() {
        return CASSANDRA_HOST_ADDRESS_DEFAULT_VALUE;
    }

    @Override
    public int getPort() {
        return CASSANDRA_PORT_DEFAULT_VALUE;
    }

    @Override
    public String getCassandraKeyspace() {
        return "junit";
    }

    @Override
    public String getReplicationStrategy() {
        return CASSANDRA_REPLICATION_STRATEGY_DEFAULT_VALUE;
    }

    @Override
    public String getReplicationFactorKey() {
        return CASSANDRA_REPLICATION_FACTOR_KEY_DEFAULT_VALUE;
    }

    @Override
    public int getReplicationFactorValue() {
        return CASSANDRA_REPLICATION_FACTOR_VALUE_DEFAULT_VALUE;
    }
}
