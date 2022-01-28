/*
 * Copyright 2020 Netflix, Inc.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.service.AdminService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminResourceTest {

    @Mock private AdminService mockAdminService;

    @Mock private AdminResource adminResource;

    @Before
    public void before() {
        this.mockAdminService = mock(AdminService.class);
        this.adminResource = new AdminResource(mockAdminService);
    }

    @Test
    public void testGetAllConfig() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("config1", "test");
        when(mockAdminService.getAllConfig()).thenReturn(configs);
        assertEquals(configs, adminResource.getAllConfig());
    }

    @Test
    public void testView() {
        Task task = new Task();
        task.setReferenceTaskName("test");
        List<Task> listOfTask = new ArrayList<>();
        listOfTask.add(task);
        when(mockAdminService.getListOfPendingTask(anyString(), anyInt(), anyInt()))
                .thenReturn(listOfTask);
        assertEquals(listOfTask, adminResource.view("testTask", 0, 100));
    }

    @Test
    public void testRequeueSweep() {
        String workflowId = "w123";
        when(mockAdminService.requeueSweep(anyString())).thenReturn(workflowId);
        assertEquals(workflowId, adminResource.requeueSweep(workflowId));
    }

    @Test
    public void testGetEventQueues() {
        adminResource.getEventQueues(false);
        verify(mockAdminService, times(1)).getEventQueues(anyBoolean());
    }
}
