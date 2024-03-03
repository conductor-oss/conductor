/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.postgres.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.netflix.conductor.postgres.config.PostgresProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class PostgresIndexQueryBuilderTest {

    private PostgresProperties properties = new PostgresProperties();

    @Test
    void shouldGenerateQueryForEmptyString() throws SQLException {
        String inputQuery = "";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals("SELECT json_data::TEXT FROM table_name LIMIT ? OFFSET ?", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForNull() throws SQLException {
        String inputQuery = null;
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals("SELECT json_data::TEXT FROM table_name LIMIT ? OFFSET ?", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForWorkflowId() throws SQLException {
        String inputQuery = "workflowId=\"abc123\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE workflow_id = ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("abc123");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForMultipleInClause() throws SQLException {
        String inputQuery = "status IN (COMPLETED,RUNNING)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE status = ANY(?) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMPLETED", "RUNNING")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForSingleInClause() throws SQLException {
        String inputQuery = "status IN (COMPLETED)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE status = ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("COMPLETED");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForStartTimeGt() throws SQLException {
        String inputQuery = "startTime>1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE start_time > ?::TIMESTAMPTZ LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForStartTimeLt() throws SQLException {
        String inputQuery = "startTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE start_time < ?::TIMESTAMPTZ LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForUpdateTimeGt() throws SQLException {
        String inputQuery = "updateTime>1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE update_time > ?::TIMESTAMPTZ LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForUpdateTimeLt() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE update_time < ?::TIMESTAMPTZ LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForMultipleConditions() throws SQLException {
        String inputQuery =
                "workflowId=\"abc123\" AND workflowType IN (one,two) AND status IN (COMPLETED,RUNNING) AND startTime>1675701498000 AND startTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE start_time < ?::TIMESTAMPTZ AND start_time > ?::TIMESTAMPTZ AND status = ANY(?) AND workflow_id = ? AND workflow_type = ANY(?) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:38:18Z");
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMPLETED", "RUNNING")));
        inOrder.verify(mockQuery).addParameter("abc123");
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("one", "two")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateOrderBy() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        String[] query = {"updateTime:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT json_data::TEXT FROM table_name WHERE update_time < ?::TIMESTAMPTZ ORDER BY update_time DESC LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }

    @Test
    void shouldGenerateOrderByMultiple() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        String[] query = {"updateTime:DESC", "correlationId:ASC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT json_data::TEXT FROM table_name WHERE update_time < ?::TIMESTAMPTZ ORDER BY update_time DESC, correlation_id ASC LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }

    @Test
    void shouldNotAllowInvalidColumns() throws SQLException {
        String inputQuery = "sqlInjection<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String expectedQuery = "SELECT json_data::TEXT FROM table_name LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }

    @Test
    void shouldNotAllowInvalidSortColumn() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT json_data::TEXT FROM table_name WHERE update_time < ?::TIMESTAMPTZ LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }

    @Test
    void shouldAllowFullTextSearch() throws SQLException {
        String freeText = "correlation-id";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", "", freeText, 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT json_data::TEXT FROM table_name WHERE jsonb_to_tsvector('english', json_data, '[\"all\"]') @@ to_tsquery(?) LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }

    @Test
    void shouldAllowJsonSearch() throws SQLException {
        String freeText = "{\"correlationId\":\"not-the-id\"}";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", "", freeText, 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT json_data::TEXT FROM table_name WHERE json_data @> ?::JSONB LIMIT ? OFFSET ?";
        assertEquals(expectedQuery, builder.getQuery());
    }
}
