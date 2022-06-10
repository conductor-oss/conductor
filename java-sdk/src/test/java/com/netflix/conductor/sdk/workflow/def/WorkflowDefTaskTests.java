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
package com.netflix.conductor.sdk.workflow.def;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.sdk.workflow.def.tasks.*;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowDefTaskTests {

    static {
        WorkflowExecutor.initTaskImplementations();
    }

    @Test
    public void testWorkflowDefTaskWithStartDelay() {
        SimpleTask simpleTask = new SimpleTask("task_name", "task_ref_name");
        int startDelay = 5;

        simpleTask.setStartDelay(startDelay);

        WorkflowTask workflowTask = simpleTask.getWorkflowDefTasks().get(0);

        assertEquals(simpleTask.getStartDelay(), workflowTask.getStartDelay());
        assertEquals(startDelay, simpleTask.getStartDelay());
        assertEquals(startDelay, workflowTask.getStartDelay());
    }

    @Test
    public void testWorkflowDefTaskWithOptionalEnabled() {
        SimpleTask simpleTask = new SimpleTask("task_name", "task_ref_name");

        simpleTask.setOptional(true);

        WorkflowTask workflowTask = simpleTask.getWorkflowDefTasks().get(0);

        assertEquals(simpleTask.getStartDelay(), workflowTask.getStartDelay());
        assertEquals(true, simpleTask.isOptional());
        assertEquals(true, workflowTask.isOptional());
    }
}
