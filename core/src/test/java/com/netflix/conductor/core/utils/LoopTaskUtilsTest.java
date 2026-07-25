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
package com.netflix.conductor.core.utils;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.model.TaskModel;

import static org.junit.Assert.assertEquals;

public class LoopTaskUtilsTest {

    @Test
    public void removesTheWholeIterationSuffixChain() {
        assertEquals("task", TaskUtils.removeIterationFromTaskRefName("task__1"));
        assertEquals("task", TaskUtils.removeIterationFromTaskRefName("task__2__14"));
    }

    @Test
    public void computesSuffixChainAgainstDefinitionName() {
        TaskModel topLevelLoop = task("loop", "loop", 3);
        topLevelLoop.setTaskType("DO_WHILE");
        assertEquals("", LoopTaskUtils.getIterationSuffixChain(topLevelLoop));
        assertEquals("__2", LoopTaskUtils.getIterationSuffixChain(task("inner__2", "inner", 2)));
        assertEquals(
                "__2__1", LoopTaskUtils.getIterationSuffixChain(task("body__2__1", "body", 1)));
        assertEquals("", LoopTaskUtils.getIterationSuffixChain(task("my__2", "my__2", 0)));
        assertEquals("__1", LoopTaskUtils.getIterationSuffixChain(task("my__2__1", "my__2", 1)));
    }

    @Test
    public void returnsInnermostIteration() {
        assertEquals(1, LoopTaskUtils.getIterationFromSuffixChain("__1"));
        assertEquals(14, LoopTaskUtils.getIterationFromSuffixChain("__2__14"));
        assertEquals(-1, LoopTaskUtils.getIterationFromSuffixChain(""));
    }

    private TaskModel task(String runtimeRefName, String definitionRefName, int iteration) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName(definitionRefName);
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(runtimeRefName);
        task.setWorkflowTask(workflowTask);
        task.setIteration(iteration);
        return task;
    }
}
