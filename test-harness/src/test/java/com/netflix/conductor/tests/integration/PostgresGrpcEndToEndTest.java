package com.netflix.conductor.tests.integration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.conductor.bootstrap.BootstrapModule;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.client.grpc.MetadataClient;
import com.netflix.conductor.client.grpc.TaskClient;
import com.netflix.conductor.client.grpc.WorkflowClient;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.grpc.server.GRPCServer;
import com.netflix.conductor.grpc.server.GRPCServerProvider;
import com.netflix.conductor.tests.utils.TestEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Optional;

import static com.netflix.conductor.core.config.Configuration.DB_PROPERTY_NAME;
import static com.netflix.conductor.elasticsearch.ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME;
import static com.netflix.conductor.elasticsearch.ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME;
import static com.netflix.conductor.grpc.server.GRPCServerConfiguration.ENABLED_PROPERTY_NAME;
import static com.netflix.conductor.grpc.server.GRPCServerConfiguration.PORT_PROPERTY_NAME;
import static com.netflix.conductor.postgres.PostgresConfiguration.CONNECTION_POOL_MAX_SIZE_PROPERTY_NAME;
import static com.netflix.conductor.postgres.PostgresConfiguration.CONNECTION_POOL_MINIMUM_IDLE_PROPERTY_NAME;
import static com.netflix.conductor.postgres.PostgresConfiguration.JDBC_PASSWORD_PROPERTY_NAME;
import static com.netflix.conductor.postgres.PostgresConfiguration.JDBC_URL_PROPERTY_NAME;
import static com.netflix.conductor.postgres.PostgresConfiguration.JDBC_USER_NAME_PROPERTY_NAME;
import static org.junit.Assert.assertTrue;

public class PostgresGrpcEndToEndTest extends AbstractGrpcEndToEndTest {

    private static final int SERVER_PORT = 8098;
    protected static Optional<GRPCServer> server;

    @BeforeClass
    public static void setup() throws Exception {
        TestEnvironment.setup();

        System.setProperty("workflow.namespace.prefix", "conductor" + System.getProperty("user.name"));
        System.setProperty(DB_PROPERTY_NAME, "postgres");
        System.setProperty(ENABLED_PROPERTY_NAME, "true");
        System.setProperty(PORT_PROPERTY_NAME, "8098");
        System.setProperty(EMBEDDED_PORT_PROPERTY_NAME, "9208");
        System.setProperty(ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9308");
        System.setProperty(JDBC_URL_PROPERTY_NAME, "jdbc:postgresql://localhost:54320/conductor");
        System.setProperty(JDBC_USER_NAME_PROPERTY_NAME, "postgres");
        System.setProperty(JDBC_PASSWORD_PROPERTY_NAME, "postgres");

        System.setProperty(CONNECTION_POOL_MINIMUM_IDLE_PROPERTY_NAME, "8");
        System.setProperty(CONNECTION_POOL_MAX_SIZE_PROPERTY_NAME, "8");
        System.setProperty(CONNECTION_POOL_MINIMUM_IDLE_PROPERTY_NAME, "300000");

        Injector bootInjector = Guice.createInjector(new BootstrapModule());
        Injector serverInjector = Guice.createInjector(bootInjector.getInstance(ModulesProvider.class).get());

        search = serverInjector.getInstance(EmbeddedElasticSearchProvider .class).get().get();
        search.start();

        server = serverInjector.getInstance(GRPCServerProvider.class).get();
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
        server.ifPresent(GRPCServer::stop);
    }

}