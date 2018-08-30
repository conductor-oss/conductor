/**
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
/**
 *
 */
package com.netflix.conductor.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
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
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.when;

/**
 * @author Viren
 */
public class TestWorkflowExecutor {

    private WorkflowExecutor workflowExecutor;
    private ExecutionDAO executionDAO;
    private QueueDAO queueDAO;

    @Before
    public void init() {
        TestConfiguration config = new TestConfiguration();
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        executionDAO = mock(ExecutionDAO.class);
        queueDAO = mock(QueueDAO.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ParametersUtils parametersUtils = new ParametersUtils();
        Map<String, TaskMapper> taskMappers = new HashMap<>();
        taskMappers.put("DECISION", new DecisionTaskMapper());
        taskMappers.put("DYNAMIC", new DynamicTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("FORK_JOIN", new ForkJoinTaskMapper());
        taskMappers.put("JOIN", new JoinTaskMapper());
        taskMappers.put("FORK_JOIN_DYNAMIC", new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper));
        taskMappers.put("USER_DEFINED", new UserDefinedTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("SIMPLE", new SimpleTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("SUB_WORKFLOW", new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put("EVENT", new EventTaskMapper(parametersUtils));
        taskMappers.put("WAIT", new WaitTaskMapper(parametersUtils));
        DeciderService deciderService = new DeciderService(metadataDAO, queueDAO, taskMappers);
        workflowExecutor = new WorkflowExecutor(deciderService, metadataDAO, executionDAO, queueDAO, config);
    }

    @Test
    public void testScheduleTask() throws Exception {

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
        taskToSchedule.setWorkflowTaskType(Type.USER_DEFINED);
        taskToSchedule.setType("HTTP");

        WorkflowTask taskToSchedule2 = new WorkflowTask();
        taskToSchedule2.setWorkflowTaskType(Type.USER_DEFINED);
        taskToSchedule2.setType("HTTP2");

        WorkflowTask wait = new WorkflowTask();
        wait.setWorkflowTaskType(Type.WAIT);
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
        task2.setEndTime(System.currentTimeMillis());
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


        when(executionDAO.createTasks(tasks)).thenReturn(tasks);
        AtomicInteger startedTaskCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            startedTaskCount.incrementAndGet();
            return null;
        }).when(executionDAO)
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
    public void testCompleteWorkflow() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("1");
        workflow.setWorkflowType("test");
        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setStartTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(executionDAO.getWorkflow(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateWorkflowCalledCounter.incrementAndGet();
            return null;
        }).when(executionDAO).updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            updateTasksCalledCounter.incrementAndGet();
            return null;
        }).when(executionDAO).updateTasks(any());

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
    }
}
