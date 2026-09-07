/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.conductoross.conductor.common.metadata.agent.CreateTrackingWorkflowRequest;
import org.conductoross.conductor.common.metadata.agent.CreateTrackingWorkflowResponse;
import org.conductoross.conductor.common.metadata.agent.InjectTaskRequest;
import org.conductoross.conductor.common.metadata.agent.InjectTaskResponse;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentDagService {

    private final ExecutionDAO executionDAO;

    public InjectTaskResponse injectTask(String executionId, InjectTaskRequest req) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, true);
        if (workflow == null) {
            throw new NotFoundException("Execution not found: " + executionId);
        }

        boolean isSubWorkflow =
                "SUB_WORKFLOW".equals(req.getType()) && req.getSubWorkflowParam() != null;

        // Build inputData — for SUB_WORKFLOW, include the standard Conductor fields
        Map<String, Object> inputData;
        if (isSubWorkflow) {
            inputData = new LinkedHashMap<>();
            inputData.put("subWorkflowName", req.getSubWorkflowParam().getName());
            inputData.put("subWorkflowVersion", req.getSubWorkflowParam().getVersion());
            // workflowInput is what was passed to the sub-workflow
            inputData.put(
                    "workflowInput", req.getInputData() != null ? req.getInputData() : Map.of());
        } else {
            inputData = req.getInputData() != null ? req.getInputData() : Collections.emptyMap();
        }

        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(req.getTaskDefName());
        task.setReferenceTaskName(req.getReferenceTaskName());
        task.setTaskType(req.getType());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setWorkflowInstanceId(executionId);
        task.setWorkflowType(workflow.getWorkflowName());
        task.setInputData(inputData);
        task.setSeq(workflow.getTasks().size() + 1);
        long now = System.currentTimeMillis();
        task.setScheduledTime(now);
        task.setStartTime(now);

        // Build the WorkflowTask first — needed both for the WorkflowDef and the TaskModel
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(req.getTaskDefName());
        workflowTask.setTaskReferenceName(req.getReferenceTaskName());
        workflowTask.setOptional(true); // display-only — must not fail the parent workflow

        if (isSubWorkflow) {
            workflowTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
            SubWorkflowParams swp = new SubWorkflowParams();
            swp.setName(req.getSubWorkflowParam().getName());
            swp.setVersion(req.getSubWorkflowParam().getVersion());
            // Attach the sub-workflow's definition so the DAG can render it
            String subExecId = req.getSubWorkflowParam().getExecutionId();
            if (subExecId != null) {
                WorkflowModel subWf = executionDAO.getWorkflow(subExecId, false);
                if (subWf != null && subWf.getWorkflowDefinition() != null) {
                    swp.setWorkflowDefinition(subWf.getWorkflowDefinition());
                }
            }
            workflowTask.setSubWorkflowParam(swp);
            // Standard SUB_WORKFLOW inputParameters
            Map<String, Object> inputParams = new LinkedHashMap<>();
            if (req.getInputData() != null) {
                inputParams.putAll(req.getInputData());
            }
            workflowTask.setInputParameters(inputParams);
        } else {
            workflowTask.setType(req.getType());
            workflowTask.setInputParameters(
                    req.getInputData() != null ? req.getInputData() : Collections.emptyMap());
        }

        if (isSubWorkflow) {
            String subExecId = req.getSubWorkflowParam().getExecutionId();
            task.setSubWorkflowId(subExecId);
            // Pre-set outputData with subWorkflowId so the UI can link immediately
            Map<String, Object> outputData = new LinkedHashMap<>();
            outputData.put("subWorkflowId", subExecId);
            task.setOutputData(outputData);
        }

        // Attach the WorkflowTask to the TaskModel so Conductor API returns it correctly
        task.setWorkflowTask(workflowTask);

        executionDAO.createTasks(List.of(task));

        // Also add the WorkflowTask to the WorkflowDef so the DAG view renders it
        WorkflowDef def = workflow.getWorkflowDefinition();
        List<WorkflowTask> defTasks = new ArrayList<>(def.getTasks());
        defTasks.add(workflowTask);
        def.setTasks(defTasks);
        executionDAO.updateWorkflow(workflow);

        return new InjectTaskResponse(task.getTaskId());
    }

    public CreateTrackingWorkflowResponse createTrackingWorkflow(
            CreateTrackingWorkflowRequest req) {
        WorkflowDef def = new WorkflowDef();
        def.setName(req.getWorkflowName());
        def.setVersion(1);
        def.setTasks(new ArrayList<>());
        def.setInputParameters(List.of("prompt"));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(UUID.randomUUID().toString());
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setInput(req.getInput() != null ? req.getInput() : Map.of());
        workflow.setCreateTime(System.currentTimeMillis());

        // Parent linkage for back-navigation
        if (req.getParentWorkflowId() != null) {
            workflow.setParentWorkflowId(req.getParentWorkflowId());
            if (req.getParentWorkflowTaskId() != null) {
                workflow.setParentWorkflowTaskId(req.getParentWorkflowTaskId());
            }
        }

        executionDAO.createWorkflow(workflow);
        return new CreateTrackingWorkflowResponse(workflow.getWorkflowId());
    }

    public void completeTrackingWorkflow(String executionId, Map<String, Object> output) {
        WorkflowModel workflow = executionDAO.getWorkflow(executionId, true);
        if (workflow == null) {
            throw new NotFoundException("Execution not found: " + executionId);
        }

        // Auto-complete any IN_PROGRESS tasks before marking the workflow done.
        // Fire-and-forget task completions from the SDK may still be in flight.
        long now = System.currentTimeMillis();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getStatus() == TaskModel.Status.IN_PROGRESS) {
                task.setStatus(TaskModel.Status.COMPLETED);
                task.setEndTime(now);
                executionDAO.updateTask(task);
            }
        }

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        if (output != null) {
            workflow.setOutput(output);
        }
        workflow.setEndTime(now);
        executionDAO.updateWorkflow(workflow);
    }
}
