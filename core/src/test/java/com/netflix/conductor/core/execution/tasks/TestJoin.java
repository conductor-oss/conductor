/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.execution.tasks;

import java.util.ArrayList;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class TestJoin {
    private final WorkflowExecutor executor = mock(WorkflowExecutor.class);

    @Test
    public void test_should_not_mark_join_as_completed_with_errors_when_not_done() {
        WorkflowModel workflow = new WorkflowModel();
        var task1 = new TaskModel();
        task1.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
        task1.setReferenceTaskName("task1");
        var task1WorkflowTask = new WorkflowTask();
        task1WorkflowTask.setOptional(true);
        task1.setWorkflowTask(task1WorkflowTask);

        var joinTask = new TaskModel();
        joinTask.setReferenceTaskName("join");
        joinTask.getInputData()
                .put(
                        "joinOn",
                        new ArrayList<String>() {
                            {
                                add("task1");
                                add("task2");
                            }
                        });

        workflow.getTasks().add(task1);
        workflow.getTasks().add(joinTask);

        var join = new Join();
        var result = join.execute(workflow, joinTask, executor);
        assertFalse(result);
    }
}
