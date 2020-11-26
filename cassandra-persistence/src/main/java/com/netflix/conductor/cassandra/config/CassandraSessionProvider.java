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
import com.datastax.driver.core.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;

public class CassandraSessionProvider implements Provider<Session> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraSessionProvider.class);

    private final Cluster cluster;

    public CassandraSessionProvider(Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public Session get() {
        LOGGER.info("Initializing cassandra session");
        return cluster.connect();
    }
}
