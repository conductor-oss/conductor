/*
 * Copyright 2022 Conductor Authors.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueManager;
import com.netflix.conductor.core.reconciliation.WorkflowRepairService;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Audit
@Trace
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminServiceImpl.class);

    // ---- reindex state ----
    private enum ReindexState {
        IDLE, RUNNING, COMPLETED, FAILED
    }

    private final AtomicReference<ReindexState> reindexState =
            new AtomicReference<>(ReindexState.IDLE);
    private final AtomicInteger reindexProcessed = new AtomicInteger(0);
    private final AtomicInteger reindexErrors = new AtomicInteger(0);
    private final AtomicInteger reindexTotal = new AtomicInteger(0);
    private volatile String reindexMessage = "";
    private final ExecutorService reindexExecutor =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "reindex-worker");
                        t.setDaemon(true);
                        return t;
                    });

    private final ConductorProperties properties;
    private final ExecutionService executionService;
    private final QueueDAO queueDAO;
    private final ExecutionDAO executionDAO;
    private final IndexDAO indexDAO;
    private final WorkflowRepairService workflowRepairService;
    private final EventQueueManager eventQueueManager;
    private final BuildProperties buildProperties;

    public AdminServiceImpl(
            ConductorProperties properties,
            ExecutionService executionService,
            QueueDAO queueDAO,
            ExecutionDAO executionDAO,
            IndexDAO indexDAO,
            Optional<WorkflowRepairService> workflowRepairService,
            Optional<EventQueueManager> eventQueueManager,
            Optional<BuildProperties> buildProperties) {
        this.properties = properties;
        this.executionService = executionService;
        this.queueDAO = queueDAO;
        this.executionDAO = executionDAO;
        this.indexDAO = indexDAO;
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

    @Override
    public Map<String, Object> startReindex() {
        if (!reindexState.compareAndSet(ReindexState.IDLE, ReindexState.RUNNING)
                && !reindexState.compareAndSet(ReindexState.COMPLETED, ReindexState.RUNNING)
                && !reindexState.compareAndSet(ReindexState.FAILED, ReindexState.RUNNING)) {
            Map<String, Object> result = new HashMap<>();
            result.put("state", "ALREADY_RUNNING");
            result.put("message", "A reindex job is already in progress");
            return result;
        }

        // Reset counters
        reindexProcessed.set(0);
        reindexErrors.set(0);
        reindexTotal.set(0);
        reindexMessage = "Starting...";

        reindexExecutor.submit(this::doReindex);

        Map<String, Object> result = new HashMap<>();
        result.put("state", "STARTED");
        result.put("message", "Reindex job started. Use GET /api/admin/reindex/status to track progress.");
        return result;
    }

    @Override
    public Map<String, Object> getReindexStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("state", reindexState.get().name());
        result.put("processed", reindexProcessed.get());
        result.put("errors", reindexErrors.get());
        result.put("total", reindexTotal.get());
        result.put("message", reindexMessage);
        return result;
    }

    private void doReindex() {
        LOGGER.info("Reindex job started");
        int batchSize = 100;
        int offset = 0;

        try {
            // First pass: count total
            int total = executionDAO.getAllWorkflowIds(0, Integer.MAX_VALUE).size();
            reindexTotal.set(total);
            reindexMessage = "Indexing 0 / " + total;
            LOGGER.info("Reindex: {} workflows to process", total);

            while (true) {
                List<String> workflowIds = executionDAO.getAllWorkflowIds(offset, batchSize);
                if (workflowIds.isEmpty()) {
                    break;
                }

                for (String workflowId : workflowIds) {
                    try {
                        WorkflowModel wfModel = executionDAO.getWorkflow(workflowId, true);
                        if (wfModel == null) {
                            LOGGER.warn("Workflow {} not found, skipping", workflowId);
                            reindexErrors.incrementAndGet();
                            continue;
                        }
                        indexDAO.indexWorkflow(new WorkflowSummary(wfModel.toWorkflow()));
                        for (TaskModel task : wfModel.getTasks()) {
                            indexDAO.indexTask(new TaskSummary(task.toTask()));
                        }
                        int done = reindexProcessed.incrementAndGet();
                        reindexMessage = "Indexing " + done + " / " + reindexTotal.get();
                    } catch (Exception e) {
                        reindexErrors.incrementAndGet();
                        LOGGER.error("Failed to reindex workflow {}", workflowId, e);
                    }
                }

                offset += batchSize;
                LOGGER.info(
                        "Reindex progress: {}/{}", reindexProcessed.get(), reindexTotal.get());
            }

            reindexMessage =
                    "Completed. processed="
                            + reindexProcessed.get()
                            + ", errors="
                            + reindexErrors.get();
            reindexState.set(ReindexState.COMPLETED);
            LOGGER.info("Reindex job completed. {}", reindexMessage);
        } catch (Exception e) {
            reindexMessage = "Failed: " + e.getMessage();
            reindexState.set(ReindexState.FAILED);
            LOGGER.error("Reindex job failed", e);
        }
    }
}
