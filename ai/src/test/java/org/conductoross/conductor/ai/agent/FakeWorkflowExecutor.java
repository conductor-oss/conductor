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
package org.conductoross.conductor.ai.agent;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.StartWorkflowInput;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** In-process, scriptable WorkflowExecutor used by the conductor-agent task tests. */
class FakeWorkflowExecutor implements WorkflowExecutor {

    AgentStartResponse startResponse =
            AgentStartResponse.builder().executionId("exec-1").agentName("planner").build();
    WorkflowModel workflow;
    WorkflowModel workflowAfterUpdate;
    boolean throwOnGetWorkflow;
    boolean throwOnTerminate;

    AgentStartRequest lastStartRequest;
    TaskResult lastTaskResult;
    String lastTerminatedExecutionId;
    String lastTerminationReason;

    @Override
    public AgentStartResponse startAgentExecution(AgentStartRequest request) {
        lastStartRequest = request;
        return startResponse;
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId, boolean includeTasks) {
        if (throwOnGetWorkflow) {
            throw new RuntimeException("executor unreachable");
        }
        return workflow;
    }

    @Override
    public TaskModel updateTask(TaskResult taskResult) {
        lastTaskResult = taskResult;
        if (workflowAfterUpdate != null) {
            workflow = workflowAfterUpdate;
        }
        return null;
    }

    @Override
    public void terminateWorkflow(String workflowId, String reason) {
        if (throwOnTerminate) {
            throw new RuntimeException("executor unreachable");
        }
        lastTerminatedExecutionId = workflowId;
        lastTerminationReason = reason;
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not used by conductor-agent tests");
    }

    @Override
    public void resetCallbacksForWorkflow(String workflowId) {
        throw unsupported();
    }

    @Override
    public String rerun(RerunWorkflowRequest request) {
        throw unsupported();
    }

    @Override
    public void restart(String workflowId, boolean useLatestDefinitions) {
        throw unsupported();
    }

    @Override
    public void retry(String workflowId, boolean resumeSubworkflowTasks) {
        throw unsupported();
    }

    @Override
    public TaskModel getTask(String taskId) {
        throw unsupported();
    }

    @Override
    public List<Workflow> getRunningWorkflows(String workflowName, int version) {
        throw unsupported();
    }

    @Override
    public List<String> getWorkflows(String name, Integer version, Long startTime, Long endTime) {
        throw unsupported();
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowName, int version) {
        throw unsupported();
    }

    @Override
    public WorkflowModel decide(String workflowId) {
        throw unsupported();
    }

    @Override
    public WorkflowModel decideWithLock(WorkflowModel workflow) {
        throw unsupported();
    }

    @Override
    public WorkflowModel terminateWorkflow(
            WorkflowModel workflow,
            String reason,
            String failureWorkflow,
            Integer failureWorkflowVersion) {
        throw unsupported();
    }

    @Override
    public void pauseWorkflow(String workflowId) {
        throw unsupported();
    }

    @Override
    public void resumeWorkflow(String workflowId) {
        throw unsupported();
    }

    @Override
    public void skipTaskFromWorkflow(
            String workflowId, String taskReferenceName, SkipTaskRequest skipTaskRequest) {
        throw unsupported();
    }

    @Override
    public AgentStartResponse startAgentExecution(
            AgentStartRequest request,
            AgentConfig config,
            WorkflowDef def,
            Map<String, Object> executionConfig) {
        throw unsupported();
    }

    @Override
    public void scheduleNextIteration(TaskModel task, WorkflowModel workflow) {
        throw unsupported();
    }

    @Override
    public String startWorkflow(StartWorkflowInput input) {
        throw unsupported();
    }

    @Override
    public WorkflowModel startWorkflowIdempotent(StartWorkflowInput input) {
        throw unsupported();
    }
}
