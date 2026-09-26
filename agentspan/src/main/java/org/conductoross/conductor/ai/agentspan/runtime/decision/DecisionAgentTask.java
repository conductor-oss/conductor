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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentStreamRegistry;
import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

/** Internal execution step for a compiled Decision agent definition. */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class DecisionAgentTask extends WorkflowSystemTask {
    private static final Logger log = LoggerFactory.getLogger(DecisionAgentTask.class);

    private final DecisionTaskSupport decisionSupport;
    private final AgentStreamRegistry streamRegistry;

    public DecisionAgentTask(
            DecisionTaskSupport decisionSupport, AgentStreamRegistry streamRegistry) {
        super("DECISION_AGENT");
        this.decisionSupport = decisionSupport;
        this.streamRegistry = streamRegistry;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        execute(workflow, task, executor);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        boolean executed =
                decisionSupport.execute(
                        task,
                        () -> validateCompiledDecision(workflow, task),
                        request -> {},
                        (output, result) -> {});
        if (executed && task.getStatus() == TaskModel.Status.COMPLETED) {
            publishDecision(task);
        }
        return executed;
    }

    /** Async system tasks bypass TaskStatusListener completion callbacks, so publish at source. */
    private void publishDecision(TaskModel task) {
        String workflowId = task.getWorkflowInstanceId();
        try {
            streamRegistry.send(
                    workflowId,
                    AgentSSEEvent.decision(
                            workflowId, task.getReferenceTaskName(), task.getOutputData()));
        } catch (RuntimeException e) {
            // Streaming is observational and must never change the durable task result.
            log.warn(
                    "Failed to emit Decision event for workflow {}: {}",
                    workflowId,
                    e.getMessage());
        }
    }

    private void validateCompiledDecision(WorkflowModel workflow, TaskModel task) {
        // Require the compiler-owned definition shape; this prevents accidental direct use of the
        // internal task type, but is not intended to be an authorization boundary.
        var definition = workflow.getWorkflowDefinition();
        var metadata = definition != null ? definition.getMetadata() : null;
        if (metadata == null
                || !"agent".equals(metadata.get("classifier"))
                || !(metadata.get("agentDef") instanceof Map<?, ?> agent)
                || !"decision".equals(agent.get("kind"))
                || definition.getTasks().size() != 1
                || !getTaskType().equals(definition.getTasks().get(0).getType())
                || !definition
                        .getTasks()
                        .get(0)
                        .getTaskReferenceName()
                        .equals(task.getReferenceTaskName())) {
            throw new NonRetryableException(
                    "Decision requires a compiled Decision agent definition");
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }
}
