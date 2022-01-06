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
package com.netflix.conductor.service;

import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.dao.QueueDAO;

import static com.netflix.conductor.TestUtils.getConstraintViolationMessages;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
public class TaskServiceTest {

    @TestConfiguration
    static class TestTaskConfiguration {

        @Bean
        public ExecutionService executionService() {
            return mock(ExecutionService.class);
        }

        @Bean
        public TaskService taskService(ExecutionService executionService) {
            QueueDAO queueDAO = mock(QueueDAO.class);
            return new TaskServiceImpl(executionService, queueDAO);
        }
    }

    @Autowired private TaskService taskService;

    @Autowired private ExecutionService executionService;

    @Test(expected = ConstraintViolationException.class)
    public void testPoll() {
        try {
            taskService.poll(null, null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testBatchPoll() {
        try {
            taskService.batchPoll(null, null, null, null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTasks() {
        try {
            taskService.getTasks(null, null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetPendingTaskForWorkflow() {
        try {
            taskService.getPendingTaskForWorkflow(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            assertTrue(messages.contains("TaskReferenceName cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTask() {
        try {
            taskService.updateTask(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskResult cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskInValid() {
        try {
            TaskResult taskResult = new TaskResult();
            taskService.updateTask(taskResult);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow Id cannot be null or empty"));
            assertTrue(messages.contains("Task ID cannot be null or empty"));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testAckTaskReceived() {
        try {
            taskService.ackTaskReceived(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test
    public void testAckTaskReceivedMissingWorkerId() {
        String ack = taskService.ackTaskReceived("abc", null);
        assertNotNull(ack);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testLog() {
        try {
            taskService.log(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTaskLogs() {
        try {
            taskService.getTaskLogs(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTask() {
        try {
            taskService.getTask(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRemoveTaskFromQueue() {
        try {
            taskService.removeTaskFromQueue(null, null);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetPollData() {
        try {
            taskService.getPollData(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRequeuePendingTask() {
        try {
            taskService.requeuePendingTask(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test
    public void testSearch() {
        SearchResult<TaskSummary> searchResult =
                new SearchResult<>(2, List.of(mock(TaskSummary.class), mock(TaskSummary.class)));
        when(executionService.getSearchTasks("query", "*", 0, 2, "Sort")).thenReturn(searchResult);
        assertEquals(searchResult, taskService.search(0, 2, "Sort", "*", "query"));
    }

    @Test
    public void testSearchV2() {
        SearchResult<Task> searchResult =
                new SearchResult<>(2, List.of(mock(Task.class), mock(Task.class)));
        when(executionService.getSearchTasksV2("query", "*", 0, 2, "Sort"))
                .thenReturn(searchResult);
        assertEquals(searchResult, taskService.searchV2(0, 2, "Sort", "*", "query"));
    }
}
