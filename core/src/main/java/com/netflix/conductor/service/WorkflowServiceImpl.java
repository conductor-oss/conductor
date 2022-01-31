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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.annotations.Audit;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.Utils;

@Audit
@Trace
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowServiceImpl.class);
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionService executionService;
    private final MetadataService metadataService;

    public WorkflowServiceImpl(
            WorkflowExecutor workflowExecutor,
            ExecutionService executionService,
            MetadataService metadataService) {
        this.workflowExecutor = workflowExecutor;
        this.executionService = executionService;
        this.metadataService = metadataService;
    }

    /**
     * Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain.
     *
     * @param startWorkflowRequest StartWorkflow request for the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        return startWorkflow(
                startWorkflowRequest.getName(),
                startWorkflowRequest.getVersion(),
                startWorkflowRequest.getCorrelationId(),
                startWorkflowRequest.getPriority(),
                startWorkflowRequest.getInput(),
                startWorkflowRequest.getExternalInputPayloadStoragePath(),
                startWorkflowRequest.getTaskToDomain(),
                startWorkflowRequest.getWorkflowDef());
    }

    /**
     * Start a new workflow.
     *
     * @param name Name of the workflow you want to start.
     * @param version Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param input Input to the workflow you want to start.
     * @param externalInputPayloadStoragePath the relative path in external storage where input
     *     payload is located
     * @param taskToDomain the task to domain mapping
     * @param workflowDef - workflow definition
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Map<String, Object> input,
            String externalInputPayloadStoragePath,
            Map<String, String> taskToDomain,
            WorkflowDef workflowDef) {
        return startWorkflow(
                name,
                version,
                correlationId,
                0,
                input,
                externalInputPayloadStoragePath,
                taskToDomain,
                workflowDef);
    }

    /**
     * Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain.
     *
     * @param name Name of the workflow you want to start.
     * @param version Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param priority Priority of the workflow you want to start.
     * @param input Input to the workflow you want to start.
     * @param externalInputPayloadStoragePath the relative path in external storage where input *
     *     payload is located
     * @param taskToDomain the task to domain mapping
     * @param workflowDef - workflow definition
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Integer priority,
            Map<String, Object> input,
            String externalInputPayloadStoragePath,
            Map<String, String> taskToDomain,
            WorkflowDef workflowDef) {

        if (workflowDef == null) {
            workflowDef = metadataService.getWorkflowDef(name, version);
            if (workflowDef == null) {
                throw new ApplicationException(
                        ApplicationException.Code.NOT_FOUND,
                        String.format(
                                "No such workflow found by name: %s, version: %d", name, version));
            }

            return workflowExecutor.startWorkflow(
                    name,
                    version,
                    correlationId,
                    priority,
                    input,
                    externalInputPayloadStoragePath,
                    null,
                    taskToDomain);
        } else {
            return workflowExecutor.startWorkflow(
                    workflowDef,
                    input,
                    externalInputPayloadStoragePath,
                    correlationId,
                    priority,
                    null,
                    taskToDomain);
        }
    }

    /**
     * Start a new workflow. Returns the ID of the workflow instance that can be later used for
     * tracking.
     *
     * @param name Name of the workflow you want to start.
     * @param version Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param input Input to the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(
            String name, Integer version, String correlationId, Map<String, Object> input) {
        metadataService.getWorkflowDef(name, version);
        return startWorkflow(name, version, correlationId, 0, input);
    }

    /**
     * Start a new workflow. Returns the ID of the workflow instance that can be later used for
     * tracking.
     *
     * @param name Name of the workflow you want to start.
     * @param version Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param priority Priority of the workflow you want to start.
     * @param input Input to the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Integer priority,
            Map<String, Object> input) {
        WorkflowDef workflowDef = metadataService.getWorkflowDef(name, version);
        if (workflowDef == null) {
            throw new ApplicationException(
                    ApplicationException.Code.NOT_FOUND,
                    String.format(
                            "No such workflow found by name: %s, version: %d", name, version));
        }
        return workflowExecutor.startWorkflow(
                workflowDef.getName(),
                workflowDef.getVersion(),
                correlationId,
                priority,
                input,
                null);
    }

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name Name of the workflow.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param includeClosed IncludeClosed workflow which are not running.
     * @param includeTasks Includes tasks associated with workflows.
     * @return a list of {@link Workflow}
     */
    public List<Workflow> getWorkflows(
            String name, String correlationId, boolean includeClosed, boolean includeTasks) {
        return executionService.getWorkflowInstances(
                name, correlationId, includeClosed, includeTasks);
    }

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name Name of the workflow.
     * @param includeClosed CorrelationID of the workflow you want to start.
     * @param includeTasks IncludeClosed workflow which are not running.
     * @param correlationIds Includes tasks associated with workflows.
     * @return a {@link Map} of {@link String} as key and a list of {@link Workflow} as value
     */
    public Map<String, List<Workflow>> getWorkflows(
            String name, boolean includeClosed, boolean includeTasks, List<String> correlationIds) {
        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        for (String correlationId : correlationIds) {
            List<Workflow> workflows =
                    executionService.getWorkflowInstances(
                            name, correlationId, includeClosed, includeTasks);
            workflowMap.put(correlationId, workflows);
        }
        return workflowMap;
    }

    /**
     * Gets the workflow by workflow id.
     *
     * @param workflowId id of the workflow.
     * @param includeTasks Includes tasks associated with workflow.
     * @return an instance of {@link Workflow}
     */
    public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
        Workflow workflow = executionService.getExecutionStatus(workflowId, includeTasks);
        if (workflow == null) {
            throw new ApplicationException(
                    ApplicationException.Code.NOT_FOUND,
                    String.format("Workflow with Id: %s not found.", workflowId));
        }
        return workflow;
    }

    /**
     * Removes the workflow from the system.
     *
     * @param workflowId WorkflowID of the workflow you want to remove from system.
     * @param archiveWorkflow Archives the workflow.
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        executionService.removeWorkflow(workflowId, archiveWorkflow);
    }

    /**
     * Retrieves all the running workflows.
     *
     * @param workflowName Name of the workflow.
     * @param version Version of the workflow.
     * @param startTime start time of the workflow.
     * @param endTime EndTime of the workflow
     * @return a list of workflow Ids.
     */
    public List<String> getRunningWorkflows(
            String workflowName, Integer version, Long startTime, Long endTime) {
        if (Optional.ofNullable(startTime).orElse(0L) != 0
                && Optional.ofNullable(endTime).orElse(0L) != 0) {
            return workflowExecutor.getWorkflows(workflowName, version, startTime, endTime);
        } else {
            version =
                    Optional.ofNullable(version)
                            .orElseGet(
                                    () -> {
                                        WorkflowDef workflowDef =
                                                metadataService.getWorkflowDef(workflowName, null);
                                        return workflowDef.getVersion();
                                    });
            return workflowExecutor.getRunningWorkflowIds(workflowName, version);
        }
    }

    /**
     * Starts the decision task for a workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    public void decideWorkflow(String workflowId) {
        workflowExecutor.decide(workflowId);
    }

    /**
     * Pauses the workflow given a workflowId.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    public void pauseWorkflow(String workflowId) {
        workflowExecutor.pauseWorkflow(workflowId);
    }

    /**
     * Resumes the workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    public void resumeWorkflow(String workflowId) {
        workflowExecutor.resumeWorkflow(workflowId);
    }

    /**
     * Skips a given task from a current running workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param taskReferenceName The task reference name.
     * @param skipTaskRequest {@link SkipTaskRequest} for task you want to skip.
     */
    public void skipTaskFromWorkflow(
            String workflowId, String taskReferenceName, SkipTaskRequest skipTaskRequest) {
        workflowExecutor.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
    }

    /**
     * Reruns the workflow from a specific task.
     *
     * @param workflowId WorkflowId of the workflow you want to rerun.
     * @param request (@link RerunWorkflowRequest) for the workflow.
     * @return WorkflowId of the rerun workflow.
     */
    public String rerunWorkflow(String workflowId, RerunWorkflowRequest request) {
        request.setReRunFromWorkflowId(workflowId);
        return workflowExecutor.rerun(request);
    }

    /**
     * Restarts a completed workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param useLatestDefinitions if true, use the latest workflow and task definitions upon
     *     restart
     */
    public void restartWorkflow(String workflowId, boolean useLatestDefinitions) {
        workflowExecutor.restart(workflowId, useLatestDefinitions);
    }

    /**
     * Retries the last failed task.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    public void retryWorkflow(String workflowId, boolean resumeSubworkflowTasks) {
        workflowExecutor.retry(workflowId, resumeSubworkflowTasks);
    }

    /**
     * Resets callback times of all non-terminal SIMPLE tasks to 0.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    public void resetWorkflow(String workflowId) {
        workflowExecutor.resetCallbacksForWorkflow(workflowId);
    }

    /**
     * Terminate workflow execution.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param reason Reason for terminating the workflow.
     */
    public void terminateWorkflow(String workflowId, String reason) {
        workflowExecutor.terminateWorkflow(workflowId, reason);
    }

    /**
     * Search for workflows based on payload and given parameters. Use sort options as sort ASCor
     * DESC e.g. sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflows(
            int start, int size, String sort, String freeText, String query) {
        return executionService.search(
                query, freeText, start, size, Utils.convertStringToList(sort));
    }

    /**
     * Search for workflows based on payload and given parameters. Use sort options as sort ASCor
     * DESC e.g. sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<Workflow> searchWorkflowsV2(
            int start, int size, String sort, String freeText, String query) {
        return executionService.searchV2(
                query, freeText, start, size, Utils.convertStringToList(sort));
    }

    /**
     * Search for workflows based on payload and given parameters. Use sort options as sort ASCor
     * DESC e.g. sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort list of sorting options, separated by "|" delimiter
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflows(
            int start, int size, List<String> sort, String freeText, String query) {
        return executionService.search(query, freeText, start, size, sort);
    }

    /**
     * Search for workflows based on payload and given parameters. Use sort options as sort ASCor
     * DESC e.g. sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort list of sorting options, separated by "|" delimiter
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<Workflow> searchWorkflowsV2(
            int start, int size, List<String> sort, String freeText, String query) {
        return executionService.searchV2(query, freeText, start, size, sort);
    }

    /**
     * Search for workflows based on task parameters. Use sort options as sort ASC or DESC e.g.
     * sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(
            int start, int size, String sort, String freeText, String query) {
        return executionService.searchWorkflowByTasks(
                query, freeText, start, size, Utils.convertStringToList(sort));
    }

    /**
     * Search for workflows based on task parameters. Use sort options as sort ASC or DESC e.g.
     * sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<Workflow> searchWorkflowsByTasksV2(
            int start, int size, String sort, String freeText, String query) {
        return executionService.searchWorkflowByTasksV2(
                query, freeText, start, size, Utils.convertStringToList(sort));
    }

    /**
     * Search for workflows based on task parameters. Use sort options as sort ASC or DESC e.g.
     * sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort list of sorting options, separated by "|" delimiter
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(
            int start, int size, List<String> sort, String freeText, String query) {
        return executionService.searchWorkflowByTasks(query, freeText, start, size, sort);
    }

    /**
     * Search for workflows based on task parameters. Use sort options as sort ASC or DESC e.g.
     * sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     *
     * @param start Start index of pagination
     * @param size Number of entries
     * @param sort list of sorting options, separated by "|" delimiter
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<Workflow> searchWorkflowsByTasksV2(
            int start, int size, List<String> sort, String freeText, String query) {
        return executionService.searchWorkflowByTasksV2(query, freeText, start, size, sort);
    }

    /**
     * Get the external storage location where the workflow input payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @param operation the operation to be performed (read or write)
     * @param type the type of payload (input or output)
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is
     *     stored in external storage
     */
    public ExternalStorageLocation getExternalStorageLocation(
            String path, String operation, String type) {
        try {
            ExternalPayloadStorage.Operation payloadOperation =
                    ExternalPayloadStorage.Operation.valueOf(StringUtils.upperCase(operation));
            ExternalPayloadStorage.PayloadType payloadType =
                    ExternalPayloadStorage.PayloadType.valueOf(StringUtils.upperCase(type));
            return executionService.getExternalStorageLocation(payloadOperation, payloadType, path);
        } catch (Exception e) {
            // FIXME: for backwards compatibility
            LOGGER.error(
                    "Invalid input - Operation: {}, PayloadType: {}, defaulting to WRITE/WORKFLOW_INPUT",
                    operation,
                    type);
            return executionService.getExternalStorageLocation(
                    ExternalPayloadStorage.Operation.WRITE,
                    ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT,
                    path);
        }
    }
}
