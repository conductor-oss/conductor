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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class PostgresIndexQueryBuilderTest {

    private PostgresProperties properties = new PostgresProperties();

    private static final String ROOT_START_TIME =
            "COALESCE(workflow_hierarchy.root_start_time, workflow_index.start_time)";
    private static final String ROOT_WORKFLOW_ID =
            "COALESCE(workflow_hierarchy.root_workflow_id, workflow_index.workflow_id)";

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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldKeepAgentChildrenBelowTheirParent() throws SQLException {
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "workflow_index",
                        "classifier=agent",
                        "",
                        0,
                        15,
                        List.of("agentHierarchy:DESC", "startTime:DESC"),
                        properties);

        String query = builder.getQuery();
        assertTrue(query.startsWith("WITH RECURSIVE workflow_hierarchy"));
        assertTrue(query.contains("JOIN workflow_hierarchy parent"));
        assertTrue(query.contains("LEFT JOIN workflow_hierarchy"));
        assertTrue(query.contains("workflow_hierarchy.hierarchy_path ASC"));
    }

    @Test
    void shouldGenerateCountQueryForEmptyString() throws SQLException {
        String inputQuery = "";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals("SELECT COUNT(json_data) FROM table_name", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForNull() throws SQLException {
        String inputQuery = null;
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals("SELECT COUNT(json_data) FROM table_name", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("abc123");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForWorkflowId() throws SQLException {
        String inputQuery = "workflowId=\"abc123\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE workflow_id = ?", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("abc123");
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMPLETED", "RUNNING")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForMultipleInClause() throws SQLException {
        String inputQuery = "status IN (COMPLETED,RUNNING)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE status = ANY(?)", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMPLETED", "RUNNING")));
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("COMPLETED");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForSingleInClause() throws SQLException {
        String inputQuery = "status IN (COMPLETED)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals("SELECT COUNT(json_data) FROM table_name WHERE status = ?", generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("COMPLETED");
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForStartTimeGt() throws SQLException {
        String inputQuery = "startTime>1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE start_time > ?::TIMESTAMPTZ",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForStartTimeLt() throws SQLException {
        String inputQuery = "startTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE start_time < ?::TIMESTAMPTZ",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForUpdateTimeGt() throws SQLException {
        String inputQuery = "updateTime>1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE update_time > ?::TIMESTAMPTZ",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
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
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateCountQueryForUpdateTimeLt() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE update_time < ?::TIMESTAMPTZ",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
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
        builder.addPagingParameters(mockQuery);
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
    void shouldGenerateCountQueryForMultipleConditions() throws SQLException {
        String inputQuery =
                "workflowId=\"abc123\" AND workflowType IN (one,two) AND status IN (COMPLETED,RUNNING) AND startTime>1675701498000 AND startTime<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getCountQuery();
        assertEquals(
                "SELECT COUNT(json_data) FROM table_name WHERE start_time < ?::TIMESTAMPTZ AND start_time > ?::TIMESTAMPTZ AND status = ANY(?) AND workflow_id = ? AND workflow_type = ANY(?)",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:38:18Z");
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMPLETED", "RUNNING")));
        inOrder.verify(mockQuery).addParameter("abc123");
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("one", "two")));
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
    void shouldNotAllowInvalidColumnsOnCountQuery() throws SQLException {
        String inputQuery = "sqlInjection<1675702498000";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String expectedQuery = "SELECT COUNT(json_data) FROM table_name";
        assertEquals(expectedQuery, builder.getCountQuery());
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
    void shouldNotAllowInvalidSortColumnOnCountQuery() throws SQLException {
        String inputQuery = "updateTime<1675702498000";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT COUNT(json_data) FROM table_name WHERE update_time < ?::TIMESTAMPTZ";
        assertEquals(expectedQuery, builder.getCountQuery());
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
    void shouldAllowFullTextSearchOnCountQuery() throws SQLException {
        String freeText = "correlation-id";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", "", freeText, 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT COUNT(json_data) FROM table_name WHERE jsonb_to_tsvector('english', json_data, '[\"all\"]') @@ to_tsquery(?)";
        assertEquals(expectedQuery, builder.getCountQuery());
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

    @Test
    void shouldAllowJsonSearchOnCountQuery() throws SQLException {
        String freeText = "{\"correlationId\":\"not-the-id\"}";
        String[] query = {"sqlInjection:DESC"};
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", "", freeText, 0, 15, Arrays.asList(query), properties);
        String expectedQuery =
                "SELECT COUNT(json_data) FROM table_name WHERE json_data @> ?::JSONB";
        assertEquals(expectedQuery, builder.getCountQuery());
    }

    @Test()
    void shouldThrowIllegalArgumentExceptionWhenQueryStringIsInvalid() {
        String inputQuery =
                "workflowType IN (one,two) AND status IN (COMPLETED,RUNNING) AND startTime>1675701498000 AND xyz";

        try {
            new PostgresIndexQueryBuilder(
                    "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);

            fail("should have failed since xyz does not conform to expected format");
        } catch (IllegalArgumentException e) {
            assertEquals("Incorrectly formatted query string: xyz", e.getMessage());
        }
    }

    @Test
    void shouldGenerateQueryWithWildcardPrefix() throws SQLException {
        String inputQuery = "workflowType=abc*";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE workflow_type LIKE ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("abc%");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryWithWildcardContains() throws SQLException {
        String inputQuery = "correlationId=\"*order*\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE correlation_id LIKE ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("%order%");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateExactMatchQueryWhenNoWildcard() throws SQLException {
        String inputQuery = "workflowType=abc";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE workflow_type = ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("abc");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldNotExpandWildcardInINClause() throws SQLException {
        String inputQuery = "status IN (COMP*,RUNNING)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE status = ANY(?) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("COMP*", "RUNNING")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForClassifier() throws SQLException {
        String inputQuery = "classifier=\"agent\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE classifier = ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("agent");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForClassifierInClause() throws SQLException {
        String inputQuery = "classifier IN (agent,pipeline)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE classifier = ANY(?) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("agent", "pipeline")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldMatchLegacyNullRowsForUntaggedClassifier() throws SQLException {
        // Rows indexed before the classifier column existed are untagged plain workflows;
        // filtering for the "workflow" token must also match those NULL rows.
        String inputQuery = "classifier=\"workflow\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE (classifier = ? OR classifier IS NULL) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("workflow");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldMatchLegacyNullRowsForUntaggedClassifierInClause() throws SQLException {
        String inputQuery = "classifier IN (agent,workflow)";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE (classifier = ANY(?) OR classifier IS NULL) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter(new ArrayList<>(List.of("agent", "workflow")));
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForParentWorkflowId() throws SQLException {
        String inputQuery = "parentWorkflowId=\"\"";
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data::TEXT FROM table_name WHERE parent_workflow_id = ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldSortWorkflowsOnEndTime() throws SQLException {
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "workflow_index", "", "", 0, 15, List.of("endTime:DESC"), properties);
        assertEquals(
                "SELECT json_data::TEXT FROM workflow_index ORDER BY end_time DESC LIMIT ? OFFSET ?",
                builder.getQuery());
    }

    @Test
    void shouldSortTasksOnEndTime() throws SQLException {
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "task_index", "", "", 0, 15, List.of("endTime:DESC"), properties);
        assertEquals(
                "SELECT json_data::TEXT FROM task_index ORDER BY end_time DESC LIMIT ? OFFSET ?",
                builder.getQuery());
    }

    // The End Time date-range picker emits `endTime>`/`endTime<` clauses. Before end_time was an
    // indexed column these were dropped by the same allow-list that dropped the sort, so the
    // picker silently returned unfiltered results.
    @Test
    void shouldGenerateQueryForEndTimeRangeInCanonicalUtc() throws SQLException {
        PostgresIndexQueryBuilder builder =
                new PostgresIndexQueryBuilder(
                        "workflow_index",
                        "endTime>1675702498000",
                        "",
                        0,
                        15,
                        new ArrayList<>(),
                        properties);
        assertEquals(
                "SELECT json_data::TEXT FROM workflow_index WHERE end_time > ?::TIMESTAMPTZ LIMIT ? OFFSET ?",
                builder.getQuery());
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06T16:54:58Z");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    /** Builds the agent-grouped query the search endpoint produces for the given caller sort. */
    private String agentQuery(String... sortTerms) throws SQLException {
        List<String> sort = new ArrayList<>();
        sort.add("agentHierarchy:DESC");
        sort.addAll(List.of(sortTerms));
        return new PostgresIndexQueryBuilder(
                        "workflow_index", "classifier=agent", "", 0, 15, sort, properties)
                .getQuery();
    }

    private static String orderBy(String query) {
        int index = query.indexOf(" ORDER BY ");
        assertTrue(index >= 0, "Query has no ORDER BY: " + query);
        return query.substring(index + " ORDER BY ".length(), query.indexOf(" LIMIT ?"));
    }

    // hierarchy_path is unique per row, so it is a total order on its own. Anything the caller
    // asked for that lands after it is unreachable -- which is exactly how the caller's sort came
    // to be silently ignored. Nothing may follow it.
    @Test
    void agentHierarchySortEndsWithHierarchyPath() throws SQLException {
        for (String sort : List.of("startTime:ASC", "startTime:DESC", "workflowType:ASC")) {
            assertTrue(
                    orderBy(agentQuery(sort)).endsWith("workflow_hierarchy.hierarchy_path ASC"),
                    "Caller key must not follow hierarchy_path for " + sort);
        }
    }

    // The caller's direction must reach the SQL rather than being fixed at DESC.
    @Test
    void agentHierarchySortHonoursCallerDirection() throws SQLException {
        String ascending = orderBy(agentQuery("startTime:ASC"));
        String descending = orderBy(agentQuery("startTime:DESC"));

        assertTrue(ascending.startsWith(ROOT_START_TIME + " ASC"));
        assertTrue(descending.startsWith(ROOT_START_TIME + " DESC"));
        assertNotEquals(ascending, descending);
    }

    // The caller's key is read off the root row so a whole group shares it; the bare column would
    // order individual executions instead, splitting groups apart.
    @Test
    void agentHierarchySortReadsCallerKeyFromRootRow() throws SQLException {
        String order = orderBy(agentQuery("workflowType:ASC"));

        assertTrue(
                order.startsWith(
                        "COALESCE(workflow_hierarchy.root_workflow_type,"
                                + " workflow_index.workflow_type) ASC"));
        assertTrue(
                agentQuery("workflowType:ASC").contains("root_workflow_type"),
                "CTE must project the requested root column");
    }

    // Every ORDER BY column must be table-qualified: the hierarchy join puts a second workflow_id
    // in scope, and a bare reference made the database reject the query outright.
    @Test
    void agentHierarchySortQualifiesWorkflowIdAgainstAmbiguity() throws SQLException {
        String order = orderBy(agentQuery("workflowId:DESC"));

        assertFalse(
                order.matches(".*(^|[ ,(])workflow_id[ ,)].*"),
                "Bare workflow_id in ORDER BY is ambiguous: " + order);
        assertTrue(order.startsWith(ROOT_WORKFLOW_ID + " DESC"));
    }

    // With no caller sort the endpoint sends the marker alone, which must still order groups.
    @Test
    void agentHierarchySortDefaultsToRootStartTime() throws SQLException {
        assertEquals(
                ROOT_START_TIME
                        + " DESC, "
                        + ROOT_WORKFLOW_ID
                        + " ASC, CASE WHEN workflow_hierarchy.hier_workflow_id IS NULL THEN 1 ELSE"
                        + " 0 END ASC, workflow_hierarchy.hierarchy_path ASC",
                orderBy(agentQuery()));
    }

    // The marker is internal and must never reach the SQL as a column.
    @Test
    void agentHierarchyMarkerIsNotEmittedAsAColumn() throws SQLException {
        assertFalse(agentQuery("startTime:ASC").contains("agent_hierarchy"));
    }

    // Grouping applies only to the workflow index; task searches must be untouched.
    @Test
    void agentHierarchySortIsIgnoredForTaskIndex() throws SQLException {
        String query =
                new PostgresIndexQueryBuilder(
                                "task_index",
                                "",
                                "",
                                0,
                                15,
                                List.of("agentHierarchy:DESC", "startTime:ASC"),
                                properties)
                        .getQuery();

        assertFalse(query.contains("workflow_hierarchy"));
        assertEquals("start_time ASC", orderBy(query));
    }
}
