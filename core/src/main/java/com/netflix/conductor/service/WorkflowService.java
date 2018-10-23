/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.service;

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
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.service.utils.ServiceUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Trace
public class WorkflowService {

    private final WorkflowExecutor workflowExecutor;

    private final ExecutionService executionService;

    private final MetadataService metadataService;

    private int maxSearchSize;

    @Inject
    public WorkflowService(WorkflowExecutor workflowExecutor, ExecutionService executionService,
                           MetadataService metadataService, Configuration config) {
        this.workflowExecutor = workflowExecutor;
        this.executionService = executionService;
        this.metadataService = metadataService;
        this.maxSearchSize = config.getIntProperty("workflow.max.search.size", 5_000);
    }

    /**
     * Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain.
     *
     * @param startWorkflowRequest StartWorkflow request for the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) {
        ServiceUtils.checkNotNull(startWorkflowRequest, "StartWorkflowRequest cannot be null");
        ServiceUtils.checkNotNullOrEmpty(startWorkflowRequest.getName(), "Workflow name cannot be null or empty");

        WorkflowDef workflowDef = startWorkflowRequest.getWorkflowDef();

        if (workflowDef == null) {
            workflowDef = metadataService.getWorkflowDef(startWorkflowRequest.getName(), startWorkflowRequest.getVersion());
            if (workflowDef == null) {
                throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                        String.format("No such workflow found by name: %s, version: %d", startWorkflowRequest.getName(),
                                startWorkflowRequest.getVersion()));
            }

            return workflowExecutor.startWorkflow(
                    startWorkflowRequest.getName(),
                    startWorkflowRequest.getVersion(),
                    startWorkflowRequest.getCorrelationId(),
                    startWorkflowRequest.getInput(),
                    startWorkflowRequest.getExternalInputPayloadStoragePath(),
                    null,
                    startWorkflowRequest.getTaskToDomain()
            );
        } else {
            return workflowExecutor.startWorkflow(
                    startWorkflowRequest.getWorkflowDef(),
                    startWorkflowRequest.getInput(),
                    startWorkflowRequest.getExternalInputPayloadStoragePath(),
                    startWorkflowRequest.getCorrelationId(),
                    null,
                    startWorkflowRequest.getTaskToDomain()
            );
        }
    }

    /**
     * Start a new workflow.  Returns the ID of the workflow instance that can be later used for tracking.
     *
     * @param name          Name of the workflow you want to start.
     * @param version       Version of the workflow you want to start.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param input         Input to the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(String name, Integer version,
                                String correlationId, Map<String, Object> input) {
        ServiceUtils.checkNotNullOrEmpty(name, "Name cannot be null or empty");
        WorkflowDef workflowDef = metadataService.getWorkflowDef(name, version);
        if (workflowDef == null) {
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("No such workflow found by name: %s, version: %d", name, version));
        }
        return workflowExecutor.startWorkflow(workflowDef.getName(), workflowDef.getVersion(),
                correlationId, input, null);
    }

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name Name of the workflow.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param includeClosed IncludeClosed workflow which are not running.
     * @param includeTasks  Includes tasks associated with workflows.
     * @return a list of {@link Workflow}
     */
    public List<Workflow> getWorkflows(String name, String correlationId,
                                       boolean includeClosed, boolean includeTasks) {
        return executionService.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
    }

    /**
     * Lists workflows for the given correlation id.
     * @param name Name of the workflow.
     * @param includeClosed CorrelationID of the workflow you want to start.
     * @param includeTasks  IncludeClosed workflow which are not running.
     * @param correlationIds Includes tasks associated with workflows.
     * @return a {@link Map} of {@link String} as key and a list of {@link Workflow} as value
     */
    public Map<String, List<Workflow>> getWorkflows(String name, boolean includeClosed,
                                                    boolean includeTasks, List<String> correlationIds) {
        ServiceUtils.checkNotNullOrEmpty(name,"Name of the workflow cannot be null or empty.");
        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        for (String correlationId : correlationIds) {
            List<Workflow> workflows = executionService.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
            workflowMap.put(correlationId, workflows);
        }
        return workflowMap;
    }

    /**
     * Gets the workflow by workflow Id.
     * @param workflowId Id of the workflow.
     * @param includeTasks Includes tasks associated with workflow.
     * @return an instance of {@link Workflow}
     */
    public Workflow getExecutionStatus(String workflowId, boolean includeTasks) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        Workflow workflow = executionService.getExecutionStatus(workflowId, includeTasks);
        if (workflow == null) {
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("Workflow with Id: %s not found.", workflowId));
        }
        return workflow;
    }

    /**
     * Removes the workflow from the system.
     * @param workflowId WorkflowID of the workflow you want to remove from system.
     * @param archiveWorkflow Archives the workflow.
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        executionService.removeWorkflow(workflowId, archiveWorkflow);
    }

    /**
     * Retrieves all the running workflows.
     * @param workflowName Name of the workflow.
     * @param version Version of the workflow.
     * @param startTime Starttime of the workflow.
     * @param endTime EndTime of the workflow
     * @return a list of workflow Ids.
     */
    public List<String> getRunningWorkflows(String workflowName, Integer version,
                                            Long startTime, Long endTime) {
        ServiceUtils.checkNotNullOrEmpty(workflowName,"Workflow name cannot be null or empty.");
        if (startTime != null && endTime != null) {
            return workflowExecutor.getWorkflows(workflowName, version, startTime, endTime);
        } else {
            return workflowExecutor.getRunningWorkflowIds(workflowName);
        }
    }

    /**
     * Starts the decision task for a workflow.
     * @param workflowId WorkflowId of the workflow.
     */
    public void decideWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.decide(workflowId);
    }

    /**
     * Pauses the workflow given a worklfowId.
     * @param workflowId WorkflowId of the workflow.
     */
    public void pauseWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.pauseWorkflow(workflowId);
    }

    /**
     * Resumes the workflow.
     * @param workflowId WorkflowId of the workflow.
     */
    public void resumeWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.resumeWorkflow(workflowId);
    }

    /**
     * Skips a given task from a current running workflow.
     * @param workflowId WorkflowId of the workflow.
     * @param taskReferenceName The task reference name.
     * @param skipTaskRequest {@link SkipTaskRequest} for task you want to skip.
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName,
                                     SkipTaskRequest skipTaskRequest) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        ServiceUtils.checkNotNullOrEmpty(taskReferenceName,"TaskReferenceName cannot be null or empty.");
        workflowExecutor.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
    }

    /**
     * Reruns the workflow from a specific task.
     * @param workflowId WorkflowId of the workflow you want to rerun.
     * @param request (@link RerunWorkflowRequest) for the workflow.
     * @return WorkflowId of the rerun workflow.
     */
    public String rerunWorkflow(String workflowId, RerunWorkflowRequest request) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        ServiceUtils.checkNotNull(request, "RerunWorkflowRequest cannot be null.");
        request.setReRunFromWorkflowId(workflowId);
        return workflowExecutor.rerun(request);
    }

    /**
     * Restarts a completed workflow.
     * @param workflowId WorkflowId of the workflow.
     */
    public void restartWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.rewind(workflowId);
    }

    /**
     * Retries the last failed task.
     * @param workflowId WorkflowId of the workflow.
     */
    public void retryWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.retry(workflowId);
    }

    /**
     * Resets callback times of all in_progress tasks to 0.
     * @param workflowId WorkflowId of the workflow.
     */
    public void resetWorkflow(String workflowId) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.resetCallbacksForInProgressTasks(workflowId);
    }

    /**
     * Terminate workflow execution.
     * @param workflowId WorkflowId of the workflow.
     * @param reason Reason for terminating the workflow.
     */
    public void terminateWorkflow(String workflowId, String reason) {
        ServiceUtils.checkNotNullOrEmpty(workflowId,"WorkflowId cannot be null or empty.");
        workflowExecutor.terminateWorkflow(workflowId, reason);
    }

    /**
     * Search for workflows based on payload and given parameters. Use sort options as sort ASCor DESC
     * e.g. sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     * @param start Start index of pagination
     * @param size  Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflows(int start, int size, String sort, String freeText, String query) {
        ServiceUtils.checkArgument(size < maxSearchSize, String.format("Cannot return more than %d workflows." +
                " Please use pagination.", maxSearchSize));
        return executionService.search(query, freeText, start, size, ServiceUtils.convertStringToList(sort));
    }

    /**
     * Search for workflows based on task parameters. Use sort options as sort ASC or DESC e.g.
     * sort=name or sort=workflowId:DESC. If order is not specified, defaults to ASC.
     * @param start Start index of pagination
     * @param size  Number of entries
     * @param sort Sorting type ASC|DESC
     * @param freeText Text you want to search
     * @param query Query you want to search
     * @return instance of {@link SearchResult}
     */
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(int start, int size, String sort, String freeText, String query) {
        return executionService.searchWorkflowByTasks(query, freeText, start, size, ServiceUtils.convertStringToList(sort));
    }

    /**
     * Get the external storage location where the workflow input payload is stored/to be stored
     *
     * @param path the path for which the external storage location is to be populated
     * @return {@link ExternalStorageLocation} containing the uri and the path to the payload is stored in external storage
     */
    public ExternalStorageLocation getExternalStorageLocation(String path) {
        return executionService.getExternalStorageLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT, path);
    }
}
