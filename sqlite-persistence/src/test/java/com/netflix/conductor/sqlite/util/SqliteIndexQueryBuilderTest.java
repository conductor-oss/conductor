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
package com.netflix.conductor.sqlite.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.netflix.conductor.sqlite.config.SqliteProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class SqliteIndexQueryBuilderTest {

    private SqliteProperties properties = new SqliteProperties();

    @Test
    void shouldGenerateQueryForEmptyString() throws SQLException {
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", "", "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals("SELECT json_data FROM table_name LIMIT ? OFFSET ?", generatedQuery);
    }

    @Test
    void shouldKeepAgentChildrenBelowTheirParent() throws SQLException {
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
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
    void shouldGenerateQueryForExactMatch() throws SQLException {
        String inputQuery = "workflowId=\"abc123\"";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE workflow_id = ? LIMIT ? OFFSET ?",
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
    void shouldGenerateQueryWithWildcardPrefix() throws SQLException {
        String inputQuery = "workflowType=abc*";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE lower(workflow_type) LIKE lower(?) LIMIT ? OFFSET ?",
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
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE lower(correlation_id) LIKE lower(?) LIMIT ? OFFSET ?",
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
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE workflow_type = ? LIMIT ? OFFSET ?",
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
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE status IN (?,?) LIMIT ? OFFSET ?",
                generatedQuery);
    }

    @Test
    void shouldGenerateQueryForClassifier() throws SQLException {
        String inputQuery = "classifier=\"agent\"";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE classifier = ? LIMIT ? OFFSET ?",
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
    void shouldMatchLegacyNullRowsForUntaggedClassifier() throws SQLException {
        // Rows indexed before the classifier column existed are untagged plain workflows;
        // filtering for the "workflow" token must also match those NULL rows.
        String inputQuery = "classifier=\"workflow\"";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE (classifier = ? OR classifier IS NULL) LIMIT ? OFFSET ?",
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
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE (classifier IN (?,?) OR classifier IS NULL) LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("agent");
        inOrder.verify(mockQuery).addParameter("workflow");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    // Issue #1497: the bound for a *_time comparison must be rendered in the same canonical UTC
    // text format the write path now uses (yyyy-MM-dd HH:mm:ss.SSS), and the column must NOT be
    // wrapped in datetime() -- datetime() truncates to whole seconds, so 'start_time < ?' wrongly
    // excluded a row stored at exactly '...03.000' ('...03.000' < '...03' is false: longer
    // string, same prefix).
    @Test
    void shouldGenerateQueryForStartTimeGreaterThanInCanonicalUtc() throws SQLException {
        String inputQuery = "startTime>1675702498000";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE start_time > ? LIMIT ? OFFSET ?",
                generatedQuery);
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06 16:54:58.000");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }

    @Test
    void shouldGenerateQueryForParentWorkflowId() throws SQLException {
        String inputQuery = "parentWorkflowId=\"\"";
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "table_name", inputQuery, "", 0, 15, new ArrayList<>(), properties);
        String generatedQuery = builder.getQuery();
        assertEquals(
                "SELECT json_data FROM table_name WHERE parent_workflow_id = ? LIMIT ? OFFSET ?",
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
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "workflow_index", "", "", 0, 15, List.of("endTime:DESC"), properties);
        assertEquals(
                "SELECT json_data FROM workflow_index ORDER BY end_time DESC LIMIT ? OFFSET ?",
                builder.getQuery());
    }

    @Test
    void shouldSortTasksOnEndTime() throws SQLException {
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "task_index", "", "", 0, 15, List.of("endTime:DESC"), properties);
        assertEquals(
                "SELECT json_data FROM task_index ORDER BY end_time DESC LIMIT ? OFFSET ?",
                builder.getQuery());
    }

    // The End Time date-range picker emits `endTime>`/`endTime<` clauses. Before end_time was an
    // indexed column these were dropped by the same allow-list that dropped the sort, so the
    // picker silently returned unfiltered results.
    @Test
    void shouldGenerateQueryForEndTimeRangeInCanonicalUtc() throws SQLException {
        SqliteIndexQueryBuilder builder =
                new SqliteIndexQueryBuilder(
                        "workflow_index",
                        "endTime>1675702498000",
                        "",
                        0,
                        15,
                        new ArrayList<>(),
                        properties);
        assertEquals(
                "SELECT json_data FROM workflow_index WHERE end_time > ? LIMIT ? OFFSET ?",
                builder.getQuery());
        Query mockQuery = mock(Query.class);
        builder.addParameters(mockQuery);
        builder.addPagingParameters(mockQuery);
        InOrder inOrder = Mockito.inOrder(mockQuery);
        inOrder.verify(mockQuery).addParameter("2023-02-06 16:54:58.000");
        inOrder.verify(mockQuery).addParameter(15);
        inOrder.verify(mockQuery).addParameter(0);
        verifyNoMoreInteractions(mockQuery);
    }
}
