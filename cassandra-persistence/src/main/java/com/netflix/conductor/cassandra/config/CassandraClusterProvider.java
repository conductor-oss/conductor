/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.cassandra.config;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;

public class CassandraClusterProvider implements Provider<Cluster> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraClusterProvider.class);
    private final CassandraProperties properties;

    public CassandraClusterProvider(CassandraProperties properties) {
        this.properties = properties;
    }

    @Override
    public Cluster get() {
        String host = properties.getHostAddress();
        int port = properties.getPort();

        LOGGER.info("Connecting to cassandra cluster with host:{}, port:{}", host, port);

        Cluster cluster = Cluster.builder()
            .addContactPoint(host)
            .withPort(port)
            .build();

        Metadata metadata = cluster.getMetadata();
        LOGGER.info("Connected to cluster: {}", metadata.getClusterName());
        metadata.getAllHosts().forEach(h -> {
            LOGGER.info("Datacenter:{}, host:{}, rack: {}", h.getDatacenter(), h.getAddress(), h.getRack());
        });
        return cluster;
    }
}
