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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.postgres.config.PostgresProperties;

public class PostgresIndexQueryBuilder {

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
        "status",
        "task_id",
        "task_type",
        "task_def_name",
        "update_time",
        "json_data",
        "jsonb_to_tsvector('english', json_data, '[\"all\"]')"
    };

    private static final String[] VALID_SORT_ORDER = {"ASC", "DESC"};

    private static class Condition {
        private String attribute;
        private String operator;
        private List<String> values;
        private final String CONDITION_REGEX = "([a-zA-Z]+)\\s?(=|>|<|IN)\\s?(.*)";

        public Condition() {}

        public Condition(String query) {
            Pattern conditionRegex = Pattern.compile(CONDITION_REGEX);
            Matcher conditionMatcher = conditionRegex.matcher(query);
            if (conditionMatcher.find()) {
                String[] valueArr = conditionMatcher.group(3).replaceAll("[\"()]", "").split(",");
                ArrayList<String> values = new ArrayList<>(Arrays.asList(valueArr));
                this.attribute = camelToSnake(conditionMatcher.group(1));
                this.values = values;
                this.operator = getOperator(conditionMatcher.group(2));
                if (this.attribute.endsWith("_time")) {
                    values.set(0, millisToUtc(values.get(0)));
                }
            }
        }

        public String getQueryFragment() {
            if (operator.equals("IN")) {
                return attribute + " = ANY(?)";
            } else if (operator.equals("@@")) {
                return attribute + " @@ to_tsquery(?)";
            } else if (operator.equals("@>")) {
                return attribute + " @> ?::JSONB";
            } else {
                if (attribute.endsWith("_time")) {
                    return attribute + " " + operator + " ?::TIMESTAMPTZ";
                } else {
                    return attribute + " " + operator + " ?";
                }
            }
        }

        private String getOperator(String op) {
            if (op.equals("IN") && values.size() == 1) {
                return "=";
            }
            return op;
        }

        public void addParameter(Query q) throws SQLException {
            if (values.size() > 1) {
                q.addParameter(values);
            } else {
                q.addParameter(values.get(0));
            }
        }

        private String millisToUtc(String millis) {
            Long startTimeMilli = Long.parseLong(millis);
            ZonedDateTime startDate =
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTimeMilli), ZoneOffset.UTC);
            return DateTimeFormatter.ISO_DATE_TIME.format(startDate);
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

    public PostgresIndexQueryBuilder(
            String table,
            String query,
            String freeText,
            int start,
            int count,
            List<String> sort,
            PostgresProperties properties) {
        this.table = table;
        this.freeText = freeText;
        this.start = start;
        this.count = count;
        this.sort = sort;
        this.allowFullTextQueries = properties.getAllowFullTextQueries();
        this.allowJsonQueries = properties.getAllowJsonQueries();
        this.parseQuery(query);
        this.parseFreeText(freeText);
    }

    public String getQuery() {
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
        return "SELECT json_data::TEXT FROM "
                + table
                + queryString
                + getSort()
                + " LIMIT ? OFFSET ?";
    }

    public void addParameters(Query q) throws SQLException {
        for (Condition condition : conditions) {
            condition.addParameter(q);
        }
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
            if (allowJsonQueries && freeText.startsWith("{") && freeText.endsWith("}")) {
                Condition cond = new Condition();
                cond.setAttribute("json_data");
                cond.setOperator("@>");
                String[] values = {freeText};
                cond.setValues(Arrays.asList(values));
                conditions.add(cond);
            } else if (allowFullTextQueries) {
                Condition cond = new Condition();
                cond.setAttribute("jsonb_to_tsvector('english', json_data, '[\"all\"]')");
                cond.setOperator("@@");
                String[] values = {freeText};
                cond.setValues(Arrays.asList(values));
                conditions.add(cond);
            }
        }
    }

    private String getSort() {
        ArrayList<String> sortConds = new ArrayList<>();
        for (String s : sort) {
            String[] splitCond = s.split(":");
            if (splitCond.length == 2) {
                String attribute = camelToSnake(splitCond[0]);
                String order = splitCond[1].toUpperCase();
                if (Arrays.asList(VALID_FIELDS).contains(attribute)
                        && Arrays.asList(VALID_SORT_ORDER).contains(order)) {
                    sortConds.add(attribute + " " + order);
                }
            }
        }

        if (sortConds.size() > 0) {
            return " ORDER BY " + String.join(", ", sortConds);
        }
        return "";
    }

    private static String camelToSnake(String camel) {
        return camel.replaceAll("\\B([A-Z])", "_$1").toLowerCase();
    }
}
