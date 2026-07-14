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
package org.conductoross.conductor.ai.vectordb.sqlite;

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
import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;
import org.conductoross.conductor.common.utils.TextUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Vector database backed by SQLite and the <a
 * href="https://github.com/asg017/sqlite-vec">sqlite-vec</a> extension. Embeddings are stored in a
 * {@code vec0} virtual table and queried with sqlite-vec's KNN {@code MATCH} operator. This is an
 * embedded, zero-infrastructure alternative to the pgvector, MongoDB and Pinecone backends, suited
 * to local development, demos and small deployments.
 *
 * <p>sqlite-vec performs an exact (brute-force) KNN scan and has no ANN index, so it is appropriate
 * for thousands to low-millions of vectors rather than large-scale corpora.
 *
 * <p>The native {@code vec0} extension is loaded on every physical connection via {@code
 * load_extension}; see {@link SqliteConfig} for how to make it available on the host.
 */
@Slf4j
public class SqliteVectorDB extends VectorDB {

    public static final String TYPE = "sqlite";

    private final Cache<String, DataSource> sqliteClients;
    private final ObjectMapper objectMapper;
    private final SqliteConfig config;
    private static final String VALID_NAME_REGEX = "[a-zA-Z0-9_-]+";
    private static final Pattern pattern = Pattern.compile(VALID_NAME_REGEX);

    public SqliteVectorDB(String name, SqliteConfig config) {
        super(name, TYPE);
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.sqliteClients =
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
        String cacheKey = config.getDbPath();
        return sqliteClients.get(cacheKey, this::getSqliteClient);
    }

    private DataSource getSqliteClient() {
        String dbPath = config.getDbPath();
        if (StringUtils.isBlank(dbPath)) {
            throw new RuntimeException(
                    "Missing dbPath - please check conductor.vectordb.<instance>.sqlite.dbPath");
        }

        // Extension loading must be enabled at the driver level before load_extension() can run.
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enableLoadExtension(true);
        SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
        sqliteDataSource.setUrl("jdbc:sqlite:" + dbPath);

        // Resolution order: explicit configured path -> bundled binary for this platform -> the
        // bare name "vec0" (relies on the host's default library search path).
        String extension = config.getExtensionPath();
        if (StringUtils.isBlank(extension)) {
            extension = SqliteVecExtensions.resolveBundledExtensionPath();
        }
        if (StringUtils.isBlank(extension)) {
            extension = "vec0";
        }
        // Allowlist check on operator-supplied paths to prevent unexpected SQL content. Bundled
        // paths (temp-dir extractions) match this pattern; bare "vec0" also matches.
        if (!extension.matches("[a-zA-Z0-9/_\\-.]+")) {
            throw new RuntimeException("extensionPath contains invalid characters: " + extension);
        }
        // Loaded once per physical connection so every pooled connection has vec0 available.
        String loadExtensionSql = "SELECT load_extension('" + extension.replace("'", "''") + "')";

        int poolSize = config.getConnectionPoolSize() != null ? config.getConnectionPoolSize() : 5;
        // An in-memory database is private to a single connection, so the pool must be size 1.
        if (dbPath.contains(":memory:")) {
            poolSize = 1;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDataSource(sqliteDataSource);
        hikariConfig.setConnectionInitSql(loadExtensionSql);
        hikariConfig.setAutoCommit(true);
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
        final int embeddingDimensions =
                config.getDimensions() != null ? config.getDimensions() : 256;
        if (embeddingDimensions != embeddings.size()) {
            throw new RuntimeException("Embeddings must be of dimensions : " + embeddingDimensions);
        }

        DataSource dataSource;
        try {
            dataSource = getClient();
            waitForConnectionPoolReady(dataSource);
        } catch (Exception exception) {
            log.error(
                    "Error encountered while fetching datasource : {}",
                    exception.getMessage(),
                    exception);
            throw new RuntimeException(exception);
        }

        try (Connection conn = dataSource.getConnection()) {
            String tableName = tableName(namespace);
            createVectorTableIfNotExists(tableName, conn);
            return upsertEmbeddings(tableName, id, parentDocId, doc, embeddings, metadata, conn);
        } catch (Exception exception) {
            log.error(
                    "Error encountered while updating embeddings as : {}",
                    exception.getMessage(),
                    exception);
            throw new RuntimeException(exception);
        }
    }

    private int upsertEmbeddings(
            String tableName,
            String id,
            String parentDocId,
            String doc,
            List<Float> embeddings,
            Map<String, Object> metadata,
            Connection conn) {
        // vec0 is a virtual table and does not support ON CONFLICT upserts, so we delete any
        // existing row for the id and insert the new one inside a single transaction.
        String deleteQuery = "DELETE FROM " + tableName + " WHERE id = ?";
        String insertQuery =
                "INSERT INTO "
                        + tableName
                        + " (id, embedding, parent_doc_id, doc, metadata) VALUES (?, ?, ?, ?, ?)";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.setString(1, id);
                deleteStmt.executeUpdate();
            }
            int result;
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                insertStmt.setString(1, id);
                insertStmt.setString(2, toJsonVector(embeddings));
                insertStmt.setString(3, parentDocId);
                insertStmt.setString(4, TextUtils.sanitizeForPostgres(doc));
                insertStmt.setString(5, objectMapper.writeValueAsString(metadata));
                result = insertStmt.executeUpdate();
            }
            conn.commit();
            log.debug("Upsert operation completed for id {}, rows affected: {}", id, result);
            return result;
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (Exception rollbackEx) {
                log.error("Error rolling back sqlite-vec upsert : {}", rollbackEx.getMessage());
            }
            log.error("Error occurred upserting embeddings for sqlite-vec : {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            // Always restore autocommit so this connection is safe to reuse from the Hikari pool.
            try {
                conn.setAutoCommit(true);
            } catch (Exception ex) {
                log.warn(
                        "Failed to restore autocommit on sqlite-vec connection: {}",
                        ex.getMessage());
            }
        }
    }

    private void createVectorTableIfNotExists(String tableName, Connection conn) {
        final int embeddingDimensions =
                config.getDimensions() != null ? config.getDimensions() : 256;
        String distanceClause = distanceMetricClause();
        // doc/parent_doc_id/metadata are auxiliary columns (the "+" prefix): stored and returned
        // alongside each vector but not indexed or used for filtering.
        String sql =
                "CREATE VIRTUAL TABLE IF NOT EXISTS "
                        + tableName
                        + " USING vec0("
                        + "id TEXT PRIMARY KEY, "
                        + "embedding FLOAT["
                        + embeddingDimensions
                        + "]"
                        + distanceClause
                        + ", "
                        + "+parent_doc_id TEXT, "
                        + "+doc TEXT, "
                        + "+metadata TEXT"
                        + ")";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            log.debug("Executing SQL: {}", sql);
            statement.executeUpdate();
        } catch (Exception e) {
            log.error("Error occurred creating vec0 table for sqlite-vec : {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {
        if (!pattern.matcher(namespace).matches()) {
            throw new RuntimeException("Invalid namespace");
        }
        final int embeddingDimensions =
                config.getDimensions() != null ? config.getDimensions() : 256;
        if (embeddingDimensions != embeddings.size()) {
            throw new RuntimeException("Embeddings must be of dimensions : " + embeddingDimensions);
        }

        DataSource dataSource = getClient();
        try (Connection conn = dataSource.getConnection()) {
            String tableName = tableName(namespace);
            // sqlite-vec KNN: constrain the vector column with MATCH and bound the result set with
            // the special "k" column.
            String searchQuery =
                    "SELECT id, parent_doc_id, doc, metadata, distance FROM "
                            + tableName
                            + " WHERE embedding MATCH ? AND k = ? ORDER BY distance";
            try (PreparedStatement statement = conn.prepareStatement(searchQuery)) {
                statement.setString(1, toJsonVector(embeddings));
                statement.setInt(2, maxResults);
                try (ResultSet rs = statement.executeQuery()) {
                    List<IndexedDoc> matches = new ArrayList<>();
                    while (rs.next()) {
                        String docId = rs.getString("id");
                        String parentDocId = rs.getString("parent_doc_id");
                        double distance = rs.getDouble("distance");
                        String text = rs.getString("doc");
                        String metadataJson = rs.getString("metadata");
                        Map<String, Object> metadata =
                                metadataJson != null
                                        ? objectMapper.readValue(metadataJson, Map.class)
                                        : Map.of();
                        IndexedDoc indexedDoc = new IndexedDoc(docId, parentDocId, text, distance);
                        indexedDoc.setMetadata(metadata);
                        matches.add(indexedDoc);
                    }
                    return matches;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred searching in vec0 table for sqlite-vec : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String tableName(String namespace) {
        String rawName =
                config.getTablePrefix() != null
                        ? config.getTablePrefix() + "_" + namespace
                        : namespace;
        // namespace/prefix are validated against VALID_NAME_REGEX; quoting lets identifiers
        // containing '-' be used safely.
        return "\"" + rawName.replace("\"", "\"\"") + "\"";
    }

    /**
     * Renders the embedding as a JSON array, which sqlite-vec accepts for both storage and MATCH.
     */
    private String toJsonVector(List<Float> embeddings) {
        StringBuilder sb = new StringBuilder(embeddings.size() * 8);
        sb.append('[');
        for (int i = 0; i < embeddings.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embeddings.get(i).floatValue());
        }
        sb.append(']');
        return sb.toString();
    }

    private String distanceMetricClause() {
        String distanceMetric =
                config.getDistanceMetric() != null ? config.getDistanceMetric() : "l2";
        switch (distanceMetric.toLowerCase()) {
            case "cosine":
                return " distance_metric=cosine";
            case "l1":
                return " distance_metric=L1";
            case "l2":
                return ""; // L2 is the vec0 default
            default:
                log.warn(
                        "Unsupported sqlite-vec distance metric '{}', falling back to l2",
                        distanceMetric);
                return "";
        }
    }
}
