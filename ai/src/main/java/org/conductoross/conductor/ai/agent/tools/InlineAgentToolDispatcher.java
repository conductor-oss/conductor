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
package org.conductoross.conductor.ai.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs an agent's tools as tasks in the agent's own workflow.
 *
 * <p>The tools an agent asks for are work the workflow is doing, so they belong in it: one task per
 * call, in the same execution, in the same diagram, holding the run open until they finish. The
 * alternative implementation puts them in a child workflow, which isolates them but means the
 * agent's own execution shows none of the work it did.
 *
 * <p>Holds nothing between calls. A dispatch is identified by the workflow, the agent's task and
 * the turn, all of which are in the id itself, so a poll landing on another replica resolves the
 * same batch by reading the workflow back.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "conductor.integrations.ai.agent.tool-execution",
        havingValue = "inline",
        matchIfMissing = true)
@Conditional(AIIntegrationEnabledCondition.class)
public class InlineAgentToolDispatcher implements AgentToolDispatcher {

    private static final String ID_PREFIX = "inline:";

    private final ObjectProvider<ExecutionDAOFacade> executionDAO;

    // Resolved on use rather than injected, for the constructor cycle
    // SubWorkflowAgentToolDispatcher
    // documents: WorkflowServiceImpl -> WorkflowExecutorOps -> SystemTaskRegistry ->
    // WorkerTaskAnnotationScanner.
    private final ObjectProvider<WorkflowExecutor> workflowExecutor;

    public InlineAgentToolDispatcher(
            ObjectProvider<ExecutionDAOFacade> executionDAO,
            ObjectProvider<WorkflowExecutor> workflowExecutor) {
        this.executionDAO = executionDAO;
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public AgentToolDispatch dispatch(Request request) {
        WorkflowModel workflow = load(request.parentWorkflowId());
        int turn = nextTurn(workflow, request.taskRefName());

        List<WorkflowTask> tasks = new ArrayList<>();
        for (Map<String, Object> toolCall : request.toolCalls()) {
            tasks.add(toolTask(request, toolCall, turn));
        }

        List<TaskModel> scheduled =
                workflowExecutor.getObject().scheduleDynamicTasks(workflow, tasks);
        if (scheduled.size() != tasks.size()) {
            // Names are unique per turn, so a short result means the workflow would not take them -
            // it finished, or something else claimed the names. Saying so beats waiting for tasks
            // that will never run.
            return AgentToolDispatch.failed(
                    dispatchId(request, turn),
                    "Scheduled "
                            + scheduled.size()
                            + " of "
                            + tasks.size()
                            + " tool tasks in workflow "
                            + request.parentWorkflowId()
                            + ", which is "
                            + workflow.getStatus());
        }
        log.debug(
                "Scheduled {} tool task(s) for {} turn {} in workflow {}",
                scheduled.size(),
                request.taskRefName(),
                turn,
                request.parentWorkflowId());
        return AgentToolDispatch.running(dispatchId(request, turn));
    }

    @Override
    public AgentToolDispatch status(String dispatchId) {
        Batch batch = Batch.parse(dispatchId);
        List<TaskModel> tasks = toolTasksOf(load(batch.workflowId()), batch);
        if (tasks.isEmpty()) {
            return AgentToolDispatch.failed(dispatchId, "No tool tasks found for " + dispatchId);
        }
        Map<String, Object> results = new LinkedHashMap<>();
        for (TaskModel task : tasks) {
            if (!task.getStatus().isTerminal()) {
                return AgentToolDispatch.running(dispatchId);
            }
            if (!task.getStatus().isSuccessful()) {
                return AgentToolDispatch.failed(
                        dispatchId,
                        "Tool task "
                                + task.getReferenceTaskName()
                                + " ended "
                                + task.getStatus()
                                + (task.getReasonForIncompletion() == null
                                        ? ""
                                        : ": " + task.getReasonForIncompletion()));
            }
            results.put(toolCallIdOf(task), task.getOutputData());
        }
        return AgentToolDispatch.completed(dispatchId, results);
    }

    @Override
    public void cancel(String dispatchId) {
        try {
            Batch batch = Batch.parse(dispatchId);
            WorkflowModel workflow = load(batch.workflowId());
            for (TaskModel task : toolTasksOf(workflow, batch)) {
                if (!task.getStatus().isTerminal()) {
                    task.setStatus(TaskModel.Status.CANCELED);
                    executionDAO.getObject().updateTask(task);
                }
            }
        } catch (Exception e) {
            // Best effort, like the sub-workflow dispatcher: a batch that cannot be stopped must
            // not stop the agent task from failing for the reason it was already failing.
            log.warn("Could not cancel tool tasks for {}: {}", dispatchId, e.getMessage());
        }
    }

    /**
     * The turn this batch belongs to, counted from the workflow rather than remembered.
     *
     * <p>An agent can ask for tools several times in one task, and every round needs names of its
     * own - the engine drops a repeated reference name without saying so.
     */
    private static int nextTurn(WorkflowModel workflow, String taskRefName) {
        int highest = 0;
        for (TaskModel task : workflow.getTasks()) {
            Integer turn = AgentToolNaming.turnOf(task.getReferenceTaskName(), taskRefName);
            if (turn != null) {
                highest = Math.max(highest, turn);
            }
        }
        return highest + 1;
    }

    private WorkflowModel load(String workflowId) {
        return executionDAO.getObject().getWorkflowModel(workflowId, true);
    }

    private static List<TaskModel> toolTasksOf(WorkflowModel workflow, Batch batch) {
        String prefix = AgentToolNaming.turnPrefix(batch.taskRefName(), batch.turn());
        List<TaskModel> tasks = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getReferenceTaskName() != null
                    && task.getReferenceTaskName().startsWith(prefix)) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private static String toolCallIdOf(TaskModel task) {
        Object id =
                task.getInputData() == null
                        ? null
                        : task.getInputData().get(AgentToolNaming.TOOL_CALL_ID);
        return String.valueOf(id);
    }

    private static WorkflowTask toolTask(Request request, Map<String, Object> toolCall, int turn) {
        String toolName = String.valueOf(toolCall.get("tool_name"));
        String toolCallId = String.valueOf(toolCall.get("tool_call_id"));

        WorkflowTask task = new WorkflowTask();
        task.setType("SIMPLE");
        task.setName(AgentToolNaming.taskNameFor(request.toolTaskNames(), toolName));
        task.setTaskReferenceName(
                AgentToolNaming.referenceName(request.taskRefName(), turn, toolCallId));
        task.setInputParameters(
                AgentToolNaming.toolInput(toolCall, toolCallId, toolName, request.executionId()));
        return task;
    }

    private static String dispatchId(Request request, int turn) {
        return ID_PREFIX + request.parentWorkflowId() + "|" + request.taskRefName() + "|" + turn;
    }

    /** The three things needed to find a batch again, carried in its id. */
    private record Batch(String workflowId, String taskRefName, int turn) {

        static Batch parse(String dispatchId) {
            String[] parts = dispatchId.substring(ID_PREFIX.length()).split("\\|");
            if (!dispatchId.startsWith(ID_PREFIX) || parts.length != 3) {
                throw new IllegalArgumentException("Not an inline tool dispatch id: " + dispatchId);
            }
            return new Batch(parts[0], parts[1], Integer.parseInt(parts[2]));
        }
    }
}
