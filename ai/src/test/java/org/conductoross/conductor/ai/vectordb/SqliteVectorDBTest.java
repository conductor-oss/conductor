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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.sqlite.SqliteConfig;
import org.conductoross.conductor.ai.vectordb.sqlite.SqliteVectorDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import com.zaxxer.hikari.HikariDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SqliteVectorDBTest {

    private SqliteConfig config;
    private SqliteVectorDB vectorDB;

    @BeforeEach
    public void setup() {
        config = new SqliteConfig();
        config.setDbPath("/tmp/conductor-vectordb-test.db");
        config.setDimensions(3);
        vectorDB = new SqliteVectorDB("test-sqlite", config);
    }

    @Test
    public void testUpdateEmbeddingsHappyPath() throws SQLException {
        try (MockedConstruction<HikariDataSource> mockedDataSource =
                mockConstruction(
                        HikariDataSource.class,
                        (mock, context) -> {
                            when(mock.isRunning()).thenReturn(true);
                            Connection conn = mock(Connection.class);
                            when(mock.getConnection()).thenReturn(conn);

                            PreparedStatement createStmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("CREATE VIRTUAL TABLE")))
                                    .thenReturn(createStmt);

                            PreparedStatement deleteStmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("DELETE FROM")))
                                    .thenReturn(deleteStmt);

                            PreparedStatement insertStmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("INSERT INTO")))
                                    .thenReturn(insertStmt);
                            when(insertStmt.executeUpdate()).thenReturn(1);
                        })) {

            int result =
                    vectorDB.updateEmbeddings(
                            "idx",
                            "ns",
                            "doc",
                            "parent",
                            "id",
                            List.of(1.0f, 2.0f, 3.0f),
                            Map.of("k", "v"));

            assertEquals(1, result);

            HikariDataSource ds = mockedDataSource.constructed().get(0);
            verify(ds, atLeastOnce()).getConnection();
            verify(ds.getConnection(), times(1)).close();
        }
    }

    @Test
    public void testSearchHappyPath() throws SQLException {
        try (MockedConstruction<HikariDataSource> mockedDataSource =
                mockConstruction(
                        HikariDataSource.class,
                        (mock, context) -> {
                            when(mock.isRunning()).thenReturn(true);
                            Connection conn = mock(Connection.class);
                            when(mock.getConnection()).thenReturn(conn);

                            PreparedStatement stmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("MATCH"))).thenReturn(stmt);

                            ResultSet rs = mock(ResultSet.class);
                            when(stmt.executeQuery()).thenReturn(rs);
                            when(rs.next()).thenReturn(true).thenReturn(false);
                            when(rs.getString("id")).thenReturn("id1");
                            when(rs.getString("parent_doc_id")).thenReturn("pid1");
                            when(rs.getDouble("distance")).thenReturn(0.1);
                            when(rs.getString("doc")).thenReturn("text");
                            when(rs.getString("metadata")).thenReturn("{\"k\":\"v\"}");
                        })) {

            List<IndexedDoc> results = vectorDB.search("idx", "ns", List.of(1.0f, 2.0f, 3.0f), 10);
            assertEquals(1, results.size());
            assertEquals("id1", results.get(0).getDocId());
            assertEquals("pid1", results.get(0).getParentDocId());
            assertEquals(0.1, results.get(0).getScore());
            assertEquals("v", results.get(0).getMetadata().get("k"));

            HikariDataSource ds = mockedDataSource.constructed().get(0);
            verify(ds.getConnection(), times(1)).close();
        }
    }

    @Test
    public void testSearchBindsVectorAsJsonAndK() throws SQLException {
        try (MockedConstruction<HikariDataSource> mockedDataSource =
                mockConstruction(
                        HikariDataSource.class,
                        (mock, context) -> {
                            when(mock.isRunning()).thenReturn(true);
                            Connection conn = mock(Connection.class);
                            when(mock.getConnection()).thenReturn(conn);

                            PreparedStatement stmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(anyString())).thenReturn(stmt);

                            ResultSet rs = mock(ResultSet.class);
                            when(stmt.executeQuery()).thenReturn(rs);
                            when(rs.next()).thenReturn(false);
                        })) {

            vectorDB.search("idx", "ns", List.of(1.0f, 2.0f, 3.0f), 7);

            HikariDataSource ds = mockedDataSource.constructed().get(0);
            // prepareStatement(anyString()) is stubbed to return the same mock for any query.
            PreparedStatement stmt = ds.getConnection().prepareStatement("dummy");
            ArgumentCaptor<String> vectorCaptor = ArgumentCaptor.forClass(String.class);
            verify(stmt).setString(eq(1), vectorCaptor.capture());
            assertEquals("[1.0,2.0,3.0]", vectorCaptor.getValue());
            verify(stmt).setInt(2, 7);
        }
    }

    @Test
    public void testCosineDistanceMetricInTableDefinition() throws SQLException {
        config.setDistanceMetric("cosine");
        try (MockedConstruction<HikariDataSource> mockedDataSource =
                mockConstruction(
                        HikariDataSource.class,
                        (mock, context) -> {
                            when(mock.isRunning()).thenReturn(true);
                            Connection conn = mock(Connection.class);
                            when(mock.getConnection()).thenReturn(conn);
                            when(conn.prepareStatement(anyString()))
                                    .thenReturn(mock(PreparedStatement.class));
                        })) {

            vectorDB.updateEmbeddings("idx", "ns", "doc", "p", "id", List.of(1f, 2f, 3f), Map.of());

            HikariDataSource ds = mockedDataSource.constructed().get(0);
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(ds.getConnection(), atLeastOnce()).prepareStatement(sqlCaptor.capture());
            assertTrue(
                    sqlCaptor.getAllValues().stream()
                            .anyMatch(s -> s.contains("distance_metric=cosine")));
        }
    }

    @Test
    public void testInvalidNamespace() {
        assertThrows(
                RuntimeException.class,
                () ->
                        vectorDB.updateEmbeddings(
                                "idx",
                                "invalid/ns",
                                "doc",
                                "p",
                                "id",
                                List.of(1f, 2f, 3f),
                                Map.of()));
        assertThrows(
                RuntimeException.class,
                () -> vectorDB.search("idx", "invalid/ns", List.of(1f, 2f, 3f), 1));
    }

    @Test
    public void testDimensionMismatch() {
        assertThrows(
                RuntimeException.class,
                () ->
                        vectorDB.updateEmbeddings(
                                "idx", "ns", "doc", "p", "id", List.of(1f), Map.of()));
        assertThrows(RuntimeException.class, () -> vectorDB.search("idx", "ns", List.of(1f), 1));
    }

    @Test
    public void testMissingDbPath() {
        config.setDbPath(null);
        assertThrows(
                RuntimeException.class, () -> vectorDB.search("idx", "ns", List.of(1f, 2f, 3f), 1));
    }

    @Test
    public void testUpsertRollsBackAndRestoresAutocommitOnInsertFailure() throws SQLException {
        try (MockedConstruction<HikariDataSource> mockedDataSource =
                mockConstruction(
                        HikariDataSource.class,
                        (mock, context) -> {
                            when(mock.isRunning()).thenReturn(true);
                            Connection conn = mock(Connection.class);
                            when(mock.getConnection()).thenReturn(conn);

                            PreparedStatement createStmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("CREATE VIRTUAL TABLE")))
                                    .thenReturn(createStmt);

                            PreparedStatement deleteStmt = mock(PreparedStatement.class);
                            when(conn.prepareStatement(contains("DELETE FROM")))
                                    .thenReturn(deleteStmt);

                            PreparedStatement insertStmt = mock(PreparedStatement.class);
                            when(insertStmt.executeUpdate())
                                    .thenThrow(new java.sql.SQLException("Insert failed"));
                            when(conn.prepareStatement(contains("INSERT INTO")))
                                    .thenReturn(insertStmt);
                        })) {

            assertThrows(
                    RuntimeException.class,
                    () ->
                            vectorDB.updateEmbeddings(
                                    "idx", "ns", "doc", "p", "id", List.of(1f, 2f, 3f), Map.of()));

            HikariDataSource ds = mockedDataSource.constructed().get(0);
            Connection conn = ds.getConnection();
            // Rollback must be called on failure.
            verify(conn).rollback();
            // Autocommit must be restored so the pooled connection is safe for the next caller.
            verify(conn).setAutoCommit(true);
        }
    }
}
