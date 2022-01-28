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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.service.TaskService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskResourceTest {

    private TaskService mockTaskService;

    private TaskResource taskResource;

    @Before
    public void before() {
        this.mockTaskService = mock(TaskService.class);
        this.taskResource = new TaskResource(this.mockTaskService);
    }

    @Test
    public void testPoll() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");

        when(mockTaskService.poll(anyString(), anyString(), anyString())).thenReturn(task);
        assertEquals(ResponseEntity.ok(task), taskResource.poll("SIMPLE", "123", "test"));
    }

    @Test
    public void testBatchPoll() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        List<Task> listOfTasks = new ArrayList<>();
        listOfTasks.add(task);

        when(mockTaskService.batchPoll(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(listOfTasks);
        assertEquals(
                ResponseEntity.ok(listOfTasks),
                taskResource.batchPoll("SIMPLE", "123", "test", 1, 100));
    }

    @Test
    public void testUpdateTask() {
        TaskResult taskResult = new TaskResult();
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId("123");
        when(mockTaskService.updateTask(any(TaskResult.class))).thenReturn("123");
        assertEquals("123", taskResource.updateTask(taskResult));
    }

    @Test
    public void testLog() {
        taskResource.log("123", "test log");
        verify(mockTaskService, times(1)).log(anyString(), anyString());
    }

    @Test
    public void testGetTaskLogs() {
        List<TaskExecLog> listOfLogs = new ArrayList<>();
        listOfLogs.add(new TaskExecLog("test log"));
        when(mockTaskService.getTaskLogs(anyString())).thenReturn(listOfLogs);
        assertEquals(listOfLogs, taskResource.getTaskLogs("123"));
    }

    @Test
    public void testGetTask() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        when(mockTaskService.getTask(anyString())).thenReturn(task);
        ResponseEntity<Task> entity = taskResource.getTask("123");
        assertNotNull(entity);
        assertEquals(task, entity.getBody());
    }

    @Test
    public void testSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("test1", 1);
        map.put("test2", 2);

        List<String> list = new ArrayList<>();
        list.add("test1");
        list.add("test2");

        when(mockTaskService.getTaskQueueSizes(anyList())).thenReturn(map);
        assertEquals(map, taskResource.size(list));
    }

    @Test
    public void testAllVerbose() {
        Map<String, Long> map = new HashMap<>();
        map.put("queue1", 1L);
        map.put("queue2", 2L);

        Map<String, Map<String, Long>> mapOfMap = new HashMap<>();
        mapOfMap.put("queue", map);

        Map<String, Map<String, Map<String, Long>>> queueSizeMap = new HashMap<>();
        queueSizeMap.put("queue", mapOfMap);

        when(mockTaskService.allVerbose()).thenReturn(queueSizeMap);
        assertEquals(queueSizeMap, taskResource.allVerbose());
    }

    @Test
    public void testQueueDetails() {
        Map<String, Long> map = new HashMap<>();
        map.put("queue1", 1L);
        map.put("queue2", 2L);

        when(mockTaskService.getAllQueueDetails()).thenReturn(map);
        assertEquals(map, taskResource.all());
    }

    @Test
    public void testGetPollData() {
        PollData pollData = new PollData("queue", "test", "w123", 100);
        List<PollData> listOfPollData = new ArrayList<>();
        listOfPollData.add(pollData);

        when(mockTaskService.getPollData(anyString())).thenReturn(listOfPollData);
        assertEquals(listOfPollData, taskResource.getPollData("w123"));
    }

    @Test
    public void testGetAllPollData() {
        PollData pollData = new PollData("queue", "test", "w123", 100);
        List<PollData> listOfPollData = new ArrayList<>();
        listOfPollData.add(pollData);

        when(mockTaskService.getAllPollData()).thenReturn(listOfPollData);
        assertEquals(listOfPollData, taskResource.getAllPollData());
    }

    @Test
    public void testRequeueTaskType() {
        when(mockTaskService.requeuePendingTask(anyString())).thenReturn("1");
        assertEquals("1", taskResource.requeuePendingTask("SIMPLE"));
    }

    @Test
    public void testSearch() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        TaskSummary taskSummary = new TaskSummary(task);
        List<TaskSummary> listOfTaskSummary = Collections.singletonList(taskSummary);
        SearchResult<TaskSummary> searchResult = new SearchResult<>(100, listOfTaskSummary);

        when(mockTaskService.search(0, 100, "asc", "*", "*")).thenReturn(searchResult);
        assertEquals(searchResult, taskResource.search(0, 100, "asc", "*", "*"));
    }

    @Test
    public void testSearchV2() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        List<Task> listOfTasks = Collections.singletonList(task);
        SearchResult<Task> searchResult = new SearchResult<>(100, listOfTasks);

        when(mockTaskService.searchV2(0, 100, "asc", "*", "*")).thenReturn(searchResult);
        assertEquals(searchResult, taskResource.searchV2(0, 100, "asc", "*", "*"));
    }

    @Test
    public void testGetExternalStorageLocation() {
        ExternalStorageLocation externalStorageLocation = mock(ExternalStorageLocation.class);
        when(mockTaskService.getExternalStorageLocation("path", "operation", "payloadType"))
                .thenReturn(externalStorageLocation);
        assertEquals(
                externalStorageLocation,
                taskResource.getExternalStorageLocation("path", "operation", "payloadType"));
    }
}
