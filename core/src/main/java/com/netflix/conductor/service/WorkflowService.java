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

import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

@Validated
public interface WorkflowService {

    /**
     * Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain.
     *
     * @param startWorkflowRequest StartWorkflow request for the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    String startWorkflow(
            @NotNull(message = "StartWorkflowRequest cannot be null") @Valid
                    StartWorkflowRequest startWorkflowRequest);

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
    String startWorkflow(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name,
            Integer version,
            String correlationId,
            @Min(value = 0, message = "0 is the minimum priority value")
                    @Max(value = 99, message = "99 is the maximum priority value")
                    Integer priority,
            Map<String, Object> input);

    /**
     * Start a new workflow. Returns the ID of the workflow instance that can be later used for
     * tracking.
     *
     * @param name Name of the workflow you want to start.
     * @param version Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param priority Priority of the workflow you want to start.
     * @param input Input to the workflow you want to start.
     * @param externalInputPayloadStoragePath
     * @param taskToDomain
     * @param workflowDef - workflow definition
     * @return the id of the workflow instance that can be use for tracking.
     */
    String startWorkflow(
            String name,
            Integer version,
            String correlationId,
            Integer priority,
            Map<String, Object> input,
            String externalInputPayloadStoragePath,
            Map<String, String> taskToDomain,
            WorkflowDef workflowDef);

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name Name of the workflow.
     * @param correlationId CorrelationID of the workflow you want to list.
     * @param includeClosed IncludeClosed workflow which are not running.
     * @param includeTasks Includes tasks associated with workflows.
     * @return a list of {@link Workflow}
     */
    List<Workflow> getWorkflows(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name,
            String correlationId,
            boolean includeClosed,
            boolean includeTasks);

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name Name of the workflow.
     * @param includeClosed CorrelationID of the workflow you want to start.
     * @param includeTasks IncludeClosed workflow which are not running.
     * @param correlationIds Includes tasks associated with workflows.
     * @return a {@link Map} of {@link String} as key and a list of {@link Workflow} as value
     */
    Map<String, List<Workflow>> getWorkflows(
            @NotEmpty(message = "Workflow name cannot be null or empty") String name,
            boolean includeClosed,
            boolean includeTasks,
            List<String> correlationIds);

    /**
     * Gets the workflow by workflow Id.
     *
     * @param workflowId Id of the workflow.
     * @param includeTasks Includes tasks associated with workflow.
     * @return an instance of {@link Workflow}
     */
    Workflow getExecutionStatus(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            boolean includeTasks);

    /**
     * Removes the workflow from the system.
     *
     * @param workflowId WorkflowID of the workflow you want to remove from system.
     * @param archiveWorkflow Archives the workflow and associated tasks instead of removing them.
     */
    void deleteWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            boolean archiveWorkflow);

    /**
     * Retrieves all the running workflows.
     *
     * @param workflowName Name of the workflow.
     * @param version Version of the workflow.
     * @param startTime Starttime of the workflow.
     * @param endTime EndTime of the workflow
     * @return a list of workflow Ids.
     */
    List<String> getRunningWorkflows(
            @NotEmpty(message = "Workflow name cannot be null or empty.") String workflowName,
            Integer version,
            Long startTime,
            Long endTime);

    /**
     * Starts the decision task for a workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    void decideWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);

    /**
     * Pauses the workflow given a worklfowId.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    void pauseWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);

    /**
     * Resumes the workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    void resumeWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);

    /**
     * Skips a given task from a current running workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param taskReferenceName The task reference name.
     * @param skipTaskRequest {@link SkipTaskRequest} for task you want to skip.
     */
    void skipTaskFromWorkflow(
            @NotEmpty(message = "WorkflowId name cannot be null or empty.") String workflowId,
            @NotEmpty(message = "TaskReferenceName cannot be null or empty.")
                    String taskReferenceName,
            SkipTaskRequest skipTaskRequest);

    /**
     * Reruns the workflow from a specific task.
     *
     * @param workflowId WorkflowId of the workflow you want to rerun.
     * @param request (@link RerunWorkflowRequest) for the workflow.
     * @return WorkflowId of the rerun workflow.
     */
    String rerunWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            @NotNull(message = "RerunWorkflowRequest cannot be null.")
                    RerunWorkflowRequest request);

    /**
     * Restarts a completed workflow.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param useLatestDefinitions if true, use the latest workflow and task definitions upon
     *     restart
     */
    void restartWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            boolean useLatestDefinitions);

    /**
     * Retries the last failed task.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    void retryWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            boolean resumeSubworkflowTasks);

    /**
     * Resets callback times of all non-terminal SIMPLE tasks to 0.
     *
     * @param workflowId WorkflowId of the workflow.
     */
    void resetWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId);

    /**
     * Terminate workflow execution.
     *
     * @param workflowId WorkflowId of the workflow.
     * @param reason Reason for terminating the workflow.
     */
    void terminateWorkflow(
            @NotEmpty(message = "WorkflowId cannot be null or empty.") String workflowId,
            String reason);

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
    SearchResult<WorkflowSummary> searchWorkflows(
            int start,
            @Max(
                            value = 5_000,
                            message =
                                    "Cannot return more than {value} workflows. Please use pagination.")
                    int size,
            String sort,
            String freeText,
            String query);

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
    SearchResult<Workflow> searchWorkflowsV2(
            int start,
            @Max(
                            value = 5_000,
                            message =
                                    "Cannot return more than {value} workflows. Please use pagination.")
                    int size,
            String sort,
            String freeText,
            String query);

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
    SearchResult<WorkflowSummary> searchWorkflows(
            int start,
            @Max(
                            value = 5_000,
                            message =
                                    "Cannot return more than {value} workflows. Please use pagination.")
                    int size,
            List<String> sort,
            String freeText,
            String query);

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
    SearchResult<Workflow> searchWorkflowsV2(
            int start,
            @Max(
                            value = 5_000,
                            message =
                                    "Cannot return more than {value} workflows. Please use pagination.")
                    int size,
            List<String> sort,
            String freeText,
            String query);

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
    SearchResult<WorkflowSummary> searchWorkflowsByTasks(
            int start, int size, String sort, String freeText, String query);

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
    SearchResult<Workflow> searchWorkflowsByTasksV2(
            int start, int size, String sort, String freeText, String query);

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
    SearchResult<WorkflowSummary> searchWorkflowsByTasks(
            int start, int size, List<String> sort, String freeText, String query);

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
    SearchResult<Workflow> searchWorkflowsByTasksV2(
            int start, int size, List<String> sort, String freeText, String query);

    /**
     * Get the external storage location where the workflow input payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @param operation the operation to be performed (read or write)
     * @param payloadType the type of payload (input or output)
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is
     *     stored in external storage
     */
    ExternalStorageLocation getExternalStorageLocation(
            String path, String operation, String payloadType);
}
