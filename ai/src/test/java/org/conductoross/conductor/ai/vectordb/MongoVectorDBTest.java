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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.models.StoreEmbeddingsInput;
import org.conductoross.conductor.ai.tasks.worker.VectorDBWorkers;
import org.conductoross.conductor.ai.vectordb.mongodb.MongoDBConfig;
import org.conductoross.conductor.ai.vectordb.mongodb.MongoVectorDB;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.MongoDBContainer;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        properties = {"conductor.integrations.ai.enabled=true"},
        classes = {TestConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MongoVectorDBTest {

    private static MongoDBContainer mongoDBContainer;
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private static VectorDBWorkers aiWorkers;
    private static final String DATABASE_NAME = "test-database";

    @BeforeAll
    public static void setup() {
        mongoDBContainer = new MongoDBContainer("mongo:7.0").withSharding();
        mongoDBContainer.start();

        CodecRegistry pojoCodecRegistry =
                CodecRegistries.fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        CodecRegistries.fromProviders(
                                PojoCodecProvider.builder().automatic(true).build()));

        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(
                                new ConnectionString(mongoDBContainer.getConnectionString()))
                        .codecRegistry(pojoCodecRegistry)
                        .build();

        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(DATABASE_NAME);

        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
        AIModelProvider provider = new AIModelProvider(List.of(), new StandardEnvironment());
        LLMs llm = new LLMs(null, new JsonSchemaValidator(objectMapper), provider);

        MongoDBConfig mongoConfig = new MongoDBConfig();
        mongoConfig.setDatabase(DATABASE_NAME);
        mongoConfig.setConnectionString(mongoDBContainer.getConnectionString());

        // Create VectorDB instance
        MongoVectorDB mongoVectorDB = new MongoVectorDB("mongodb-test", mongoConfig);

        // Create instance config with the vectorDB
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        VectorDBInstanceConfig.VectorDBInstance instance =
                new VectorDBInstanceConfig.VectorDBInstance();
        instance.setName("mongodb-test");
        instance.setType("mongodb");
        instance.setMongodb(mongoConfig);
        instanceConfig.setInstances(List.of(instance));

        VectorDBProvider vectorDBProvider = new VectorDBProvider(instanceConfig);
        VectorDBs vectorDBs = new VectorDBs(vectorDBProvider);
        aiWorkers = new VectorDBWorkers(vectorDBs, llm);
    }

    @Test
    public void testConnectionStringNotEmpty() {
        assertNotNull(mongoDBContainer);
        assertNotNull(mongoDBContainer.getConnectionString());
    }

    @Test
    public void testUpdateEmbeddings() {
        StoreEmbeddingsInput storeEmbeddingsInput =
                getMockStoreEmbeddingsInput(List.of(1.1f, 2.2f, 3.4f));

        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        TaskContext.TASK_CONTEXT_INHERITABLE_THREAD_LOCAL.set(
                new TaskContext(task, new TaskResult()));

        int documentsUpdated = aiWorkers.storeEmbeddings(storeEmbeddingsInput);
        MongoCollection<?> collection = database.getCollection("items");
        Document result = (Document) collection.find(new Document("doc_id", "testId")).first();
        assertNotNull(result);
        assertTrue(documentsUpdated != 0);
    }

    @Test
    public void testSearchEmbeddings() {
        /**
         * Vector search doesn't work with MongoDB local container,it only works with Atlas, Right
         * now there is no way to automatically spin-up atlas container using testContainers and
         * perform vector search
         */
        assertTrue(true);
    }

    private StoreEmbeddingsInput getMockStoreEmbeddingsInput(List<Float> embeddings) {
        StoreEmbeddingsInput storeEmbeddingsInput = new StoreEmbeddingsInput();
        storeEmbeddingsInput.setVectorDB("mongodb-test");
        storeEmbeddingsInput.setId("testId");
        storeEmbeddingsInput.setIndex("testindex");
        storeEmbeddingsInput.setMetadata(Map.of("key1", "val1"));
        storeEmbeddingsInput.setNamespace("items");
        storeEmbeddingsInput.setMaxResults(4);
        storeEmbeddingsInput.setEmbeddings(embeddings);
        return storeEmbeddingsInput;
    }
}
