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
package com.netflix.conductor.util;

import com.datastax.driver.core.Session;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedCassandra {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCassandra.class);

    public EmbeddedCassandra() throws Exception {
        LOGGER.info("Starting embedded cassandra");
        startEmbeddedCassandra();
    }

    private void startEmbeddedCassandra() throws Exception {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        } catch (Exception e) {
            LOGGER.error("Error starting embedded cassandra server", e);
            throw e;
        }
    }

    public Session getSession() {
        return EmbeddedCassandraServerHelper.getSession();
    }

    public void cleanupData() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
}
