/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueManager;
import com.netflix.conductor.core.reconciliation.WorkflowRepairService;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.QueueDAO;

@Audit
@Trace
@Service
public class AdminServiceImpl implements AdminService {

    private final ConductorProperties properties;
    private final ExecutionService executionService;
    private final QueueDAO queueDAO;
    private final WorkflowRepairService workflowRepairService;
    private final EventQueueManager eventQueueManager;
    private final BuildProperties buildProperties;

    public AdminServiceImpl(
            ConductorProperties properties,
            ExecutionService executionService,
            QueueDAO queueDAO,
            Optional<WorkflowRepairService> workflowRepairService,
            Optional<EventQueueManager> eventQueueManager,
            Optional<BuildProperties> buildProperties) {
        this.properties = properties;
        this.executionService = executionService;
        this.queueDAO = queueDAO;
        this.workflowRepairService = workflowRepairService.orElse(null);
        this.eventQueueManager = eventQueueManager.orElse(null);
        this.buildProperties = buildProperties.orElse(null);
    }

    /**
     * Get all the configuration parameters.
     *
     * @return all the configuration parameters.
     */
    public Map<String, Object> getAllConfig() {
        Map<String, Object> configs = properties.getAll();
        configs.putAll(getBuildProperties());
        return configs;
    }

    /**
     * Get all build properties
     *
     * @return all the build properties.
     */
    private Map<String, Object> getBuildProperties() {
        if (buildProperties == null) return Collections.emptyMap();
        Map<String, Object> buildProps = new HashMap<>();
        buildProps.put("version", buildProperties.getVersion());
        buildProps.put("buildDate", buildProperties.getTime());
        return buildProps;
    }

    /**
     * Get the list of pending tasks for a given task type.
     *
     * @param taskType Name of the task
     * @param start Start index of pagination
     * @param count Number of entries
     * @return list of pending {@link Task}
     */
    public List<Task> getListOfPendingTask(String taskType, Integer start, Integer count) {
        List<Task> tasks = executionService.getPendingTasksForTaskType(taskType);
        int total = start + count;
        total = Math.min(tasks.size(), total);
        if (start > tasks.size()) {
            start = tasks.size();
        }
        return tasks.subList(start, total);
    }

    @Override
    public boolean verifyAndRepairWorkflowConsistency(String workflowId) {
        if (workflowRepairService == null) {
            throw new IllegalStateException(
                    WorkflowRepairService.class.getSimpleName() + " is disabled.");
        }
        return workflowRepairService.verifyAndRepairWorkflow(workflowId, true);
    }

    /**
     * Queue up the workflow for sweep.
     *
     * @param workflowId Id of the workflow
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String requeueSweep(String workflowId) {
        boolean pushed =
                queueDAO.pushIfNotExists(
                        Utils.DECIDER_QUEUE,
                        workflowId,
                        properties.getWorkflowOffsetTimeout().getSeconds());
        return pushed + "." + workflowId;
    }

    /**
     * Get registered queues.
     *
     * @param verbose `true|false` for verbose logs
     * @return map of event queues
     */
    public Map<String, ?> getEventQueues(boolean verbose) {
        if (eventQueueManager == null) {
            throw new IllegalStateException("Event processing is DISABLED");
        }
        return (verbose ? eventQueueManager.getQueueSizes() : eventQueueManager.getQueues());
    }
}
