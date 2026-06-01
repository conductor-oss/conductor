/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.core.utils;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest.TaskRateLimitOverride;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class TaskMapperUtilsTest {

    @Mock private WorkflowModel workflowModel;
    @Mock private WorkflowTask workflowTask;
    @Mock private TaskDef taskDef;

    private static final String TASK_REF_NAME = "test_task_ref";
    private static final String TASK_NAME = "test_task";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(workflowTask.getTaskReferenceName()).thenReturn(TASK_REF_NAME);
        when(workflowTask.getName()).thenReturn(TASK_NAME);
        when(taskDef.getName()).thenReturn(TASK_NAME);
    }

    @Test
    public void testApplyRateLimitsWithNoOverrides() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(new HashMap<>());

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(100, taskModel.getRateLimitPerFrequency());
        assertEquals(60, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithFullOverrides() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(200);
        override.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_REF_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(200, taskModel.getRateLimitPerFrequency());
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithPartialOverrides() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_REF_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(100, taskModel.getRateLimitPerFrequency());
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithTaskNameOverride() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(300);
        override.setRateLimitFrequencyInSeconds(15);
        overrides.put(TASK_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(300, taskModel.getRateLimitPerFrequency());
        assertEquals(15, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithNullTaskDef() {
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(200);
        override.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_REF_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, null, taskModel);

        assertEquals(200, taskModel.getRateLimitPerFrequency());
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithNullOverridesMap() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(null);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(100, taskModel.getRateLimitPerFrequency());
        assertEquals(60, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithZeroValues() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(0);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(0);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(new HashMap<>());

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(0, taskModel.getRateLimitPerFrequency());
        assertEquals(0, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsZeroOverrideIsExplicit() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Zero is a valid explicit override (means "no rate limiting on this run")
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(0);
        override.setRateLimitFrequencyInSeconds(0);
        overrides.put(TASK_REF_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(0, taskModel.getRateLimitPerFrequency());
        assertEquals(0, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testReferenceNameOverrideHasPrecedenceOverTaskName() {
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride taskNameOverride = new TaskRateLimitOverride();
        taskNameOverride.setRateLimitPerFrequency(200);
        taskNameOverride.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_NAME, taskNameOverride);

        TaskRateLimitOverride refNameOverride = new TaskRateLimitOverride();
        refNameOverride.setRateLimitPerFrequency(300);
        refNameOverride.setRateLimitFrequencyInSeconds(15);
        overrides.put(TASK_REF_NAME, refNameOverride);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        TaskModel taskModel = new TaskModel();
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        assertEquals(300, taskModel.getRateLimitPerFrequency());
        assertEquals(15, taskModel.getRateLimitFrequencyInSeconds());
    }
}
