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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
import com.netflix.conductor.sqlite.config.SqliteProperties;

public class SqliteIndexQueryBuilder {

    private final String table;
    private final String freeText;
    private final int start;
    private final int count;
    private final List<String> sort;
    private final List<Condition> conditions = new ArrayList<>();

    private boolean allowJsonQueries;
    private boolean allowFullTextQueries;

    private static final String[] VALID_FIELDS = {
        "workflow_id",
        "correlation_id",
        "workflow_type",
        "start_time",
        "end_time",
        "status",
        "task_id",
        "task_type",
        "task_def_name",
        "update_time",
        "json_data",
        "parent_workflow_id",
        "classifier"
    };

    private static final String[] VALID_SORT_ORDER = {"ASC", "DESC"};

    /** Internal sort marker (not a column) requesting agent root/descendant grouping. */
    private static final String AGENT_HIERARCHY_SORT = "agent_hierarchy";

    /** {@code workflow_index} columns the hierarchy CTE can carry as a group sort key. */
    private static final String[] HIERARCHY_ROOT_FIELDS = {
        "workflow_id",
        "correlation_id",
        "workflow_type",
        "start_time",
        "end_time",
        "status",
        "update_time",
        "parent_workflow_id",
        "classifier"
    };

    private static class Condition {
        private String attribute;
        private String operator;
        private List<String> values;
        private final String CONDITION_REGEX = "([a-zA-Z]+)\\s?(=|>|<|IN)\\s?(.*)";

        /** Must match {@code SqliteIndexDAO}'s write-path format exactly. */
        private static final DateTimeFormatter SQLITE_UTC_TIMESTAMP =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

        public Condition() {}

        public Condition(String query) {
            Pattern conditionRegex = Pattern.compile(CONDITION_REGEX);
            Matcher conditionMatcher = conditionRegex.matcher(query);
            if (conditionMatcher.find()) {
                String[] valueArr = conditionMatcher.group(3).replaceAll("[\"'()]", "").split(",");
                ArrayList<String> values = new ArrayList<>(Arrays.asList(valueArr));
                this.attribute = camelToSnake(conditionMatcher.group(1));
                this.values = values;
                this.operator = getOperator(conditionMatcher.group(2));
                if (this.attribute.endsWith("_time")) {
                    values.set(0, millisToUtc(values.get(0)));
                }
            } else {
                throw new IllegalArgumentException("Incorrectly formatted query string: " + query);
            }
        }

        public String getQueryFragment() {
            if (operator.equals("IN")) {
                // Create proper IN clause for SQLite
                String inClause =
                        attribute
                                + " IN ("
                                + String.join(",", Collections.nCopies(values.size(), "?"))
                                + ")";
                if (classifierMatchesUntagged()) {
                    return "(" + inClause + " OR " + attribute + " IS NULL)";
                }
                return inClause;
            } else if (operator.equals("MATCH")) {
                // SQLite FTS5 full-text search
                return "json_data MATCH ?";
            } else if (operator.equals("JSON_CONTAINS")) {
                // SQLite JSON1 extension query
                return "json_extract(json_data, ?) IS NOT NULL";
            } else if (operator.equals("LIKE")) {
                return "lower(" + attribute + ") LIKE ?";
            } else {
                if (attribute.endsWith("_time")) {
                    // No datetime() wrapper: it truncates to whole seconds, so
                    // 'start_time < ?' wrongly excluded a row stored at exactly '...03.000'
                    // ('...03.000' < '...03' is false -- longer string, same prefix). With
                    // identical canonical-UTC text on both sides the comparison is exact
                    // (issue #1497).
                    return attribute + " " + operator + " ?";
                } else if (operator.equals("=")
                        && values.size() == 1
                        && values.get(0).contains("*")) {
                    return "lower(" + attribute + ") LIKE lower(?)";
                } else if (operator.equals("=") && classifierMatchesUntagged()) {
                    return "(" + attribute + " = ? OR " + attribute + " IS NULL)";
                } else {
                    return attribute + " " + operator + " ?";
                }
            }
        }

        /**
         * Rows indexed before the classifier column existed have a NULL classifier but are
         * semantically untagged, i.e. plain workflows. When a filter asks for the untagged token
         * ({@link WorkflowClassifier#WORKFLOW}), widen the predicate to also match those legacy
         * NULL rows.
         */
        private boolean classifierMatchesUntagged() {
            return "classifier".equals(attribute)
                    && values != null
                    && values.stream().anyMatch(WorkflowClassifier.WORKFLOW::equalsIgnoreCase);
        }

        private String getOperator(String op) {
            if (op.equals("IN") && values.size() == 1) {
                return "=";
            }
            return op;
        }

        public void addParameter(Query q) throws SQLException {
            if (values.size() > 1) {
                // For IN clause, add each value separately
                for (String value : values) {
                    q.addParameter(value);
                }
            } else {
                String val = values.get(0);
                if (val.contains("*")) {
                    val = val.replace("*", "%");
                }
                q.addParameter(val);
            }
        }

        private String millisToUtc(String millis) {
            return SQLITE_UTC_TIMESTAMP.format(Instant.ofEpochMilli(Long.parseLong(millis)));
        }

        private boolean isValid() {
            return Arrays.asList(VALID_FIELDS).contains(attribute);
        }

        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }

    public SqliteIndexQueryBuilder(
            String table,
            String query,
            String freeText,
            int start,
            int count,
            List<String> sort,
            SqliteProperties properties) {
        this.table = table;
        this.freeText = freeText;
        this.start = start;
        this.count = count;
        this.sort = sort != null ? sort : Collections.emptyList();
        this.allowFullTextQueries = true;
        this.allowJsonQueries = true;
        this.parseQuery(query);
        this.parseFreeText(freeText);
    }

    public String getQuery() {
        return getQuery("json_data");
    }

    public String getQuery(String selectColumn) {
        String queryString = "";
        List<Condition> validConditions =
                conditions.stream().filter(c -> c.isValid()).collect(Collectors.toList());
        if (validConditions.size() > 0) {
            queryString =
                    " WHERE "
                            + String.join(
                                    " AND ",
                                    validConditions.stream()
                                            .map(c -> c.getQueryFragment())
                                            .collect(Collectors.toList()));
        }
        return hierarchyCte()
                + "SELECT "
                + selectColumn
                + " FROM "
                + table
                + hierarchyJoin()
                + queryString
                + getSort()
                + " LIMIT ? OFFSET ?";
    }

    public String getCountQuery() {
        String queryString = "";
        List<Condition> validConditions =
                conditions.stream().filter(c -> c.isValid()).collect(Collectors.toList());
        if (validConditions.size() > 0) {
            queryString =
                    " WHERE "
                            + String.join(
                                    " AND ",
                                    validConditions.stream()
                                            .map(c -> c.getQueryFragment())
                                            .collect(Collectors.toList()));
        }
        return "SELECT COUNT(*) FROM " + table + queryString;
    }

    public void addParameters(Query q) throws SQLException {
        for (Condition condition : conditions) {
            if (condition.isValid()) {
                condition.addParameter(q);
            }
        }
    }

    public void addPagingParameters(Query q) throws SQLException {
        q.addParameter(count);
        q.addParameter(start);
    }

    private void parseQuery(String query) {
        if (!StringUtils.isEmpty(query)) {
            for (String s : query.split(" AND ")) {
                conditions.add(new Condition(s));
            }
            Collections.sort(conditions, Comparator.comparing(Condition::getQueryFragment));
        }
    }

    private void parseFreeText(String freeText) {
        if (!StringUtils.isEmpty(freeText) && !freeText.equals("*")) {
            Condition cond = new Condition();
            cond.setAttribute("json_data");
            cond.setOperator("LIKE");
            String[] values = {freeText};
            cond.setValues(
                    Arrays.stream(values)
                            .map(v -> "%" + v.toLowerCase() + "%")
                            .collect(Collectors.toList()));
            conditions.add(cond);
        }
    }

    private String getSort() {
        boolean hierarchical = hasAgentHierarchySort();
        ArrayList<String> sortConds = new ArrayList<>();
        // Caller-requested keys, rewritten to read off the group's root row when grouping agents.
        ArrayList<String> groupKeys = new ArrayList<>();
        String hierarchyOrder = null;

        for (String s : sort) {
            String[] splitCond = s.split(":");
            if (splitCond.length != 2) {
                continue;
            }
            String attribute = camelToSnake(splitCond[0]);
            String order = splitCond[1].toUpperCase();
            if (!Arrays.asList(VALID_SORT_ORDER).contains(order)) {
                continue;
            }
            if (AGENT_HIERARCHY_SORT.equals(attribute)) {
                // A marker rather than a column: it only supplies the default group order.
                hierarchyOrder = order;
            } else if (!Arrays.asList(VALID_FIELDS).contains(attribute)) {
                continue;
            } else if (!hierarchical) {
                sortConds.add(attribute + " " + order);
            } else if (Arrays.asList(HIERARCHY_ROOT_FIELDS).contains(attribute)) {
                groupKeys.add(rootColumn(attribute) + " " + order);
            }
        }

        if (hierarchical && hierarchyOrder != null) {
            sortConds.addAll(agentHierarchySort(groupKeys, hierarchyOrder));
        }

        if (sortConds.size() > 0) {
            return " ORDER BY " + String.join(", ", sortConds);
        }
        return "";
    }

    /**
     * Orders agent executions so a root and all of its descendants stay together, with the groups
     * themselves ordered by the caller's requested sort. Each caller key is read off the root row
     * (see {@link #rootColumn}) so every member of a group shares the same value; that makes the
     * group, rather than the individual execution, the unit being sorted. {@code hierarchy_path}
     * then imposes depth-first order strictly within a group. The recursive CTE makes this a
     * database operation before pagination, so a nested sub-agent cannot be separated from its
     * ancestors by another execution page.
     *
     * <p>{@code hierarchy_path} is unique per row, so it must stay last: it is a total order on its
     * own, and any caller key placed after it would be unreachable and silently ignored.
     */
    private List<String> agentHierarchySort(List<String> groupKeys, String defaultOrder) {
        List<String> terms = new ArrayList<>();
        if (groupKeys.isEmpty()) {
            terms.add(rootColumn("start_time") + " " + defaultOrder);
        } else {
            terms.addAll(groupKeys);
        }
        // Stable tiebreak between distinct groups whose sort keys compare equal. Skipped when the
        // caller already sorts on the root id, which is unique per group and so already total.
        String rootId = rootColumn("workflow_id");
        if (terms.stream().noneMatch(t -> t.startsWith(rootId + " "))) {
            terms.add(rootId + " ASC");
        }
        terms.add("CASE WHEN workflow_hierarchy.hier_workflow_id IS NULL THEN 1 ELSE 0 END ASC");
        terms.add("workflow_hierarchy.hierarchy_path ASC");
        return terms;
    }

    /**
     * Resolves a column against the group's root row, falling back to the execution's own value
     * when it has no hierarchy entry (such a row is its own group). The CTE prefixes every
     * projected column so nothing it exposes collides with a {@code workflow_index} column name.
     */
    private String rootColumn(String attribute) {
        return "COALESCE(workflow_hierarchy.root_"
                + attribute
                + ", "
                + table
                + "."
                + attribute
                + ")";
    }

    /**
     * Root-row columns the hierarchy CTE must carry: the two used unconditionally for the default
     * order and the tiebreak, plus whatever the caller asked to sort by.
     */
    private List<String> hierarchyRootFields() {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        fields.add("workflow_id");
        fields.add("start_time");
        for (String s : sort) {
            String[] splitCond = s.split(":");
            if (splitCond.length == 2) {
                String attribute = camelToSnake(splitCond[0]);
                if (Arrays.asList(HIERARCHY_ROOT_FIELDS).contains(attribute)) {
                    fields.add(attribute);
                }
            }
        }
        return new ArrayList<>(fields);
    }

    private boolean hasAgentHierarchySort() {
        return "workflow_index".equals(table)
                && sort.stream()
                        .map(s -> s.split(":", 2)[0])
                        .map(SqliteIndexQueryBuilder::camelToSnake)
                        .anyMatch(AGENT_HIERARCHY_SORT::equals);
    }

    private String hierarchyCte() {
        if (!hasAgentHierarchySort()) {
            return "";
        }
        List<String> rootFields = hierarchyRootFields();
        String cteColumns =
                rootFields.stream().map(f -> "root_" + f).collect(Collectors.joining(", "));
        String baseColumns = String.join(", ", rootFields);
        String recursiveColumns =
                rootFields.stream().map(f -> "parent.root_" + f).collect(Collectors.joining(", "));
        return "WITH RECURSIVE workflow_hierarchy(hier_workflow_id, hierarchy_path, "
                + cteColumns
                + ") AS ("
                + " SELECT workflow_id, '|' || workflow_id || '|', "
                + baseColumns
                + " FROM workflow_index WHERE parent_workflow_id IS NULL OR parent_workflow_id = ''"
                + " UNION ALL"
                + " SELECT child.workflow_id, parent.hierarchy_path || child.workflow_id || '|', "
                + recursiveColumns
                + " FROM workflow_index child JOIN workflow_hierarchy parent"
                + " ON child.parent_workflow_id = parent.hier_workflow_id"
                + " WHERE instr(parent.hierarchy_path, '|' || child.workflow_id || '|') = 0"
                + ") ";
    }

    private String hierarchyJoin() {
        return hasAgentHierarchySort()
                ? " LEFT JOIN workflow_hierarchy ON workflow_hierarchy.hier_workflow_id = "
                        + table
                        + ".workflow_id"
                : "";
    }

    private static String camelToSnake(String camel) {
        return camel.replaceAll("\\B([A-Z])", "_$1").toLowerCase();
    }
}
