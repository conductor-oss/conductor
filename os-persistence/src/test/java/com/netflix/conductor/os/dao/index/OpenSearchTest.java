/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.os.dao.index;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.os.config.OpenSearchProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

@ContextConfiguration(
        classes = {TestObjectMapperConfiguration.class, OpenSearchTest.TestConfiguration.class})
@RunWith(SpringRunner.class)
@TestPropertySource(
        properties = {"conductor.indexing.enabled=true", "conductor.elasticsearch.version=0"})
public abstract class OpenSearchTest {

    @Configuration
    static class TestConfiguration {

        @Bean
        public OpenSearchProperties elasticSearchProperties() {
            return new OpenSearchProperties();
        }
    }

    protected static OpensearchContainer<?> container =
            new OpensearchContainer<>(
                    DockerImageName.parse(
                            "opensearchproject/opensearch:2.18.0")); // this should match the client
    // version

    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected OpenSearchProperties properties;

    @BeforeClass
    public static void startServer() {
        container.start();
    }

    @AfterClass
    public static void stopServer() {
        container.stop();
    }
}
