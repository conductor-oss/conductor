/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.rest.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDefSummary;
import com.netflix.conductor.service.MetadataService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetadataResourceTest {

    private MetadataResource metadataResource;

    private MetadataService mockMetadataService;

    @Before
    public void before() {
        this.mockMetadataService = mock(MetadataService.class);
        this.metadataResource = new MetadataResource(this.mockMetadataService);
    }

    @Test
    public void testCreateWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        metadataResource.create(workflowDef);
        verify(mockMetadataService, times(1)).registerWorkflowDef(any(WorkflowDef.class));
    }

    @Test
    public void testValidateWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        metadataResource.validate(workflowDef);
        verify(mockMetadataService, times(1)).validateWorkflowDef(any(WorkflowDef.class));
    }

    @Test
    public void testUpdateWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);
        metadataResource.update(listOfWorkflowDef);
        verify(mockMetadataService, times(1)).updateWorkflowDef(anyList());
    }

    @Test
    public void testGetWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);
        workflowDef.setDescription("test");

        when(mockMetadataService.getWorkflowDef(anyString(), any())).thenReturn(workflowDef);
        assertEquals(workflowDef, metadataResource.get("test", 1));
    }

    @Test
    public void testGetAllWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);
        workflowDef.setDescription("test");

        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);

        when(mockMetadataService.getWorkflowDefs()).thenReturn(listOfWorkflowDef);
        assertEquals(listOfWorkflowDef, metadataResource.getAll());
    }

    @Test
    public void testGetAllWorkflowDefLatestVersions() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);
        workflowDef.setDescription("test");

        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);

        when(mockMetadataService.getWorkflowDefsLatestVersions()).thenReturn(listOfWorkflowDef);
        assertEquals(listOfWorkflowDef, metadataResource.getAllWorkflowsWithLatestVersions());
    }

    @Test
    public void testUnregisterWorkflowDef() throws Exception {
        metadataResource.unregisterWorkflowDef("test", 1);
        verify(mockMetadataService, times(1)).unregisterWorkflowDef(anyString(), any());
    }

    @Test
    public void testRegisterListOfTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setDescription("desc");
        List<TaskDef> listOfTaskDefs = new ArrayList<>();
        listOfTaskDefs.add(taskDef);

        metadataResource.registerTaskDef(listOfTaskDefs);
        verify(mockMetadataService, times(1)).registerTaskDef(listOfTaskDefs);
    }

    @Test
    public void testRegisterTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setDescription("desc");
        metadataResource.registerTaskDef(taskDef);
        verify(mockMetadataService, times(1)).updateTaskDef(taskDef);
    }

    @Test
    public void testGetAllTaskDefs() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setDescription("desc");
        List<TaskDef> listOfTaskDefs = new ArrayList<>();
        listOfTaskDefs.add(taskDef);

        when(mockMetadataService.getTaskDefs()).thenReturn(listOfTaskDefs);
        assertEquals(listOfTaskDefs, metadataResource.getTaskDefs());
    }

    @Test
    public void testGetTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setDescription("desc");

        when(mockMetadataService.getTaskDef(anyString())).thenReturn(taskDef);
        assertEquals(taskDef, metadataResource.getTaskDef("test"));
    }

    @Test
    public void testUnregisterTaskDef() {
        metadataResource.unregisterTaskDef("test");
        verify(mockMetadataService, times(1)).unregisterTaskDef(anyString());
    }

    @Test
    public void testGetWorkflowNames() {
        List<String> names = Arrays.asList("workflow_a", "workflow_b");
        when(mockMetadataService.getWorkflowNames()).thenReturn(names);
        assertEquals(names, metadataResource.getWorkflowNames());
        verify(mockMetadataService, times(1)).getWorkflowNames();
    }

    @Test
    public void testGetWorkflowVersions() {
        WorkflowDefSummary v1 = new WorkflowDefSummary();
        v1.setName("my_workflow");
        v1.setVersion(1);
        v1.setCreateTime(1000L);

        WorkflowDefSummary v2 = new WorkflowDefSummary();
        v2.setName("my_workflow");
        v2.setVersion(2);
        v2.setCreateTime(2000L);

        List<WorkflowDefSummary> versions = Arrays.asList(v1, v2);
        when(mockMetadataService.getWorkflowVersions("my_workflow")).thenReturn(versions);

        List<WorkflowDefSummary> result = metadataResource.getWorkflowVersions("my_workflow");
        assertEquals(versions, result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getVersion());
        assertEquals(2, result.get(1).getVersion());
        verify(mockMetadataService, times(1)).getWorkflowVersions("my_workflow");
    }
}
