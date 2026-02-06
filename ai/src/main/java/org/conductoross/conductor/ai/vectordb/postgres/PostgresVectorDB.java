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
package org.conductoross.conductor.ai.vectordb.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.models.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;
import org.conductoross.conductor.common.utils.TextUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.pgvector.PGvector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(PostgresVectorDB.TYPE)
public class PostgresVectorDB extends VectorDB {

    public static final String TYPE = "pgvectordb";

    private final Cache<String, DataSource> pgvectorClients;
    private final ObjectMapper objectMapper;
    private final PostgresConfig config;
    private static final String VALID_NAME_REGEX = "[a-zA-Z0-9_-]+";
    private static final Pattern pattern = Pattern.compile(VALID_NAME_REGEX);

    public PostgresVectorDB(PostgresConfig config) {
        super(TYPE);
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.pgvectorClients =
                CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterAccess(Duration.ofSeconds(60))
                        .concurrencyLevel(32)
                        .removalListener(
                                new RemovalListener<String, DataSource>() {
                                    @Override
                                    public void onRemoval(
                                            RemovalNotification<String, DataSource> notification) {
                                        DataSource dataSource = notification.getValue();
                                        if (dataSource instanceof HikariDataSource) {
                                            ((HikariDataSource) dataSource).close();
                                        }
                                    }
                                })
                        .build();
    }

    @SneakyThrows
    private DataSource getClient() {
        String cacheKey = config.getDatasourceURL();
        return pgvectorClients.get(cacheKey, this::getPgVectorClient);
    }

    private DataSource getPgVectorClient() {
        String connectionURL = config.getDatasourceURL();
        final String driverClassName = "org.postgresql.Driver";

        if (StringUtils.isBlank(connectionURL)) {
            throw new RuntimeException(
                    "Missing connection URL - please check conductor.vectordb.postgres.datasourceURL");
        }

        String userName = config.getUser();
        String password = config.getPassword();
        int poolSize = config.getConnectionPoolSize() != null ? config.getConnectionPoolSize() : 5;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(connectionURL);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setDriverClassName(driverClassName);
        hikariConfig.setUsername(userName);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setIdleTimeout(60_000);

        return new HikariDataSource(hikariConfig);
    }

    private void waitForConnectionPoolReady(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            int maxWaitTime = 5000; // 5 seconds
            int waitInterval = 20; // 20ms
            int totalWaited = 0;

            while (!hikariDataSource.isRunning() && totalWaited < maxWaitTime) {
                try {
                    Thread.sleep(waitInterval);
                    totalWaited += waitInterval;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for connection pool", e);
                }
            }

            if (!hikariDataSource.isRunning()) {
                throw new RuntimeException(
                        "Connection pool failed to start within " + maxWaitTime + "ms");
            }
        }
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
        if (parentDocId == null) {
            parentDocId = id;
        }
        if (!pattern.matcher(namespace).matches()) {
            throw new RuntimeException("Invalid namespace");
        }
        if (!pattern.matcher(indexName).matches()) {
            throw new RuntimeException("Invalid index name");
        }
        DataSource dataSource;
        try {
            // Assuming vector extension exists
            dataSource = getClient();
            // Wait for connection pool to be ready
            waitForConnectionPoolReady(dataSource);
        } catch (Exception exception) {
            log.error(
                    "Error encountered while fetching datasource : {}",
                    exception.getMessage(),
                    exception);
            throw new RuntimeException(exception);
        }

        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
            String tableName =
                    config.getTablePrefix() != null
                            ? config.getTablePrefix() + "_" + namespace
                            : namespace;
            createVectorTableIfNotExists(tableName, conn);
            createVectorIndexIfNotExists(tableName, indexName, conn);
            return upsertEmbeddings(namespace, id, parentDocId, doc, embeddings, metadata, conn);
        } catch (Exception exception) {
            log.error(
                    "Error encountered while updating embeddings as : {}",
                    exception.getMessage(),
                    exception);
            throw new RuntimeException(exception);
        }
    }

    private int upsertEmbeddings(
            String namespace,
            String id,
            String parentDocId,
            String doc,
            List<Float> embeddings,
            Map<String, Object> metadata,
            Connection conn) {
        final int embeddingDimensions =
                config.getDimensions() != null ? config.getDimensions() : 256;
        if (embeddingDimensions != embeddings.size()) {
            throw new RuntimeException("Embeddings must be of dimensions : " + embeddingDimensions);
        }
        String tableName =
                config.getTablePrefix() != null
                        ? config.getTablePrefix() + "_" + namespace
                        : namespace;
        String UPSERT_QUERY =
                "INSERT INTO "
                        + tableName
                        + " AS n (id, parent_doc_id, embedding, doc, metadata) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET parent_doc_id = ?, embedding = ?, doc = ?, metadata = ? WHERE n.id = ?";
        log.debug("Executing upsert query: {}", UPSERT_QUERY);
        log.debug(
                "Upserting document with id: {}, parentDocId: {}, doc length: {}, embedding dimensions: {}",
                id,
                parentDocId,
                doc.length(),
                embeddings.size());
        try (PreparedStatement statement = conn.prepareStatement(UPSERT_QUERY)) {
            conn.setAutoCommit(true);
            int paramIndex = 1;

            Object[] vectorArray = new Object[embeddings.size()];
            for (int i = 0; i < embeddings.size(); i++) {
                vectorArray[i] = embeddings.get(i);
            }
            // insert
            statement.setString(paramIndex++, id);
            statement.setString(paramIndex++, parentDocId);
            statement.setArray(
                    paramIndex++, conn.createArrayOf("float8", vectorArray)); // embedding
            statement.setString(paramIndex++, TextUtils.sanitizeForPostgres(doc));
            statement.setObject(
                    paramIndex++, objectMapper.writeValueAsString(metadata), java.sql.Types.OTHER);

            // updates
            statement.setString(paramIndex++, parentDocId);
            statement.setArray(
                    paramIndex++, conn.createArrayOf("float8", vectorArray)); // embedding
            statement.setString(paramIndex++, TextUtils.sanitizeForPostgres(doc));
            statement.setObject(
                    paramIndex++, objectMapper.writeValueAsString(metadata), java.sql.Types.OTHER);
            statement.setString(paramIndex++, id);
            int result = statement.executeUpdate();
            log.debug("Upsert operation completed, rows affected: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error occurred creating vector table for pgvector support : {}", e);
            throw new RuntimeException(e);
        }
    }

    private void createVectorIndexIfNotExists(String tableName, String indexName, Connection conn) {
        try {
            conn.setAutoCommit(true);
            final String indexingMethod =
                    config.getIndexingMethod() != null ? config.getIndexingMethod() : "hnsw";
            final String distanceMetric =
                    config.getDistanceMetric() != null ? config.getDistanceMetric() : "l2";
            String vectorOps = getVectorOps(distanceMetric);
            String sql =
                    "CREATE INDEX IF NOT EXISTS "
                            + indexName
                            + " ON "
                            + tableName
                            + " USING hnsw (embedding "
                            + vectorOps
                            + ");";
            switch (indexingMethod) {
                case "ivfflat":
                    int invertedListCount =
                            config.getInvertedListCount() != null
                                    ? config.getInvertedListCount()
                                    : 100;
                    sql =
                            "CREATE INDEX IF NOT EXISTS "
                                    + indexName
                                    + " ON "
                                    + tableName
                                    + " USING ivfflat (embedding "
                                    + vectorOps
                                    + ") WITH (lists = "
                                    + invertedListCount
                                    + ");";
                    break;
                default:
                    break;
            }
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                int created = statement.executeUpdate();
                log.debug("Vector table created for pgvector {}", created);
            }
        } catch (Exception e) {
            log.error("Error occurred creating vector index for pgvector : {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void createVectorTableIfNotExists(String tableName, Connection conn) {
        try {
            conn.setAutoCommit(true);
            final int embeddingDimensions =
                    config.getDimensions() != null ? config.getDimensions() : 256;
            String sql =
                    "CREATE TABLE IF NOT EXISTS "
                            + tableName
                            + " ("
                            + "id VARCHAR(255) PRIMARY KEY, "
                            + "parent_doc_id VARCHAR(255) NOT NULL, "
                            + "embedding VECTOR("
                            + embeddingDimensions
                            + "), "
                            + "doc TEXT NOT NULL, "
                            + "metadata TEXT NOT NULL"
                            + ")";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                log.debug("Executing SQL: {}", sql);
                int created = statement.executeUpdate();
                log.debug("Pgvector table created {}, SQL result: {}", created, created);
            }
        } catch (Exception e) {
            log.error("Error occurred creating vector table for pgvector : {}", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {
        return searchWithNamespace(namespace, embeddings, maxResults);
    }

    private List<IndexedDoc> searchWithNamespace(
            String namespace, List<Float> embeddings, int maxResults) {
        if (!pattern.matcher(namespace).matches()) {
            throw new RuntimeException("Invalid namespace");
        }
        DataSource dataSource = getClient();
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
            String distanceMetric =
                    config.getDistanceMetric() != null ? config.getDistanceMetric() : "l2";
            String queryOperator = getQueryOperator(distanceMetric);
            final int embeddingDimensions =
                    config.getDimensions() != null ? config.getDimensions() : 256;
            if (embeddingDimensions != embeddings.size()) {
                throw new RuntimeException(
                        "Embeddings must be of dimensions : " + embeddingDimensions);
            }
            String tableName =
                    config.getTablePrefix() != null
                            ? config.getTablePrefix() + "_" + namespace
                            : namespace;
            conn.setAutoCommit(true);
            // queryOperator is derived from a switch statement on valid distance metrics,
            // so it is safe from injection
            String SEARCH_QUERY =
                    "SELECT id, parent_doc_id, doc, metadata, embedding "
                            + queryOperator
                            + " ? AS distance FROM "
                            + tableName
                            + " ORDER BY distance LIMIT "
                            + maxResults;
            try (PreparedStatement statement = conn.prepareStatement(SEARCH_QUERY)) {
                float[] embeddingArray = new float[embeddings.size()];
                for (int i = 0; i < embeddings.size(); i++) {
                    embeddingArray[i] = embeddings.get(i);
                }
                statement.setObject(1, new PGvector(embeddingArray));
                try (ResultSet rs = statement.executeQuery()) {
                    List<IndexedDoc> matches = new ArrayList<>();
                    while (rs.next()) {
                        String docId = rs.getString("id");
                        String parentDocId = rs.getString("parent_doc_id");
                        double distance = rs.getDouble("distance");
                        String text = rs.getString("doc");
                        Map<String, Object> metadata =
                                objectMapper.readValue(
                                        rs.getObject("metadata").toString(), Map.class);
                        IndexedDoc indexedDoc = new IndexedDoc(docId, parentDocId, text, distance);
                        indexedDoc.setMetadata(metadata);
                        matches.add(indexedDoc);
                    }
                    return matches;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred searching in vector table for pgvector : {}", e);
            throw new RuntimeException(e);
        }
    }

    private String getVectorOps(String distanceMetric) {
        String vectorOps = "vector_l2_ops";
        switch (distanceMetric) {
            case "cosine":
                vectorOps = "vector_cosine_ops";
                break;
            case "inner_product":
                vectorOps = "vector_ip_ops";
                break;
            default:
                break;
        }
        return vectorOps;
    }

    private String getQueryOperator(String distanceMetric) {
        String queryOperator = "<->";
        switch (distanceMetric) {
            case "cosine":
                queryOperator = "<=>";
                break;
            case "inner_product":
                queryOperator = "<#>";
                break;
            default:
                break;
        }
        return queryOperator;
    }
}
