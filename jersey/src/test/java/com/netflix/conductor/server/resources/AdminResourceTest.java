package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.service.AdminService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminResourceTest {

    @Mock
    private AdminService mockAdminService;

    @Mock
    private AdminResource adminResource;

    @Before
    public void before() {
        this.mockAdminService = Mockito.mock(AdminService.class);
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
    public void testView() throws Exception {
        Task task = new Task();
        task.setReferenceTaskName("test");
        List<Task> listOfTask = new ArrayList<>();
        listOfTask.add(task);
        when(mockAdminService.getListOfPendingTask(anyString(), anyInt(), anyInt())).thenReturn(listOfTask);
        assertEquals(listOfTask, adminResource.view("testTask", 0, 100));
    }

    @Test
    public void testRequeueSweep() {
        String workflowId = "w123";
        when(mockAdminService.requeueSweep(anyString())).thenReturn(workflowId);
        assertEquals(workflowId, adminResource.requeueSweep(workflowId));
    }
}
