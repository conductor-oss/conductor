/**
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
/**
 *
 */
package com.netflix.conductor.tests.integration;

import static org.junit.Assert.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.conductor.bootstrap.BootstrapModule;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.grpc.server.GRPCServer;
import com.netflix.conductor.grpc.server.GRPCServerConfiguration;
import com.netflix.conductor.grpc.server.GRPCServerProvider;
import com.netflix.conductor.mysql.MySQLConfiguration;
import com.netflix.conductor.tests.utils.MySQLTestModule;
import com.netflix.conductor.tests.utils.MySQLTestRunner;
import com.netflix.conductor.tests.utils.TestEnvironment;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * @author Viren
 */
@RunWith(MySQLTestRunner.class)
public class MySQLGrpcEndToEndTest extends AbstractGrpcEndToEndTest {

    private static final int SERVER_PORT = 8094;

    @BeforeClass
    public static void setup() throws Exception {
        TestEnvironment.setup();
        System.setProperty(GRPCServerConfiguration.ENABLED_PROPERTY_NAME, "true");
        System.setProperty(GRPCServerConfiguration.PORT_PROPERTY_NAME, "8094");
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9204");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9304");

        Injector bootInjector = Guice.createInjector(new BootstrapModule());
        Injector serverInjector = Guice.createInjector(bootInjector.getInstance(ModulesProvider.class).get());

        search = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get().get();
        search.start();

        Optional<GRPCServer> server = serverInjector.getInstance(GRPCServerProvider.class).get();
        assertTrue("failed to instantiate GRPCServer", server.isPresent());
        server.get().start();

        taskClient = new TaskClient("localhost", SERVER_PORT);
        workflowClient = new WorkflowClient("localhost", SERVER_PORT);
        metadataClient = new MetadataClient("localhost", SERVER_PORT);
    }

    @AfterClass
    public static void teardown() throws Exception {
        TestEnvironment.teardown();
        search.stop();
    }

}
