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
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.SearchResult;
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
        SearchResult<WorkflowDef> result =
                metadataResource.getAllWorkflowsWithLatestVersions(null, null);

        assertEquals(1, result.getTotalHits());
        assertEquals(listOfWorkflowDef, result.getResults());
        verify(mockMetadataService, times(1)).getWorkflowDefsLatestVersions();
    }

    @Test
    public void testGetAllWorkflowDefLatestVersionsWithPagination() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);
        workflowDef.setDescription("test");

        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);

        SearchResult<WorkflowDef> expectedResult = new SearchResult<>(100, listOfWorkflowDef);

        when(mockMetadataService.searchWorkflowDefsLatestVersions(0, 10))
                .thenReturn(expectedResult);
        SearchResult<WorkflowDef> result =
                metadataResource.getAllWorkflowsWithLatestVersions(0, 10);

        assertEquals(100, result.getTotalHits());
        assertEquals(listOfWorkflowDef, result.getResults());
        verify(mockMetadataService, times(1)).searchWorkflowDefsLatestVersions(0, 10);
    }

    @Test
    public void testGetAllWorkflowDefLatestVersionsWithOnlyStartParam() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);

        SearchResult<WorkflowDef> expectedResult = new SearchResult<>(50, listOfWorkflowDef);

        when(mockMetadataService.searchWorkflowDefsLatestVersions(20, 100))
                .thenReturn(expectedResult);
        SearchResult<WorkflowDef> result =
                metadataResource.getAllWorkflowsWithLatestVersions(20, null);

        assertEquals(50, result.getTotalHits());
        assertEquals(1, result.getResults().size());
        verify(mockMetadataService, times(1)).searchWorkflowDefsLatestVersions(20, 100);
    }

    @Test
    public void testGetAllWorkflowDefLatestVersionsWithOnlySizeParam() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        workflowDef.setVersion(1);

        List<WorkflowDef> listOfWorkflowDef = new ArrayList<>();
        listOfWorkflowDef.add(workflowDef);

        SearchResult<WorkflowDef> expectedResult = new SearchResult<>(50, listOfWorkflowDef);

        when(mockMetadataService.searchWorkflowDefsLatestVersions(0, 50))
                .thenReturn(expectedResult);
        SearchResult<WorkflowDef> result =
                metadataResource.getAllWorkflowsWithLatestVersions(null, 50);

        assertEquals(50, result.getTotalHits());
        assertEquals(1, result.getResults().size());
        verify(mockMetadataService, times(1)).searchWorkflowDefsLatestVersions(0, 50);
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
}
