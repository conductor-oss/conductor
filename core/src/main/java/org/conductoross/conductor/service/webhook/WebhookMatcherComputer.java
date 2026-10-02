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
package org.conductoross.conductor.service.webhook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.dao.MetadataDAO;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;
import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WEBHOOK_DELIMITER;

/**
 * Matcher computation shared by every {@link org.conductoross.conductor.dao.webhook.WebhookDAO}
 * impl. Must be identical across impls — a WorkflowDef edit produces the same matcher set
 * regardless of which persistence module is backing {@code WebhookDAO}.
 *
 * <p>Each backing persists only the {@code workflowName → version} target snapshot (taken at
 * config-create time so any expression evaluation is preserved). The actual {@code matches}
 * criteria are recomputed from {@link MetadataDAO} on every {@code getMatchers()} call so
 * WorkflowDef updates take effect without re-registering the webhook.
 *
 * <p><b>Performance note.</b> {@link #compute} fetches one {@link MetadataDAO#getWorkflowDef} per
 * target workflow per invocation. This is the read hot path for inbound webhook events.
 *
 * <ul>
 *   <li>On Cassandra, {@code CacheableMetadataDAO} (in {@code cassandra-persistence}) wraps the
 *       primary DAO so these fetches resolve from a process-local cache.
 *   <li>On Postgres / MySQL / SQLite / Redis, there is no equivalent wrapper today — each fetch is
 *       a backend round-trip. Acceptable when a webhook targets a small number of workflows;
 *       revisit if matcher fanout grows or if traffic-per-webhook makes the cost visible in
 *       profiles.
 * </ul>
 */
public final class WebhookMatcherComputer {

    private WebhookMatcherComputer() {}

    /**
     * Build the matcher map for a single webhook from its persisted target-workflow snapshot.
     *
     * @param targets workflowName → workflowVersion snapshot stored by the DAO
     * @param metadataDAO source of truth for current WorkflowDef state
     * @return {@code workflowName;version;taskRefName → matches} for every {@code
     *     WAIT_FOR_WEBHOOK}/{@code WAIT} task with a non-empty {@code matches} input parameter.
     *     Empty (immutable) if {@code targets} is null or empty.
     */
    public static Map<String, Map<String, Object>> compute(
            Map<String, Integer> targets, MetadataDAO metadataDAO) {
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> computed = new HashMap<>();
        targets.forEach(
                (workflowName, wfVersion) -> {
                    Optional<WorkflowDef> def = metadataDAO.getWorkflowDef(workflowName, wfVersion);
                    if (def.isEmpty()) {
                        return;
                    }
                    for (WorkflowTask task : def.get().collectTasks()) {
                        String type = task.getType();
                        if (!WAIT_FOR_WEBHOOK.equals(type)
                                && !TaskType.WAIT.toString().equals(type)) {
                            continue;
                        }
                        Object raw = task.getInputParameters().get("matches");
                        if (raw instanceof Map<?, ?> m && !CollectionUtils.isEmpty(m)) {
                            // 'matches' is authored in JSON / WorkflowDef DSL — keys MUST be
                            // strings.
                            // A non-string key means a corrupted/illegal def; skip it rather than
                            // ClassCastException downstream when callers iterate the map.
                            boolean allStringKeys =
                                    m.keySet().stream().allMatch(k -> k instanceof String);
                            if (!allStringKeys) {
                                continue;
                            }
                            Map<String, Object> typed = new HashMap<>(m.size());
                            m.forEach((k, v) -> typed.put((String) k, v));
                            String key =
                                    workflowName
                                            + WEBHOOK_DELIMITER
                                            + wfVersion
                                            + WEBHOOK_DELIMITER
                                            + task.getTaskReferenceName();
                            computed.put(key, typed);
                        }
                    }
                });
        return computed;
    }
}
