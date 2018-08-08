package com.netflix.conductor.server.resources;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.server.resources.TaskResource;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.common.metadata.tasks.Task;
import org.junit.Test;

import org.junit.Before;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class TaskResourceTest {

    private ExecutionService mockTaskService;

    private QueueDAO mockQueueDAO;

    private TaskResource taskResource;

    @Before
    public void before() {
        this.mockTaskService = Mockito.mock(ExecutionService.class);
        this.mockQueueDAO = Mockito.mock(QueueDAO.class);
        this.taskResource = new TaskResource(this.mockTaskService, this.mockQueueDAO);
    }

    @Test
    public void testPoll() throws Exception {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");

        when(mockTaskService.getLastPollTask(anyString(), anyString(), anyString())).thenReturn(task);
        assertEquals(task, taskResource.poll("SIMPLE", "123", "test"));
    }

    @Test
    public void testBatchPoll() throws Exception{
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        List<Task> listOfTasks = new ArrayList<>();
        listOfTasks.add(task);

        when(mockTaskService.poll(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(listOfTasks);
        assertEquals(listOfTasks, taskResource.batchPoll("SIMPLE", "123",
                "test", 1, 100));
    }

    @Test
    public void testGetInProgressTasks() throws Exception {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        List<Task> listOfTasks = new ArrayList<>();
        listOfTasks.add(task);

        when(mockTaskService.getTasks(anyString(), anyString(), anyInt())).thenReturn(listOfTasks);
        assertEquals(listOfTasks, taskResource.getTasks("SIMPLE", "123", 123));
    }

    @Test
    public void testGetPendingTaskForWorkflow() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        when(mockTaskService.getPendingTaskForWorkflow(anyString(), anyString())).thenReturn(task);
        assertEquals(task, taskResource.getPendingTaskForWorkflow("SIMPLE", "123"));
    }

    @Test
    public void testUpdateTask() throws Exception {
        TaskResult taskResult = new TaskResult();
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.setTaskId("123");
        assertEquals("123", taskResource.updateTask(taskResult));
    }

    @Test
    public void testAck() throws Exception {
        Boolean acked = true;
        when(mockTaskService.ackTaskReceived(anyString())).thenReturn(acked);
        assertEquals("true", taskResource.ack("123", "456"));
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
    public void testGetTask() throws Exception {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        when(mockTaskService.getTask(anyString())).thenReturn(task);
        assertEquals(task, taskResource.getTask("123"));
    }

    @Test
    public void testRemoveTaskFromQueue() {
        taskResource.removeTaskFromQueue("SIMPLE", "123");
        verify(mockTaskService, times(1)).removeTaskfromQueue(anyString(), anyString());
    }

    @Test
    public void testSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("test1", 1);
        map.put("test2", 2);

        ArrayList<String> list = new ArrayList<String>() {{
            add("test1");
            add("test2");
        }};

        when(mockTaskService.getTaskQueueSizes(anyListOf(String.class))).thenReturn(map);
        assertEquals(map, taskResource.size(list));
    }

    @Test
    public void testAllVerbose() {

        Map<String, Long> map = new HashMap<>();
        map.put("queue1", 1L);
        map.put("queue2", 2L);

        Map<String, Map<String, Long>>  mapOfMap = new HashMap<>();
        mapOfMap.put("queue", map);

        Map<String, Map<String, Map<String, Long>>> queueSizeMap = new HashMap<>();
        queueSizeMap.put("queue", mapOfMap);

        when(mockQueueDAO.queuesDetailVerbose()).thenReturn(queueSizeMap);
        assertEquals(queueSizeMap, taskResource.allVerbose());
    }

    @Test
    public void testQueueDetails() {
        Map<String, Long> map = new HashMap<>();
        map.put("queue1", 1L);
        map.put("queue2", 2L);

        when(mockQueueDAO.queuesDetail()).thenReturn(map);
        assertEquals(map, taskResource.all());
    }

    @Test
    public void testGetPollData() throws Exception{
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
    public void testRequeue() throws Exception {
        when(mockTaskService.requeuePendingTasks()).thenReturn(1);
        assertEquals("1", taskResource.requeue());
    }

    @Test
    public void testRequeueTaskType() throws Exception {
        when(mockTaskService.requeuePendingTasks(anyString())).thenReturn(1);
        assertEquals("1", taskResource.requeue("SIMPLE"));
    }

    @Test
    public void search() {
        Task task = new Task();
        task.setTaskType("SIMPLE");
        task.setWorkerId("123");
        task.setDomain("test");
        task.setStatus(Task.Status.IN_PROGRESS);
        TaskSummary taskSummary = new TaskSummary(task);
        ArrayList<TaskSummary> listOfTaskSummary = new ArrayList<TaskSummary>() {{
            add(taskSummary);
        }};
        SearchResult<TaskSummary> searchResult = new SearchResult<TaskSummary>(100, listOfTaskSummary);
        listOfTaskSummary.add(taskSummary);

        when(mockTaskService.getSearchTasks(anyString(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(searchResult);
        assertEquals(searchResult, taskResource.search(0,100,"asc", "*", "*"));
    }
}