/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.tests.integration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.conductor.bootstrap.BootstrapModule;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.ElasticSearchRestClientProvider;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.elasticsearch.SystemPropertiesElasticSearchConfiguration;
import com.netflix.conductor.jetty.server.JettyServer;
import com.netflix.conductor.tests.utils.TestEnvironment;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 */
public class ESRestClientHttpEndToEndTest extends AbstractHttpEndToEndTest {

    private static final Logger logger =
        LoggerFactory.getLogger(ESRestClientHttpEndToEndTest.class);

    private static final int SERVER_PORT = 8083;

    private static RestClient elasticSearchAdminClient;

    @BeforeClass
    public static void setup() throws Exception {
        TestEnvironment.setup();
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9203");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "http://localhost:9203");

        Injector bootInjector = Guice.createInjector(new BootstrapModule());
        Injector serverInjector = Guice.createInjector(bootInjector.getInstance(ModulesProvider.class).get());

        search = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get().get();
        search.start();

        SystemPropertiesElasticSearchConfiguration configuration = new SystemPropertiesElasticSearchConfiguration();
        ElasticSearchRestClientProvider restClientProvider = new ElasticSearchRestClientProvider(configuration);
        elasticSearchAdminClient = restClientProvider.get();

        waitForGreenCluster();

        JettyServer server = new JettyServer(SERVER_PORT, false);
        server.start();

        apiRoot = String.format("http://localhost:%d/api/", SERVER_PORT);

        taskClient = new TaskClient();
        taskClient.setRootURI(apiRoot);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);
    }

    @AfterClass
    public static void teardown() throws Exception {
        TestEnvironment.teardown();
        search.stop();
    }

    private static void waitForGreenCluster() throws Exception {
        long startTime = System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("wait_for_status", "green");
        params.put("timeout", "30s");

        elasticSearchAdminClient.performRequest("GET", "/_cluster/health", params);
        logger.info("Elasticsearch Cluster ready in {} ms", System.currentTimeMillis() - startTime);
    }

}
