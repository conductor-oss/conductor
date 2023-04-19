/*
 * Copyright 2023 Netflix, Inc.
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowTestRequest;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;

@Component
public class WorkflowTestService {

    private static final int MAX_LOOPS = 20_000;

    private static final Set<String> operators = new HashSet<>();

    static {
        operators.add(TaskType.TASK_TYPE_JOIN);
        operators.add(TaskType.TASK_TYPE_DO_WHILE);
        operators.add(TaskType.TASK_TYPE_SET_VARIABLE);
        operators.add(TaskType.TASK_TYPE_FORK);
        operators.add(TaskType.TASK_TYPE_INLINE);
        operators.add(TaskType.TASK_TYPE_TERMINATE);
        operators.add(TaskType.TASK_TYPE_DECISION);
        operators.add(TaskType.TASK_TYPE_DYNAMIC);
        operators.add(TaskType.TASK_TYPE_FORK_JOIN);
        operators.add(TaskType.TASK_TYPE_FORK_JOIN_DYNAMIC);
        operators.add(TaskType.TASK_TYPE_SWITCH);
        operators.add(TaskType.TASK_TYPE_SUB_WORKFLOW);
    }

    private final WorkflowService workflowService;

    private final ExecutionDAO executionDAO;

    private final ExecutionService workflowExecutionService;

    public WorkflowTestService(
            WorkflowService workflowService,
            ExecutionDAO executionDAO,
            ExecutionService workflowExecutionService) {
        this.workflowService = workflowService;
        this.executionDAO = executionDAO;
        this.workflowExecutionService = workflowExecutionService;
    }

    public Workflow testWorkflow(WorkflowTestRequest request) {
        request.setName(request.getName());
        request.setVersion(request.getVersion());
        String domain = UUID.randomUUID().toString();
        // Ensure the workflows started for the testing are not picked by any workers
        request.getTaskToDomain().put("*", domain);
        String workflowId = workflowService.startWorkflow(request);
        return testWorkflow(request, workflowId);
    }

    private Workflow testWorkflow(WorkflowTestRequest request, String workflowId) {

        Map<String, List<WorkflowTestRequest.TaskMock>> mockData = request.getTaskRefToMockOutput();
        Workflow workflow;
        int loopCount = 0;
        do {
            loopCount++;
            workflow = workflowService.getExecutionStatus(workflowId, true);

            if (loopCount > MAX_LOOPS) {
                // Short circuit to avoid large loops
                return workflow;
            }

            List<String> runningTasksMissingInput =
                    workflow.getTasks().stream()
                            .filter(task -> !operators.contains(task.getTaskType()))
                            .filter(t -> !t.getStatus().isTerminal())
                            .filter(t2 -> !mockData.containsKey(t2.getReferenceTaskName()))
                            .map(task -> task.getReferenceTaskName())
                            .collect(Collectors.toList());

            if (!runningTasksMissingInput.isEmpty()) {
                break;
            }
            Stream<Task> runningTasks =
                    workflow.getTasks().stream().filter(t -> !t.getStatus().isTerminal());
            runningTasks.forEach(
                    running -> {
                        if (running.getTaskType().equals(TaskType.SUB_WORKFLOW.name())) {
                            String subWorkflowId = running.getSubWorkflowId();
                            WorkflowTestRequest subWorkflowTestRequest =
                                    request.getSubWorkflowTestRequest()
                                            .get(running.getReferenceTaskName());
                            if (subWorkflowId != null && subWorkflowTestRequest != null) {
                                testWorkflow(subWorkflowTestRequest, subWorkflowId);
                            }
                        }
                        String refName = running.getReferenceTaskName();
                        List<WorkflowTestRequest.TaskMock> taskMock = mockData.get(refName);
                        if (taskMock == null
                                || taskMock.isEmpty()
                                || operators.contains(running.getTaskType())) {
                            mockData.remove(refName);
                            workflowService.decideWorkflow(workflowId);
                        } else {
                            WorkflowTestRequest.TaskMock task = taskMock.remove(0);
                            if (task.getExecutionTime() > 0 || task.getQueueWaitTime() > 0) {
                                TaskModel existing = executionDAO.getTask(running.getTaskId());
                                existing.setScheduledTime(
                                        System.currentTimeMillis()
                                                - (task.getExecutionTime()
                                                        + task.getQueueWaitTime()));
                                existing.setStartTime(
                                        System.currentTimeMillis() - task.getExecutionTime());
                                existing.setStatus(
                                        TaskModel.Status.valueOf(task.getStatus().name()));
                                existing.getOutputData().putAll(task.getOutput());

                                executionDAO.updateTask(existing);
                                workflowService.decideWorkflow(workflowId);
                            } else {
                                TaskResult taskResult = new TaskResult(running);
                                taskResult.setStatus(task.getStatus());
                                taskResult.getOutputData().putAll(task.getOutput());
                                workflowExecutionService.updateTask(taskResult);
                            }
                        }
                    });
        } while (!workflow.getStatus().isTerminal() && !mockData.isEmpty());

        return workflow;
    }
}
