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

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** Asynchronous Decision inference exposing a decision for a downstream SWITCH task. */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class AiDecisionTask extends WorkflowSystemTask {
    private final DecisionTaskSupport decisionSupport;

    public AiDecisionTask(DecisionTaskSupport decisionSupport) {
        super("AI_DECISION");
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
                () -> {},
                request -> {
                    DecisionQuestion question =
                            request.questions() != null && request.questions().size() == 1
                                    ? request.questions().values().iterator().next()
                                    : null;
                    if (question == null || question.type() != DecisionQuestion.Type.CHOICE) {
                        throw new IllegalArgumentException(
                                "AI_DECISION requires exactly one choice question");
                    }
                },
                (output, result) ->
                        // Keep the full response plus a value directly consumable by SWITCH.
                        output.put(
                                "selectedCase",
                                result.answers().values().iterator().next().choice()));
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }
}
