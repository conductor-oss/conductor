/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.sdk.workflow.def.tasks;

import java.util.*;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/** Switch Task */
public class Switch extends Task<Switch> {

    public static final String VALUE_PARAM_NAME = "value-param";

    public static final String JAVASCRIPT_NAME = "javascript";

    private String caseExpression;

    private boolean useJavascript;

    private List<Task<?>> defaultTasks = new ArrayList<>();

    private Map<String, List<Task<?>>> branches = new HashMap<>();

    /**
     * Switch case (similar to if...then...else or switch in java language)
     *
     * @param taskReferenceName
     * @param caseExpression An expression that outputs a string value to be used as case branches.
     *     Case expression can be a support value parameter e.g. ${workflow.input.key} or
     *     ${task.output.key} or a Javascript statement.
     * @param useJavascript set to true if the caseExpression is a javascript statement
     */
    public Switch(String taskReferenceName, String caseExpression, boolean useJavascript) {
        super(taskReferenceName, TaskType.SWITCH);
        this.caseExpression = caseExpression;
        this.useJavascript = useJavascript;
    }

    /**
     * Switch case (similar to if...then...else or switch in java language)
     *
     * @param taskReferenceName
     * @param caseExpression
     */
    public Switch(String taskReferenceName, String caseExpression) {
        super(taskReferenceName, TaskType.SWITCH);
        this.caseExpression = caseExpression;
        this.useJavascript = false;
    }

    Switch(WorkflowTask workflowTask) {
        super(workflowTask);
        Map<String, List<WorkflowTask>> decisions = workflowTask.getDecisionCases();

        decisions.entrySet().stream()
                .forEach(
                        branch -> {
                            String branchName = branch.getKey();
                            List<WorkflowTask> branchWorkflowTasks = branch.getValue();
                            List<Task<?>> branchTasks = new ArrayList<>();
                            for (WorkflowTask branchWorkflowTask : branchWorkflowTasks) {
                                branchTasks.add(TaskRegistry.getTask(branchWorkflowTask));
                            }
                            this.branches.put(branchName, branchTasks);
                        });

        List<WorkflowTask> defaultCases = workflowTask.getDefaultCase();
        for (WorkflowTask defaultCase : defaultCases) {
            this.defaultTasks.add(TaskRegistry.getTask(defaultCase));
        }
    }

    public Switch defaultCase(Task<?>... tasks) {
        defaultTasks = Arrays.asList(tasks);
        return this;
    }

    public Switch defaultCase(List<Task<?>> defaultTasks) {
        this.defaultTasks = defaultTasks;
        return this;
    }

    public Switch decisionCases(Map<String, List<Task<?>>> branches) {
        this.branches = branches;
        return this;
    }

    public Switch defaultCase(String... workerTasks) {
        for (String workerTask : workerTasks) {
            this.defaultTasks.add(new SimpleTask(workerTask, workerTask));
        }
        return this;
    }

    public Switch switchCase(String caseValue, Task... tasks) {
        branches.put(caseValue, Arrays.asList(tasks));
        return this;
    }

    public Switch switchCase(String caseValue, String... workerTasks) {
        List<Task<?>> tasks = new ArrayList<>(workerTasks.length);
        int i = 0;
        for (String workerTask : workerTasks) {
            tasks.add(new SimpleTask(workerTask, workerTask));
        }
        branches.put(caseValue, tasks);
        return this;
    }

    public List<Task<?>> getDefaultTasks() {
        return defaultTasks;
    }

    public Map<String, List<Task<?>>> getBranches() {
        return branches;
    }

    @Override
    public void updateWorkflowTask(WorkflowTask workflowTask) {

        if (useJavascript) {
            workflowTask.setEvaluatorType(JAVASCRIPT_NAME);
            workflowTask.setExpression(caseExpression);

        } else {
            workflowTask.setEvaluatorType(VALUE_PARAM_NAME);
            workflowTask.getInputParameters().put("switchCaseValue", caseExpression);
            workflowTask.setExpression("switchCaseValue");
        }

        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        branches.entrySet()
                .forEach(
                        entry -> {
                            String decisionCase = entry.getKey();
                            List<Task<?>> decisionTasks = entry.getValue();
                            List<WorkflowTask> decionTaskDefs =
                                    new ArrayList<>(decisionTasks.size());
                            for (Task<?> decisionTask : decisionTasks) {
                                decionTaskDefs.addAll(decisionTask.getWorkflowDefTasks());
                            }
                            decisionCases.put(decisionCase, decionTaskDefs);
                        });

        workflowTask.setDecisionCases(decisionCases);
        List<WorkflowTask> defaultCaseTaskDefs = new ArrayList<>(defaultTasks.size());
        for (Task<?> defaultTask : defaultTasks) {
            defaultCaseTaskDefs.addAll(defaultTask.getWorkflowDefTasks());
        }
        workflowTask.setDefaultCase(defaultCaseTaskDefs);
    }
}
