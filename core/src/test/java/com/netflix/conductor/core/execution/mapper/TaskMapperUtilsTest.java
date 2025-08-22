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
package com.netflix.conductor.core.execution.mapper;

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

        // Set up basic workflow task properties
        when(workflowTask.getTaskReferenceName()).thenReturn(TASK_REF_NAME);
        when(workflowTask.getName()).thenReturn(TASK_NAME);
    }

    @Test
    public void testApplyRateLimitsWithNoOverrides() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Empty overrides map
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(new HashMap<>());

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify TaskDef defaults were used
        assertEquals(100, taskModel.getRateLimitPerFrequency());
        assertEquals(60, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithFullOverrides() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Create overrides by reference task name
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(200);
        override.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_REF_NAME, override);

        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify overrides were used instead of defaults
        assertEquals(200, taskModel.getRateLimitPerFrequency());
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithPartialOverrides() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Create partial override (only frequency set)
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitFrequencyInSeconds(30); // Only set frequency
        overrides.put(TASK_REF_NAME, override);

        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify mixed values (default for perFreq, override for freqSecs)
        assertEquals(100, taskModel.getRateLimitPerFrequency()); // Default
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds()); // Override
    }

    @Test
    public void testApplyRateLimitsWithTaskNameOverride() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Create override by task name (not reference name)
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(300);
        override.setRateLimitFrequencyInSeconds(15);
        overrides.put(TASK_NAME, override); // Use task name instead of reference name

        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify task name override was used
        assertEquals(300, taskModel.getRateLimitPerFrequency());
        assertEquals(15, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithNullTaskDef() {
        // Create override
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();
        override.setRateLimitPerFrequency(200);
        override.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_REF_NAME, override);

        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits with null TaskDef
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, null, taskModel);

        // Verify overrides were used with null TaskDef
        assertEquals(200, taskModel.getRateLimitPerFrequency());
        assertEquals(30, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithNullOverridesMap() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Return null overrides map
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(null);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify defaults were used
        assertEquals(100, taskModel.getRateLimitPerFrequency());
        assertEquals(60, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithZeroValues() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(0);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(0);

        // Empty overrides map
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(new HashMap<>());

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify zero values were passed through
        assertEquals(0, taskModel.getRateLimitPerFrequency());
        assertEquals(0, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testApplyRateLimitsWithOptimizedOverrideClass() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Create override using the optimized implementation
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();
        TaskRateLimitOverride override = new TaskRateLimitOverride();

        // Use the new implementation with validation
        override.setRateLimitPerFrequency(500); // This will validate non-negative
        override.setRateLimitFrequencyInSeconds(45); // This will validate non-negative

        overrides.put(TASK_REF_NAME, override);
        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify optimized implementation works correctly
        assertEquals(500, taskModel.getRateLimitPerFrequency());
        assertEquals(45, taskModel.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testReferenceNameOverrideHasPrecedenceOverTaskName() {
        // Set up TaskDef defaults
        when(taskDef.getRateLimitPerFrequency()).thenReturn(100);
        when(taskDef.getRateLimitFrequencyInSeconds()).thenReturn(60);

        // Create both reference name and task name overrides
        Map<String, TaskRateLimitOverride> overrides = new HashMap<>();

        // Task name override
        TaskRateLimitOverride taskNameOverride = new TaskRateLimitOverride();
        taskNameOverride.setRateLimitPerFrequency(200);
        taskNameOverride.setRateLimitFrequencyInSeconds(30);
        overrides.put(TASK_NAME, taskNameOverride);

        // Reference name override (should take precedence)
        TaskRateLimitOverride refNameOverride = new TaskRateLimitOverride();
        refNameOverride.setRateLimitPerFrequency(300);
        refNameOverride.setRateLimitFrequencyInSeconds(15);
        overrides.put(TASK_REF_NAME, refNameOverride);

        when(workflowModel.getTaskRateLimitOverrides()).thenReturn(overrides);

        // Create a real TaskModel
        TaskModel taskModel = new TaskModel();

        // Apply rate limits
        TaskMapperUtils.applyRateLimits(workflowModel, workflowTask, taskDef, taskModel);

        // Verify reference name override was used (not task name override)
        assertEquals(300, taskModel.getRateLimitPerFrequency());
        assertEquals(15, taskModel.getRateLimitFrequencyInSeconds());
    }
}
