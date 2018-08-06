package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowResourceInfo {

    private WorkflowExecutor workflowExecutor;

    private ExecutionService executionService;

    private MetadataService metadata;

    private int maxSearchSize;

    public WorkflowResourceInfo(WorkflowExecutor workflowExecutor, ExecutionService executionService,
                                MetadataService metadata, Configuration config) {
        this.workflowExecutor = workflowExecutor;
        this.executionService = executionService;
        this.metadata = metadata;
        this.maxSearchSize = config.getIntProperty("workflow.max.search.size", 5_000);
    }

    /**
     * Start a new workflow with StartWorkflowRequest, which allows task to be executed in a domain.
     *
     * @param startWorkflowRequest StartWorkflow request for the workflow you want to start.
     * @return the id of the workflow instance that can be use for tracking.
     */
    public String startWorkflow(StartWorkflowRequest startWorkflowRequest) throws Exception {
        WorkflowDef workflowDef = metadata.getWorkflowDef(startWorkflowRequest.getName(), startWorkflowRequest.getVersion());
        if (workflowDef == null) {
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("No such workflow found by name=%s, version=%d", startWorkflowRequest.getName(),
                            startWorkflowRequest.getVersion()));
        }
        return workflowExecutor.startWorkflow(workflowDef.getName(), workflowDef.getVersion(),
                startWorkflowRequest.getCorrelationId(), startWorkflowRequest.getInput(), null,
                startWorkflowRequest.getTaskToDomain());

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
                                String correlationId, Map<String, Object> input) throws Exception {

        WorkflowDef workflowDef = metadata.getWorkflowDef(name, version);
        if (workflowDef == null) {
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("No such workflow found by name=%s, version=%d", name, version));
        }
        return workflowExecutor.startWorkflow(workflowDef.getName(), workflowDef.getVersion(),
                correlationId, input, null);
    }

    /**
     * Lists workflows for the given correlation id.
     *
     * @param name          Name of the workflow.
     * @param correlationId CorrelationID of the workflow you want to start.
     * @param includeClosed
     * @param includeTasks
     * @return the list of workflows.
     */
    public List<Workflow> getWorklfows(String name, String correlationId,
                                       boolean includeClosed, boolean includeTasks) throws Exception {
        return executionService.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
    }

    /*
     * Lists workflows for the given correlation id.
     * @param name
     * @param includeClosed
     * @param includeTasks
     * @param correlationIds
     * @return
     */
    public Map<String, List<Workflow>> getWorkflows(String name, boolean includeClosed,
                                                    boolean includeTasks, List<String> correlationIds) throws Exception {
        Map<String, List<Workflow>> workflowMap = new HashMap<>();
        for (String correlationId : correlationIds) {
            List<Workflow> workflows = executionService.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
            workflowMap.put(correlationId, workflows);
        }
        return workflowMap;
    }

    /*
     * Gets the workflow by workflow Id.
     * @param name
     * @param includeClosed
     * @param includeTasks
     * @param correlationIds
     * @return
     */
    public Workflow getExecutionStatus(String workflowId, boolean includeTasks) throws Exception {
        Workflow workflow = executionService.getExecutionStatus(workflowId, includeTasks);

        if (workflow == null) {
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("Workflow with Id= %s not found.", workflowId));
        }
        return workflow;
    }

    /*
     * Removes the workflow from the system.
     * @param workflowId
     * @param archiveWorkflow
     */
    public void deleteWorkflow(String workflowId, boolean archiveWorkflow) throws Exception {
        executionService.removeWorkflow(workflowId, archiveWorkflow);
    }

    /*
     * "Retrieve all the running workflows".
     * @param workflowId
     * @param archiveWorkflow
     */
    public List<String> getRunningWorkflows(String workflowName, Integer version, Long startTime, Long endTime) throws Exception {
        if (startTime != null && endTime != null) {
            return workflowExecutor.getWorkflows(workflowName, version, startTime, endTime);
        } else {
            return workflowExecutor.getRunningWorkflowIds(workflowName);
        }
    }

    /*
     * Starts the decision task for a workflow.
     * @param workflowId
     * @param archiveWorkflow
     */
    public void decideWorkflow(String workflowId) throws Exception {
        workflowExecutor.decide(workflowId);
    }

    /*
     * Pauses the workflow
     * @param workflowId
     * @param archiveWorkflow
     */
    public void pauseWorkflow(String workflowId) throws Exception {
        workflowExecutor.pauseWorkflow(workflowId);
    }

    /*
     * Resumes the workflow.
     * @param workflowId
     */
    public void resumeWorkflow(String workflowId) throws Exception {
        workflowExecutor.resumeWorkflow(workflowId);
    }

    /*
     * Skips a given task from a current running workflow.
     * @param workflowId
     */
    public void skipTaskFromWorkflow(String workflowId, String taskReferenceName,
                                     SkipTaskRequest skipTaskRequest) throws Exception {
        workflowExecutor.skipTaskFromWorkflow(workflowId, taskReferenceName, skipTaskRequest);
    }


    /*
     * Reruns the workflow from a specific task
     * @param workflowId
     */
    public String rerunWorkflow(String workflowId, RerunWorkflowRequest request) throws Exception {
        request.setReRunFromWorkflowId(workflowId);
        return workflowExecutor.rerun(request);
    }

    /*
     * Restarts a completed workflow.
     * @param workflowId
     */
    public void restartWorkflow(String workflowId) throws Exception {
        workflowExecutor.rewind(workflowId);
    }

    /*
     * Retries the last failed task.
     * @param workflowId
     */
    public void retryWorkflow(String workflowId) throws Exception {
        workflowExecutor.retry(workflowId);
    }

    /*
     * Resets callback times of all in_progress tasks to 0.
     * @param workflowId
     */
    public void resetWorkflow(String workflowId) throws Exception {
        workflowExecutor.resetCallbacksForInProgressTasks(workflowId);
    }

    /*
     * Terminate workflow execution
     */
    public void terminateWorkflow(String workflowId, String reason) throws Exception {
        workflowExecutor.terminateWorkflow(workflowId, reason);
    }


    /*
     * Search for workflows based on payload and given parameters. Use sort options as sort=<field>:ASC|DESC
     * e.g. sort=name&sort=workflowId:DESC. If order is not specified, defaults to ASC
     */
    public SearchResult<WorkflowSummary> searchWorkflows(int start, int size, String sort, String freeText, String query) {
        if (size > maxSearchSize) {
            throw new ApplicationException(ApplicationException.Code.INVALID_INPUT,
                    String.format("Cannot return more than %d workflows. Please use pagination.", maxSearchSize));
        }
        return executionService.search(query, freeText, start, size, convertToSortedList(sort));
    }


    /*
     * Search for workflows based on task parameters. Use sort options as sort=<field>:ASC|DESC e.g.
     * sort=name&sort=workflowId:DESC. If order is not specified, defaults to ASC."
     */
    public SearchResult<WorkflowSummary> searchWorkflowsByTasks(int start, int size, String sort, String freeText, String query) {
        return executionService.searchWorkflowByTasks(query, freeText, start, size, convertToSortedList(sort));
    }

    private List<String> convertToSortedList(String sortStr) {
        List<String> list = new ArrayList<String>();
        if (sortStr != null && sortStr.length() != 0) {
            list = Arrays.asList(sortStr.split("\\|"));
        }
        return list;
    }
}
