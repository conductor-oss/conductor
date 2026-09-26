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

/** Structured decision inference shared by workflows, routers, and agent tools. */
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
        return decisionSupport.execute(task, () -> {}, request -> {}, this::addSelectedCase);
    }

    /** A single choice remains directly consumable by SWITCH; other question sets stay generic. */
    private void addSelectedCase(Map<String, Object> output, DecisionResult result) {
        if (result.answers().size() != 1) return;
        DecisionResult.Answer answer = result.answers().values().iterator().next();
        if (answer.type() == DecisionQuestion.Type.CHOICE) {
            output.put("selectedCase", answer.choice());
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }
}
