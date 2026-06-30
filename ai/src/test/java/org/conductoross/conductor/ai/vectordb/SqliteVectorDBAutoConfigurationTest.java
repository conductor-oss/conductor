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

import org.conductoross.conductor.ai.vectordb.sqlite.SqliteVectorDBAutoConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SqliteVectorDBAutoConfiguration#resolveDbPath} covering the JDBC URL
 * query-param stripping bug and various edge cases.
 */
public class SqliteVectorDBAutoConfigurationTest {

    @Test
    void testExplicitPathAlwaysWins() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath(
                        "/explicit/path.db", "jdbc:sqlite:other.db?busy_timeout=15000");
        assertEquals("/explicit/path.db", result);
    }

    @Test
    void testSimpleRelativePath() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath("", "jdbc:sqlite:conductoross.db");
        assertEquals("conductoross_vectordb.db", result);
    }

    @Test
    void testQueryParamsAreStripped() {
        // The real Conductor server URL — without stripping, the old code produced
        // "conductoross_vectordb.db?busy_timeout=15000&journal_mode=WAL" which is not a valid path.
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath(
                        "", "jdbc:sqlite:conductoross.db?busy_timeout=15000&journal_mode=WAL");
        assertEquals("conductoross_vectordb.db", result);
    }

    @Test
    void testAbsolutePathWithQueryParams() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath(
                        "", "jdbc:sqlite:/var/lib/conductor/data.db?busy_timeout=5000");
        assertEquals("/var/lib/conductor/data_vectordb.db", result);
    }

    @Test
    void testInMemoryDatasourceUsesDefault() {
        String result = SqliteVectorDBAutoConfiguration.resolveDbPath("", "jdbc:sqlite::memory:");
        assertEquals("conductor_vectordb.db", result);
    }

    @Test
    void testBlankDatasourceUrlUsesDefault() {
        String result = SqliteVectorDBAutoConfiguration.resolveDbPath("", "");
        assertEquals("conductor_vectordb.db", result);
    }

    @Test
    void testNullDatasourceUrlUsesDefault() {
        String result = SqliteVectorDBAutoConfiguration.resolveDbPath("", null);
        assertEquals("conductor_vectordb.db", result);
    }

    @Test
    void testNonSqliteUrlUsesDefault() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath(
                        "", "jdbc:postgresql://localhost:5432/conductor");
        assertEquals("conductor_vectordb.db", result);
    }

    @Test
    void testPathWithoutExtension() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath("", "jdbc:sqlite:conductoross");
        assertEquals("conductoross_vectordb.db", result);
    }

    @Test
    void testPathWithoutExtensionAndQueryParams() {
        String result =
                SqliteVectorDBAutoConfiguration.resolveDbPath(
                        "", "jdbc:sqlite:conductoross?journal_mode=WAL");
        assertEquals("conductoross_vectordb.db", result);
    }
}
