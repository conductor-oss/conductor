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

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
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
    private final DecisionTaskSupport decisionSupport;

    public DecisionAgentTask(DecisionTaskSupport decisionSupport) {
        super("DECISION_AGENT");
        this.decisionSupport = decisionSupport;
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
        return decisionSupport.execute(
                task,
                () -> validateCompiledDecision(workflow, task),
                request -> {},
                (output, result) -> {});
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
