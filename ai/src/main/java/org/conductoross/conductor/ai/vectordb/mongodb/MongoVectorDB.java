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
package org.conductoross.conductor.ai.vectordb.mongodb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.conductoross.conductor.ai.models.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoVectorDB extends VectorDB {

    public static final String TYPE = "mongodb";
    private final Cache<String, MongoClient> mongoClients;
    private final Cache<String, MongoDatabase> mongoDatabases;
    private final MongoDBConfig config;

    private static final String VALID_NAME_REGEX = "[a-zA-Z0-9_-]+";
    private static final Pattern pattern = Pattern.compile(VALID_NAME_REGEX);

    public MongoVectorDB(String name, MongoDBConfig config) {
        super(name, TYPE);
        this.config = config;
        this.mongoClients =
                CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterAccess(Duration.ofSeconds(60))
                        .concurrencyLevel(32)
                        .build();
        this.mongoDatabases =
                CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterAccess(Duration.ofSeconds(60))
                        .concurrencyLevel(32)
                        .build();
    }

    @Override
    public int updateEmbeddings(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> metadata) {
        if (!pattern.matcher(namespace).matches()) {
            throw new RuntimeException("Invalid namespace");
        }
        if (!pattern.matcher(indexName).matches()) {
            throw new RuntimeException("Invalid index name");
        }

        MongoClient client = null;
        MongoDatabase mongoDatabase = null;
        try {
            client = getClient();
            mongoDatabase = getDatabase(client);
            // assume collection exists and vector search index applied on it
            return upsertEmbeddings(
                    namespace, id, parentDocId, doc, embeddings, metadata, mongoDatabase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int upsertEmbeddings(
            String namespace,
            String id,
            String parentDocId,
            String doc,
            List<Float> embeddings,
            Map<String, Object> metadata,
            MongoDatabase database) {
        MongoCollection<Document> collection = database.getCollection(namespace);
        Document filter = new Document("doc_id", id);

        Document update =
                new Document(
                        "$set",
                        new Document("parent_doc_id", parentDocId)
                                .append("doc_id", id)
                                .append("doc", doc)
                                .append("embedding", embeddings)
                                .append("metadata", metadata));

        FindOneAndUpdateOptions options =
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Document result = collection.findOneAndUpdate(filter, update, options);
        return result != null ? 1 : 0;
    }

    @SneakyThrows
    private MongoClient getClient() {
        String connectionString = config.getConnectionString();
        return mongoClients.get(connectionString, () -> getMongoClient(connectionString));
    }

    @SneakyThrows
    private MongoDatabase getDatabase(MongoClient mongoClient) {
        String database = config.getDatabase();
        return mongoDatabases.get(database, () -> mongoClient.getDatabase(database));
    }

    private MongoClient getMongoClient(String connectionString) {
        CodecRegistry pojoCodecRegistry =
                CodecRegistries.fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        CodecRegistries.fromProviders(
                                PojoCodecProvider.builder().automatic(true).build()));
        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(connectionString))
                        .codecRegistry(pojoCodecRegistry)
                        .build();
        return MongoClients.create(settings);
    }

    @Override
    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {

        Bson vectorSearch =
                new Document(
                        "$vectorSearch",
                        new Document("queryVector", embeddings)
                                .append("path", "embedding")
                                .append("limit", maxResults)
                                .append("index", indexName));

        MongoClient client = getClient();
        MongoDatabase database = getDatabase(client);
        MongoCollection<Document> collection = database.getCollection(namespace);

        Bson project =
                new Document(
                        "$project",
                        new Document("score", new Document("$meta", "vectorSearchScore"))
                                .append("doc", 1)
                                .append("parent_doc_id", 1)
                                .append("doc_id", 1)
                                .append("metadata", 1));

        List<Bson> pipeline = Arrays.asList(vectorSearch, project);
        List<IndexedDoc> matches = new ArrayList<>();
        AggregateIterable<Document> results = collection.aggregate(pipeline);

        for (Document document : results) {
            IndexedDoc indexedDoc =
                    new IndexedDoc(
                            document.getString("doc_id"),
                            document.getString("parent_doc_id"),
                            document.getString("doc"),
                            document.getDouble("score").doubleValue());
            indexedDoc.setMetadata((Map<String, Object>) document.get("metadata"));
            matches.add(indexedDoc);
        }
        return matches;
    }
}
