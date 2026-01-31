/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.vectordb;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.models.IndexedDoc;
import org.conductoross.conductor.ai.models.StoreEmbeddingsInput;
import org.conductoross.conductor.ai.models.VectorDBInput;
import org.conductoross.conductor.ai.tasks.worker.VectorDBWorkers;
import org.conductoross.conductor.ai.vectordb.postgres.PostgresConfig;
import org.conductoross.conductor.ai.vectordb.postgres.PostgresVectorDB;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        properties = {
            "conductor.integrations.enabled=true",
            "conductor.integrations.ai.enabled=true"
        },
        classes = {TestConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgresVectorDBTest {

    public PostgreSQLContainer<?> postgreSQLContainer;
    private HikariDataSource dataSource;

    private VectorDBWorkers aiWorkers;

    private void createExtensionIfNotExists(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            Statement setupStmt = conn.createStatement();
            setupStmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @BeforeEach
    public void setup() {
        postgreSQLContainer =
                new PostgreSQLContainer<>(
                                DockerImageName.parse("pgvector/pgvector:pg16")
                                        .asCompatibleSubstituteFor("postgres"))
                        .withDatabaseName("conductor");
        postgreSQLContainer.start();
        this.dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgreSQLContainer.getJdbcUrl());
        dataSource.setUsername(postgreSQLContainer.getUsername());
        dataSource.setPassword(postgreSQLContainer.getPassword());
        dataSource.setAutoCommit(true);
        dataSource.setMaximumPoolSize(8);

        createExtensionIfNotExists(dataSource);
        AIModelProvider provider = new AIModelProvider(List.of(), new StandardEnvironment());

        // Create PostgresConfig with test database settings
        PostgresConfig postgresConfig = new PostgresConfig();
        postgresConfig.setDatasourceURL(dataSource.getJdbcUrl());
        postgresConfig.setUser(dataSource.getUsername());
        postgresConfig.setPassword(dataSource.getPassword());
        postgresConfig.setConnectionPoolSize(8);
        postgresConfig.setDimensions(3);
        postgresConfig.setIndexingMethod("hnsw");
        postgresConfig.setDistanceMetric("euclidean");

        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        LLMs llm = new LLMs(null, new JsonSchemaValidator(objectMapper), provider);

        // Create VectorDB instance and wrap it in VectorDBConfig
        PostgresVectorDB postgresVectorDB = new PostgresVectorDB(postgresConfig);
        VectorDBConfig<VectorDB> vectorDBConfig = () -> postgresVectorDB;

        VectorDBProvider vectorDBProvider = new VectorDBProvider(List.of(vectorDBConfig));
        VectorDBs vectorDBs = new VectorDBs(vectorDBProvider);
        aiWorkers = new VectorDBWorkers(vectorDBs, llm);
    }

    private boolean resourceCreated(DataSource dataSource, String resource, String resourceName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables =
                    metaData.getTables(null, null, resourceName, new String[] {resource});
            return tables.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private StoreEmbeddingsInput getMockStoreEmbeddingsInput(List<Float> embeddings) {
        StoreEmbeddingsInput storeEmbeddingsInput = new StoreEmbeddingsInput();
        storeEmbeddingsInput.setVectorDB("pgvectordb");
        storeEmbeddingsInput.setId(UUID.randomUUID().toString());
        storeEmbeddingsInput.setIndex("testindex");
        storeEmbeddingsInput.setMetadata(Map.of("key1", "val1"));
        storeEmbeddingsInput.setNamespace("items");
        storeEmbeddingsInput.setMaxResults(4);
        storeEmbeddingsInput.setEmbeddings(embeddings);
        return storeEmbeddingsInput;
    }

    private VectorDBInput getMockSVectorDBInput() {
        VectorDBInput vectorDBInput = new VectorDBInput();
        vectorDBInput.setVectorDB("pgvectordb");
        vectorDBInput.setIndex("testindex");
        vectorDBInput.setNamespace("items");
        vectorDBInput.setMetadata(Map.of("key1", "val1"));
        vectorDBInput.setDimensions(3);
        vectorDBInput.setEmbeddings(List.of(1.0f, 2.001f, 3.03f));
        vectorDBInput.setMaxResults(3);
        return vectorDBInput;
    }

    @Test
    public void testUpdateEmbeddingsInvalidNamespace() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        TaskContext.TASK_CONTEXT_INHERITABLE_THREAD_LOCAL.set(
                new TaskContext(task, new TaskResult()));

        StoreEmbeddingsInput storeEmbeddingsInput =
                getMockStoreEmbeddingsInput(List.of(1.1f, 2.2f, 3.4f));
        try {
            aiWorkers.storeEmbeddings(storeEmbeddingsInput);
        } catch (RuntimeException applicationException) {
            assertEquals("Invalid namespace", applicationException.getMessage());
        }
    }

    @Test
    public void testUpdateEmbeddings() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        TaskContext.TASK_CONTEXT_INHERITABLE_THREAD_LOCAL.set(
                new TaskContext(task, new TaskResult()));

        StoreEmbeddingsInput storeEmbeddingsInput =
                getMockStoreEmbeddingsInput(List.of(1.1f, 2.2f, 3.4f));
        int result = aiWorkers.storeEmbeddings(storeEmbeddingsInput);
        assertTrue(result != 0);
        assertTrue(resourceCreated(dataSource, "TABLE", "items"));
        assertTrue(resourceCreated(dataSource, "INDEX", "testindex"));
    }

    @Test
    public void testSearchEmbeddings() {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        TaskContext.TASK_CONTEXT_INHERITABLE_THREAD_LOCAL.set(
                new TaskContext(task, new TaskResult()));

        StoreEmbeddingsInput storeEmbeddingsInput =
                getMockStoreEmbeddingsInput(List.of(1.1f, 2.2f, 3.4f));
        aiWorkers.storeEmbeddings(storeEmbeddingsInput);
        StoreEmbeddingsInput storeEmbeddingsInput2 =
                getMockStoreEmbeddingsInput(List.of(1.001f, 0.001f, 3.335f));
        aiWorkers.storeEmbeddings(storeEmbeddingsInput2);
        List<IndexedDoc> indexedDocs = aiWorkers.searchUsingEmbeddings(getMockSVectorDBInput());
        assertEquals(2, indexedDocs.size());
    }
}
