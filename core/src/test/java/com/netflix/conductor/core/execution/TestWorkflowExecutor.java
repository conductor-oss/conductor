/*
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.orchestration.DataAccessor;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Viren
 */
public class TestWorkflowExecutor {

    private WorkflowExecutor workflowExecutor;
    private DataAccessor dataAccessor;
    private MetadataDAO metadataDAO;
    private QueueDAO queueDAO;
    private WorkflowStatusListener workflowStatusListener;

    @Before
    public void init() {
        TestConfiguration config = new TestConfiguration();
        dataAccessor = mock(DataAccessor.class);
        metadataDAO = mock(MetadataDAO.class);
        queueDAO = mock(QueueDAO.class);
        workflowStatusListener = mock(WorkflowStatusListener.class);
        ExternalPayloadStorageUtils externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ParametersUtils parametersUtils = new ParametersUtils();
        Map<String, TaskMapper> taskMappers = new HashMap<>();
        taskMappers.put("DECISION", new DecisionTaskMapper());
        taskMappers.put("DYNAMIC", new DynamicTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("FORK_JOIN", new ForkJoinTaskMapper());
        taskMappers.put("JOIN", new JoinTaskMapper());
        taskMappers.put("FORK_JOIN_DYNAMIC", new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper, metadataDAO));
        taskMappers.put("USER_DEFINED", new UserDefinedTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("SIMPLE", new SimpleTaskMapper(parametersUtils));
        taskMappers.put("SUB_WORKFLOW", new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("EVENT", new EventTaskMapper(parametersUtils));
        taskMappers.put("WAIT", new WaitTaskMapper(parametersUtils));

        DeciderService deciderService = new DeciderService(parametersUtils, queueDAO, externalPayloadStorageUtils, taskMappers);
        MetadataMapperService metadataMapperService = new MetadataMapperService(metadataDAO);
        workflowExecutor = new WorkflowExecutor(deciderService, metadataDAO, queueDAO, metadataMapperService, workflowStatusListener, dataAccessor, config);
    }

    @Test
    public void testScheduleTask() {

        AtomicBoolean httpTaskExecuted = new AtomicBoolean(false);
        AtomicBoolean http2TaskExecuted = new AtomicBoolean(false);

        new Wait();
        new WorkflowSystemTask("HTTP") {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
                httpTaskExecuted.set(true);
                task.setStatus(Status.COMPLETED);
                super.start(workflow, task, executor);
            }
        };

        new WorkflowSystemTask("HTTP2") {

            @Override
            public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
                http2TaskExecuted.set(true);
                task.setStatus(Status.COMPLETED);
                super.start(workflow, task, executor);
            }
        };

        Workflow workflow = new Workflow();
        workflow.setWorkflowId("1");

        List<Task> tasks = new LinkedList<>();

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setWorkflowTaskType(TaskType.USER_DEFINED);
        taskToSchedule.setType("HTTP");

        WorkflowTask taskToSchedule2 = new WorkflowTask();
        taskToSchedule2.setWorkflowTaskType(TaskType.USER_DEFINED);
        taskToSchedule2.setType("HTTP2");

        WorkflowTask wait = new WorkflowTask();
        wait.setWorkflowTaskType(TaskType.WAIT);
        wait.setType("WAIT");
        wait.setTaskReferenceName("wait");

        Task task1 = new Task();
        task1.setTaskType(taskToSchedule.getType());
        task1.setTaskDefName(taskToSchedule.getName());
        task1.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task1.setWorkflowInstanceId(workflow.getWorkflowId());
        task1.setCorrelationId(workflow.getCorrelationId());
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(IDGenerator.generate());
        task1.setInputData(new HashMap<>());
        task1.setStatus(Status.SCHEDULED);
        task1.setRetryCount(0);
        task1.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task1.setWorkflowTask(taskToSchedule);


        Task task2 = new Task();
        task2.setTaskType(Wait.NAME);
        task2.setTaskDefName(taskToSchedule.getName());
        task2.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task2.setWorkflowInstanceId(workflow.getWorkflowId());
        task2.setCorrelationId(workflow.getCorrelationId());
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setInputData(new HashMap<>());
        task2.setTaskId(IDGenerator.generate());
        task2.setStatus(Status.IN_PROGRESS);
        task2.setWorkflowTask(taskToSchedule);

        Task task3 = new Task();
        task3.setTaskType(taskToSchedule2.getType());
        task3.setTaskDefName(taskToSchedule.getName());
        task3.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task3.setWorkflowInstanceId(workflow.getWorkflowId());
        task3.setCorrelationId(workflow.getCorrelationId());
        task3.setScheduledTime(System.currentTimeMillis());
        task3.setTaskId(IDGenerator.generate());
        task3.setInputData(new HashMap<>());
        task3.setStatus(Status.SCHEDULED);
        task3.setRetryCount(0);
        task3.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task3.setWorkflowTask(taskToSchedule);

        tasks.add(task1);
        tasks.add(task2);
        tasks.add(task3);


        when(dataAccessor.createTasks(tasks)).thenReturn(tasks);
        AtomicInteger startedTaskCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            startedTaskCount.incrementAndGet();
            return null;
        }).when(dataAccessor)
                .updateTask(any());

        AtomicInteger queuedTaskCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            String queueName = invocation.getArgumentAt(0, String.class);
            System.out.println(queueName);
            queuedTaskCount.incrementAndGet();
            return null;
        }).when(queueDAO)
                .push(any(), any(), anyInt());

        boolean stateChanged = workflowExecutor.scheduleTask(workflow, tasks);
        assertEquals(2, startedTaskCount.get());
        assertEquals(1, queuedTaskCount.get());
        assertTrue(stateChanged);
        assertFalse(httpTaskExecuted.get());
        assertTrue(http2TaskExecuted.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCompleteWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setStartTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(dataAccessor.getWorkflowById(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateWorkflowCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateTasksCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateTasks(any());

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            removeQueueEntryCalledCounter.incrementAndGet();
            return null;
        }).when(queueDAO).remove(anyString(), anyString());

        workflowExecutor.completeWorkflow(workflow);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, updateTasksCalledCounter.get());
        assertEquals(1, removeQueueEntryCalledCounter.get());

        verify(workflowStatusListener, times(0)).onWorkflowCompleted(any(Workflow.class));

        def.setWorkflowStatusListenerEnabled(true);
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflowExecutor.completeWorkflow(workflow);
        verify(workflowStatusListener, times(1)).onWorkflowCompleted(any(Workflow.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTerminatedWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setStartTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(dataAccessor.getWorkflowById(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateWorkflowCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateTasksCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateTasks(any());

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            removeQueueEntryCalledCounter.incrementAndGet();
            return null;
        }).when(queueDAO).remove(anyString(), anyString());

        workflowExecutor.terminateWorkflow("workflowId", "reason");
        assertEquals(Workflow.WorkflowStatus.TERMINATED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, removeQueueEntryCalledCounter.get());

        verify(workflowStatusListener, times(0)).onWorkflowTerminated(any(Workflow.class));

        def.setWorkflowStatusListenerEnabled(true);
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflowExecutor.completeWorkflow(workflow);
        verify(workflowStatusListener, times(1)).onWorkflowCompleted(any(Workflow.class));
    }
    
    @Test
    public void testGetFailedTasksToRetry() {
        //setup
        Task task_1_1 = new Task();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(1);
        task_1_1.setStatus(Status.FAILED);
        task_1_1.setTaskDefName("task_1_def");
        task_1_1.setReferenceTaskName("task_1_ref_1");

        Task task_1_2 = new Task();
        task_1_2.setTaskId(UUID.randomUUID().toString());
        task_1_2.setSeq(10);
        task_1_2.setStatus(Status.FAILED);
        task_1_2.setTaskDefName("task_1_def");
        task_1_2.setReferenceTaskName("task_1_ref_2");

        Task task_1_3_1 = new Task();
        task_1_3_1.setTaskId(UUID.randomUUID().toString());
        task_1_3_1.setSeq(100);
        task_1_3_1.setStatus(Status.FAILED);
        task_1_3_1.setTaskDefName("task_1_def");
        task_1_3_1.setReferenceTaskName("task_1_ref_3");


        Task task_1_3_2 = new Task();
        task_1_3_2.setTaskId(UUID.randomUUID().toString());
        task_1_3_2.setSeq(101);
        task_1_3_2.setStatus(Status.FAILED);
        task_1_3_2.setTaskDefName("task_1_def");
        task_1_3_2.setReferenceTaskName("task_1_ref_3");


        Task task_2_1 = new Task();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(2);
        task_2_1.setStatus(Status.COMPLETED);
        task_2_1.setTaskDefName("task_2_def");
        task_2_1.setReferenceTaskName("task_2_ref_1");

        Task task_2_2 = new Task();
        task_2_2.setTaskId(UUID.randomUUID().toString());
        task_2_2.setSeq(20);
        task_2_2.setStatus(Status.FAILED);
        task_2_2.setTaskDefName("task_2_def");
        task_2_2.setReferenceTaskName("task_2_ref_2");

        Task task_3_1 = new Task();
        task_3_1.setTaskId(UUID.randomUUID().toString());
        task_3_1.setSeq(20);
        task_3_1.setStatus(Status.TIMED_OUT);
        task_3_1.setTaskDefName("task_3_def");
        task_3_1.setReferenceTaskName("task_3_ref_1");

        Workflow workflow = new Workflow();

        //2 different task definitions
        workflow.setTasks(Arrays.asList(task_1_1, task_2_1));
        List<Task> tasks = workflowExecutor.getFailedTasksToRetry(workflow);
        assertEquals(1, tasks.size());
        assertEquals(task_1_1.getTaskId(), tasks.get(0).getTaskId());

        //2 tasks with the same  definition but different reference numbers
        workflow.setTasks(Arrays.asList(task_1_3_1, task_1_3_2));
        tasks = workflowExecutor.getFailedTasksToRetry(workflow);
        assertEquals(1, tasks.size());
        assertEquals(task_1_3_2.getTaskId(), tasks.get(0).getTaskId());

        //3 tasks with definitions and reference numbers
        workflow.setTasks(Arrays.asList(task_1_1, task_1_2, task_1_3_1, task_1_3_2, task_2_1, task_2_2, task_3_1));
        tasks = workflowExecutor.getFailedTasksToRetry(workflow);
        assertEquals(4, tasks.size());
        assertTrue(tasks.contains(task_1_1));
        assertTrue(tasks.contains(task_1_2));
        assertTrue(tasks.contains(task_2_2));
        assertTrue(tasks.contains(task_1_3_2));
    }


    @Test(expected = ApplicationException.class)
    public void testRetryNonTerminalWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("testRetryNonTerminalWorkflow");
        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        when(dataAccessor.getWorkflowById(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.retry(workflow.getWorkflowId());

    }

    @Test(expected = ApplicationException.class)
    public void testRetryWorkflowNoTasks() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("ApplicationException");
        workflow.setStatus(Workflow.WorkflowStatus.FAILED);
        //noinspection unchecked
        workflow.setTasks(new ArrayList());
        when(dataAccessor.getWorkflowById(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.retry(workflow.getWorkflowId());
    }

    @Test
    public void testRetryWorkflow() {
        //setup
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("testRetryWorkflowId");
        workflow.setWorkflowType("testRetryWorkflowId");
        workflow.setVersion(1);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setStartTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(Workflow.WorkflowStatus.FAILED);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateWorkflowCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateTasksCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateTasks(any());

        AtomicInteger updateTasksAlledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateTasksCalledCounter.incrementAndGet();
            return null;
        }).when(dataAccessor).updateTask(any());

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            removeQueueEntryCalledCounter.incrementAndGet();
            return null;
        }).when(queueDAO).remove(anyString(), anyString());

        // add 2 failed task in 2 forks and 1 cancelled in the 3rd fork
        Task task_1_1 = new Task();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(Status.CANCELED);
        task_1_1.setTaskDefName("task1");
        task_1_1.setReferenceTaskName("task1_ref1");

        Task task_1_2 = new Task();
        task_1_2.setTaskId(UUID.randomUUID().toString());
        task_1_2.setSeq(21);
        task_1_2.setTaskType(TaskType.SIMPLE.toString());
        task_1_2.setStatus(Status.FAILED);
        task_1_2.setTaskDefName("task1");
        task_1_2.setReferenceTaskName("task1_ref1");

        Task task_2_1 = new Task();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setStatus(Status.FAILED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setReferenceTaskName("task2_ref1");


        Task task_3_1 = new Task();
        task_3_1.setTaskId(UUID.randomUUID().toString());
        task_3_1.setSeq(23);
        task_3_1.setStatus(Status.CANCELED);
        task_3_1.setTaskType(TaskType.SIMPLE.toString());
        task_3_1.setTaskDefName("task3");
        task_3_1.setReferenceTaskName("task3_ref1");

        Task task_4_1 = new Task();
        task_4_1.setTaskId(UUID.randomUUID().toString());
        task_4_1.setSeq(122);
        task_4_1.setStatus(Status.FAILED);
        task_4_1.setTaskType(TaskType.SIMPLE.toString());
        task_4_1.setTaskDefName("task1");
        task_4_1.setReferenceTaskName("task4_refABC");

        workflow.setTasks(Arrays.asList(task_1_1, task_1_2, task_2_1, task_3_1, task_4_1));
        //end of setup

        //when:
        when(dataAccessor.getWorkflowById(anyString(), anyBoolean())).thenReturn(workflow);
        WorkflowDef workflowDef = new WorkflowDef();
        when(metadataDAO.get(anyString(), anyInt())).thenReturn(Optional.of(workflowDef));

        workflowExecutor.retry(workflow.getWorkflowId());

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, updateWorkflowCalledCounter.get());
        assertEquals(7, updateTasksCalledCounter.get());
        assertEquals(1, removeQueueEntryCalledCounter.get());
    }
}
