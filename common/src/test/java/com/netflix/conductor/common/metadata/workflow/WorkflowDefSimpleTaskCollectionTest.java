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
package com.netflix.conductor.common.metadata.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefSimpleTaskCollectionTest {

    @Test
    void collectsSimpleTasksAcrossControlFlowAndStaticInlineWorkflows() {
        WorkflowDef root = workflow("root");
        WorkflowDef inline = workflow("inline");
        WorkflowDef deeplyNested = workflow("deeply_nested");

        WorkflowTask decision = task("route", TaskType.SWITCH.name());
        LinkedHashMap<String, List<WorkflowTask>> cases = new LinkedHashMap<>();
        cases.put("selected", List.of(simple("switch_worker")));
        decision.setDecisionCases(cases);
        decision.setDefaultCase(List.of(simple("default_worker")));

        WorkflowTask fork = task("fork", TaskType.FORK_JOIN.name());
        WorkflowTask inlineSubWorkflow = inlineSubWorkflow("inline_subworkflow", inline);
        WorkflowTask runtimeSubWorkflow = task("runtime_subworkflow", TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams runtimeParams = new SubWorkflowParams();
        runtimeParams.setWorkflowDefinition("${compile.output.workflowDef}");
        runtimeSubWorkflow.setSubWorkflowParam(runtimeParams);
        fork.setForkTasks(
                List.of(List.of(simple("fork_worker"), inlineSubWorkflow, runtimeSubWorkflow)));

        WorkflowTask loop = task("loop", TaskType.DO_WHILE.name());
        loop.setLoopOver(List.of(simple("loop_worker")));

        root.setTasks(List.of(simple("root_worker"), decision, fork, loop));

        // Reuse a worker name, recurse through a second inline definition, and point back to the
        // root. The result must remain deduplicated and the in-memory definition cycle must end.
        inline.setTasks(
                List.of(
                        simple("inline_worker"),
                        simple("root_worker"),
                        inlineSubWorkflow("deeply_nested", deeplyNested)));
        deeplyNested.setTasks(
                List.of(simple("deep_worker"), inlineSubWorkflow("root_cycle", root)));

        Set<String> names = root.collectSimpleTaskNames();

        assertThat(names)
                .containsExactly(
                        "root_worker",
                        "switch_worker",
                        "default_worker",
                        "fork_worker",
                        "inline_worker",
                        "deep_worker",
                        "loop_worker");
    }

    @Test
    void visitsDistinctInlineDefinitionsThatAreEqualByNameAndVersion() {
        WorkflowDef root = workflow("root");
        WorkflowDef first = workflow("shared");
        WorkflowDef second = workflow("shared");
        first.setTasks(List.of(simple("first_worker")));
        second.setTasks(List.of(simple("second_worker")));
        root.setTasks(
                List.of(
                        inlineSubWorkflow("first_inline", first),
                        inlineSubWorkflow("second_inline", second)));

        assertThat(first).isEqualTo(second);
        assertThat(root.collectSimpleTaskNames()).containsExactly("first_worker", "second_worker");
    }

    private static WorkflowDef workflow(String name) {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(name);
        return workflowDef;
    }

    private static WorkflowTask simple(String name) {
        return task(name, TaskType.SIMPLE.name());
    }

    private static WorkflowTask task(String name, String type) {
        WorkflowTask task = new WorkflowTask();
        task.setName(name);
        task.setTaskReferenceName(name + "_ref");
        task.setType(type);
        return task;
    }

    private static WorkflowTask inlineSubWorkflow(String name, WorkflowDef workflowDef) {
        WorkflowTask task = task(name, TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams params = new SubWorkflowParams();
        params.setWorkflowDefinition(workflowDef);
        task.setSubWorkflowParam(params);
        return task;
    }
}
