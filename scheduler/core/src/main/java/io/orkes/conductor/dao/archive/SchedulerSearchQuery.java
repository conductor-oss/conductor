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
package io.orkes.conductor.dao.archive;

import java.util.*;

/**
 * Parses the UI search query string into structured filter criteria. The UI generates queries in
 * the format:
 *
 * <pre>
 * scheduleName IN (val1,val2) AND state IN (POLLED,EXECUTED) AND scheduledTime&gt;12345
 *     AND workflowName=my-wf AND executionId=abc-123
 * </pre>
 *
 * <p>Each clause is parsed into the corresponding field in this class.
 */
public class SchedulerSearchQuery {

    private final List<String> scheduleNames;
    private final List<String> states;
    private final Long scheduledTimeAfter;
    private final Long scheduledTimeBefore;
    private final String workflowName;
    private final String executionId;
    private final String rawQuery;

    private SchedulerSearchQuery(
            List<String> scheduleNames,
            List<String> states,
            Long scheduledTimeAfter,
            Long scheduledTimeBefore,
            String workflowName,
            String executionId,
            String rawQuery) {
        this.scheduleNames = scheduleNames;
        this.states = states;
        this.scheduledTimeAfter = scheduledTimeAfter;
        this.scheduledTimeBefore = scheduledTimeBefore;
        this.workflowName = workflowName;
        this.executionId = executionId;
        this.rawQuery = rawQuery;
    }

    public static SchedulerSearchQuery parse(String query) {
        List<String> scheduleNames = new ArrayList<>();
        List<String> states = new ArrayList<>();
        Long scheduledTimeAfter = null;
        Long scheduledTimeBefore = null;
        String workflowName = null;
        String executionId = null;

        if (query == null || query.isEmpty()) {
            return new SchedulerSearchQuery(scheduleNames, states, null, null, null, null, query);
        }

        String[] clauses = query.split("\\s+AND\\s+");
        for (String clause : clauses) {
            clause = clause.trim();
            if (clause.isEmpty()) {
                continue;
            }

            if (clause.startsWith("scheduleName IN (") && clause.endsWith(")")) {
                String csv = clause.substring("scheduleName IN (".length(), clause.length() - 1);
                for (String v : csv.split(",")) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty()) {
                        scheduleNames.add(trimmed);
                    }
                }
            } else if (clause.startsWith("state IN (") && clause.endsWith(")")) {
                String csv = clause.substring("state IN (".length(), clause.length() - 1);
                for (String v : csv.split(",")) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty()) {
                        states.add(trimmed);
                    }
                }
            } else if (clause.startsWith("scheduledTime>")) {
                String val = clause.substring("scheduledTime>".length()).trim();
                scheduledTimeAfter = Long.parseLong(val);
            } else if (clause.startsWith("scheduledTime<")) {
                String val = clause.substring("scheduledTime<".length()).trim();
                scheduledTimeBefore = Long.parseLong(val);
            } else if (clause.startsWith("workflowName=")) {
                workflowName = clause.substring("workflowName=".length()).trim();
            } else if (clause.startsWith("executionId=")) {
                executionId = clause.substring("executionId=".length()).trim();
            } else {
                // Treat unrecognized clause as a literal schedule name
                scheduleNames.add(clause);
            }
        }

        return new SchedulerSearchQuery(
                scheduleNames,
                states,
                scheduledTimeAfter,
                scheduledTimeBefore,
                workflowName,
                executionId,
                query);
    }

    public List<String> getScheduleNames() {
        return scheduleNames;
    }

    public List<String> getStates() {
        return states;
    }

    public Long getScheduledTimeAfter() {
        return scheduledTimeAfter;
    }

    public Long getScheduledTimeBefore() {
        return scheduledTimeBefore;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getExecutionId() {
        return executionId;
    }

    public boolean hasScheduleNames() {
        return !scheduleNames.isEmpty();
    }

    public boolean hasStates() {
        return !states.isEmpty();
    }

    public boolean hasTimeFilter() {
        return scheduledTimeAfter != null || scheduledTimeBefore != null;
    }

    public boolean hasWorkflowName() {
        return workflowName != null && !workflowName.isEmpty();
    }

    public boolean hasExecutionId() {
        return executionId != null && !executionId.isEmpty();
    }

    public boolean isEmpty() {
        return !hasScheduleNames()
                && !hasStates()
                && !hasTimeFilter()
                && !hasWorkflowName()
                && !hasExecutionId();
    }

    /** Sort column map from UI field names to typical DB column names. */
    private static final Map<String, String> SORT_COLUMN_MAP =
            Map.of(
                    "scheduledTime", "scheduled_time",
                    "executionTime", "execution_time",
                    "scheduleName", "schedule_name",
                    "workflowName", "workflow_name",
                    "state", "state");

    /** Resolves a UI sort field name to its DB column name. */
    public static String resolveColumnName(String uiFieldName) {
        return SORT_COLUMN_MAP.getOrDefault(uiFieldName, "scheduled_time");
    }
}
