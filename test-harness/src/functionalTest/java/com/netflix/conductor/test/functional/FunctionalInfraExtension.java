/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.test.functional;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;

/**
 * JUnit 5 global extension that starts Redis + Elasticsearch containers and an embedded Conductor
 * server before any e2e test class runs.
 *
 * <p>Registered via META-INF/services for auto-detection. The infrastructure is started once per
 * JVM and shared across all test classes. {@code SERVER_ROOT_URI} is set as a system property so
 * that {@code io.conductor.e2e.util.ApiUtil} picks it up at class-load time.
 */
public class FunctionalInfraExtension
        implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger log = LoggerFactory.getLogger(FunctionalInfraExtension.class);
    private static final String STORE_KEY = "functional-infra";

    private static volatile boolean started = false;
    private static ConfigurableApplicationContext springContext;

    private static final ElasticsearchContainer esContainer =
            new ElasticsearchContainer(
                            DockerImageName.parse("elasticsearch").withTag("7.17.11"))
                    .withExposedPorts(9200, 9300)
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    @Override
    public synchronized void beforeAll(ExtensionContext context) throws Exception {
        if (!started) {
            // Register for cleanup when JUnit is done
            context.getRoot()
                    .getStore(ExtensionContext.Namespace.GLOBAL)
                    .put(STORE_KEY, this);

            startContainers();
            startConductorServer();
            started = true;
        }
    }

    private void startContainers() {
        esContainer.start();
        redisContainer.start();
        log.info(
                "Functional test containers started — ES: {}, Redis port: {}",
                esContainer.getHttpHostAddress(),
                redisContainer.getFirstMappedPort());

        // Set connection properties for Spring Boot to pick up
        System.setProperty(
                "conductor.elasticsearch.url",
                "http://" + esContainer.getHttpHostAddress());
        System.setProperty(
                "conductor.redis.hosts",
                "localhost:" + redisContainer.getFirstMappedPort() + ":us-east-1c");
        System.setProperty(
                "conductor.redis-lock.serverAddress",
                "redis://localhost:" + redisContainer.getFirstMappedPort());
    }

    private void startConductorServer() {
        SpringApplication app = new SpringApplication(ConductorTestApp.class);
        app.setAdditionalProfiles("functionaltest");
        springContext = app.run("--server.port=0");

        int port =
                springContext
                        .getEnvironment()
                        .getProperty("local.server.port", Integer.class, 0);
        String serverUrl = "http://localhost:" + port;
        System.setProperty("SERVER_ROOT_URI", serverUrl);
        log.info("Conductor server started at {}", serverUrl);
    }

    @Override
    public void close() throws Throwable {
        if (springContext != null) {
            log.info("Shutting down embedded Conductor server");
            springContext.close();
        }
    }
}
