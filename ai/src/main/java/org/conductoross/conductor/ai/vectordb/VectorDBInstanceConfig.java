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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.vectordb.mongodb.MongoDBConfig;
import org.conductoross.conductor.ai.vectordb.pinecone.PineconeConfig;
import org.conductoross.conductor.ai.vectordb.postgres.PostgresConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Main configuration class for vector database instances. Supports multiple named instances of
 * different vector database types.
 *
 * <p>Configuration example:
 *
 * <pre>
 * conductor.vectordb.instances:
 *   - name: "postgres-prod"
 *     type: "postgres"
 *     postgres:
 *       datasourceURL: "jdbc:postgresql://prod:5432/vectors"
 *       user: "admin"
 *       password: "secret"
 *   - name: "pinecone-embeddings"
 *     type: "pinecone"
 *     pinecone:
 *       apiKey: "your-api-key"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "conductor.vectordb")
@Slf4j
public class VectorDBInstanceConfig implements VectorDBConfig<VectorDB> {

    private List<VectorDBInstance> instances;

    public List<VectorDBInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<VectorDBInstance> instances) {
        this.instances = instances;
    }

    @Override
    public VectorDB get() {
        // This method is not used directly. The provider will iterate over instances.
        throw new UnsupportedOperationException(
                "Use getInstances() to access individual vector DB configurations");
    }

    /**
     * Returns a map of VectorDB instances keyed by their configured names. Each instance is
     * initialized based on its type and configuration.
     */
    public Map<String, VectorDB> getVectorDBInstances() {
        Map<String, VectorDB> vectorDBMap = new HashMap<>();
        if (instances == null || instances.isEmpty()) {
            log.warn("No vector DB instances configured");
            return vectorDBMap;
        }

        for (VectorDBInstance instance : instances) {
            try {
                VectorDB vectorDB = createVectorDB(instance);
                if (vectorDB != null) {
                    vectorDBMap.put(instance.getName(), vectorDB);
                    log.info(
                            "Initialized vector DB instance: {} (type: {})",
                            instance.getName(),
                            instance.getType());
                }
            } catch (Exception e) {
                log.error(
                        "Failed to initialize vector DB instance: {} (type: {}), reason: {}",
                        instance.getName(),
                        instance.getType(),
                        e.getMessage());
            }
        }

        return vectorDBMap;
    }

    /** Creates a VectorDB instance based on the configuration type. */
    private VectorDB createVectorDB(VectorDBInstance instance) {
        String type = instance.getType();
        if (type == null) {
            log.error("Vector DB instance {} has no type specified", instance.getName());
            return null;
        }

        switch (type.toLowerCase()) {
            case "postgres":
            case "pgvectordb":
                return createPostgresVectorDB(instance);
            case "mongodb":
            case "mongovectordb":
                return createMongoVectorDB(instance);
            case "pinecone":
            case "pineconedb":
                return createPineconeVectorDB(instance);
            default:
                log.error("Unknown vector DB type: {} for instance: {}", type, instance.getName());
                return null;
        }
    }

    private VectorDB createPostgresVectorDB(VectorDBInstance instance) {
        PostgresConfig config = instance.getPostgres();
        if (config == null) {
            log.error("Postgres configuration missing for instance: {}", instance.getName());
            return null;
        }
        return config.get(instance.getName());
    }

    private VectorDB createMongoVectorDB(VectorDBInstance instance) {
        MongoDBConfig config = instance.getMongodb();
        if (config == null) {
            log.error("MongoDB configuration missing for instance: {}", instance.getName());
            return null;
        }
        return config.get(instance.getName());
    }

    private VectorDB createPineconeVectorDB(VectorDBInstance instance) {
        PineconeConfig config = instance.getPinecone();
        if (config == null) {
            log.error("Pinecone configuration missing for instance: {}", instance.getName());
            return null;
        }
        return config.get(instance.getName());
    }

    /** Represents a single vector DB instance configuration. */
    public static class VectorDBInstance {
        private String name;
        private String type;
        private PostgresConfig postgres;
        private MongoDBConfig mongodb;
        private PineconeConfig pinecone;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public PostgresConfig getPostgres() {
            return postgres;
        }

        public void setPostgres(PostgresConfig postgres) {
            this.postgres = postgres;
        }

        public MongoDBConfig getMongodb() {
            return mongodb;
        }

        public void setMongodb(MongoDBConfig mongodb) {
            this.mongodb = mongodb;
        }

        public PineconeConfig getPinecone() {
            return pinecone;
        }

        public void setPinecone(PineconeConfig pinecone) {
            this.pinecone = pinecone;
        }
    }
}
