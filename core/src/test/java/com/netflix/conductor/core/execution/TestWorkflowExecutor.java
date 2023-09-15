/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.event.WorkflowCreationEvent;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.mapper.*;
import com.netflix.conductor.core.execution.tasks.*;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.operation.StartWorkflowOperation;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            TestWorkflowExecutor.TestConfiguration.class
        })
@RunWith(SpringRunner.class)
public class TestWorkflowExecutor {

    private WorkflowExecutor workflowExecutor;
    private ExecutionDAOFacade executionDAOFacade;
    private MetadataDAO metadataDAO;
    private QueueDAO queueDAO;
    private WorkflowStatusListener workflowStatusListener;
    private TaskStatusListener taskStatusListener;
    private ExecutionLockService executionLockService;
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;

    @Configuration
    @ComponentScan(basePackageClasses = {Evaluator.class}) // load all Evaluator beans.
    public static class TestConfiguration {

        @Bean(TASK_TYPE_SUB_WORKFLOW)
        public SubWorkflow subWorkflow(ObjectMapper objectMapper) {
            return new SubWorkflow(objectMapper, mock(StartWorkflowOperation.class));
        }

        @Bean(TASK_TYPE_LAMBDA)
        public Lambda lambda() {
            return new Lambda();
        }

        @Bean(TASK_TYPE_WAIT)
        public Wait waitBean() {
            return new Wait();
        }

        @Bean("HTTP")
        public WorkflowSystemTask http() {
            return new WorkflowSystemTaskStub("HTTP") {
                @Override
                public boolean isAsync() {
                    return true;
                }
            };
        }

        @Bean("HTTP2")
        public WorkflowSystemTask http2() {
            return new WorkflowSystemTaskStub("HTTP2");
        }

        @Bean(TASK_TYPE_JSON_JQ_TRANSFORM)
        public WorkflowSystemTask jsonBean() {
            return new WorkflowSystemTaskStub("JSON_JQ_TRANSFORM") {
                @Override
                public boolean isAsync() {
                    return false;
                }

                @Override
                public void start(
                        WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                    task.setStatus(TaskModel.Status.COMPLETED);
                }
            };
        }

        @Bean
        public SystemTaskRegistry systemTaskRegistry(Set<WorkflowSystemTask> tasks) {
            return new SystemTaskRegistry(tasks);
        }
    }

    @Autowired private ObjectMapper objectMapper;

    @Autowired private SystemTaskRegistry systemTaskRegistry;

    @Autowired private DefaultListableBeanFactory beanFactory;

    @Autowired private Map<String, Evaluator> evaluators;

    private ApplicationEventPublisher eventPublisher;

    @Before
    public void init() {
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        metadataDAO = mock(MetadataDAO.class);
        queueDAO = mock(QueueDAO.class);
        workflowStatusListener = mock(WorkflowStatusListener.class);
        taskStatusListener = mock(TaskStatusListener.class);
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        executionLockService = mock(ExecutionLockService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        ParametersUtils parametersUtils = new ParametersUtils(objectMapper);
        IDGenerator idGenerator = new IDGenerator();
        Map<String, TaskMapper> taskMappers = new HashMap<>();
        taskMappers.put(DECISION.name(), new DecisionTaskMapper());
        taskMappers.put(SWITCH.name(), new SwitchTaskMapper(evaluators));
        taskMappers.put(DYNAMIC.name(), new DynamicTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(FORK_JOIN.name(), new ForkJoinTaskMapper());
        taskMappers.put(JOIN.name(), new JoinTaskMapper());
        taskMappers.put(
                FORK_JOIN_DYNAMIC.name(),
                new ForkJoinDynamicTaskMapper(
                        idGenerator, parametersUtils, objectMapper, metadataDAO));
        taskMappers.put(
                USER_DEFINED.name(), new UserDefinedTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(SIMPLE.name(), new SimpleTaskMapper(parametersUtils));
        taskMappers.put(
                SUB_WORKFLOW.name(), new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(EVENT.name(), new EventTaskMapper(parametersUtils));
        taskMappers.put(WAIT.name(), new WaitTaskMapper(parametersUtils));
        taskMappers.put(HTTP.name(), new HTTPTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(LAMBDA.name(), new LambdaTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(INLINE.name(), new InlineTaskMapper(parametersUtils, metadataDAO));

        DeciderService deciderService =
                new DeciderService(
                        idGenerator,
                        parametersUtils,
                        metadataDAO,
                        externalPayloadStorageUtils,
                        systemTaskRegistry,
                        taskMappers,
                        Duration.ofMinutes(60));
        MetadataMapperService metadataMapperService = new MetadataMapperService(metadataDAO);

        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getActiveWorkerLastPollTimeout()).thenReturn(Duration.ofSeconds(100));
        when(properties.getTaskExecutionPostponeDuration()).thenReturn(Duration.ofSeconds(60));
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));

        workflowExecutor =
                new WorkflowExecutor(
                        deciderService,
                        metadataDAO,
                        queueDAO,
                        metadataMapperService,
                        workflowStatusListener,
                        taskStatusListener,
                        executionDAOFacade,
                        properties,
                        executionLockService,
                        systemTaskRegistry,
                        parametersUtils,
                        idGenerator,
                        eventPublisher);
    }

    @Test
    public void testScheduleTask() {
        IDGenerator idGenerator = new IDGenerator();
        WorkflowSystemTaskStub httpTask = beanFactory.getBean("HTTP", WorkflowSystemTaskStub.class);
        WorkflowSystemTaskStub http2Task =
                beanFactory.getBean("HTTP2", WorkflowSystemTaskStub.class);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("1");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("1");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        List<TaskModel> tasks = new LinkedList<>();

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

        TaskModel task1 = new TaskModel();
        task1.setTaskType(taskToSchedule.getType());
        task1.setTaskDefName(taskToSchedule.getName());
        task1.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task1.setWorkflowInstanceId(workflow.getWorkflowId());
        task1.setCorrelationId(workflow.getCorrelationId());
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setInputData(new HashMap<>());
        task1.setStatus(TaskModel.Status.SCHEDULED);
        task1.setRetryCount(0);
        task1.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task1.setWorkflowTask(taskToSchedule);

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TASK_TYPE_WAIT);
        task2.setTaskDefName(taskToSchedule.getName());
        task2.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task2.setWorkflowInstanceId(workflow.getWorkflowId());
        task2.setCorrelationId(workflow.getCorrelationId());
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setInputData(new HashMap<>());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.IN_PROGRESS);
        task2.setWorkflowTask(taskToSchedule);

        TaskModel task3 = new TaskModel();
        task3.setTaskType(taskToSchedule2.getType());
        task3.setTaskDefName(taskToSchedule.getName());
        task3.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task3.setWorkflowInstanceId(workflow.getWorkflowId());
        task3.setCorrelationId(workflow.getCorrelationId());
        task3.setScheduledTime(System.currentTimeMillis());
        task3.setTaskId(idGenerator.generate());
        task3.setInputData(new HashMap<>());
        task3.setStatus(TaskModel.Status.SCHEDULED);
        task3.setRetryCount(0);
        task3.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task3.setWorkflowTask(taskToSchedule);

        tasks.add(task1);
        tasks.add(task2);
        tasks.add(task3);

        when(executionDAOFacade.createTasks(tasks)).thenReturn(tasks);
        AtomicInteger startedTaskCount = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            startedTaskCount.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTask(any());

        AtomicInteger queuedTaskCount = new AtomicInteger(0);
        final Answer answer =
                invocation -> {
                    String queueName = invocation.getArgument(0, String.class);
                    queuedTaskCount.incrementAndGet();
                    return null;
                };
        doAnswer(answer).when(queueDAO).push(any(), any(), anyLong());
        doAnswer(answer).when(queueDAO).push(any(), any(), anyInt(), anyLong());

        boolean stateChanged = workflowExecutor.scheduleTask(workflow, tasks);
        // Wait task is no async to it will be queued.
        assertEquals(1, startedTaskCount.get());
        assertEquals(2, queuedTaskCount.get());
        assertTrue(stateChanged);
        assertFalse(httpTask.isStarted());
        assertTrue(http2Task.isStarted());
    }

    @Test(expected = TerminateWorkflowException.class)
    public void testScheduleTaskFailure() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wid_01");

        List<TaskModel> tasks = new LinkedList<>();

        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        task1.setTaskDefName("task_1");
        task1.setReferenceTaskName("task_1");
        task1.setWorkflowInstanceId(workflow.getWorkflowId());
        task1.setTaskId("tid_01");
        task1.setStatus(TaskModel.Status.SCHEDULED);
        task1.setRetryCount(0);

        tasks.add(task1);

        when(executionDAOFacade.createTasks(tasks)).thenThrow(new RuntimeException());
        workflowExecutor.scheduleTask(workflow, tasks);
    }

    /** Simulate Queue push failures and assert that scheduleTask doesn't throw an exception. */
    @Test
    public void testQueueFailuresDuringScheduleTask() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wid_01");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("wid");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        List<TaskModel> tasks = new LinkedList<>();

        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        task1.setTaskDefName("task_1");
        task1.setReferenceTaskName("task_1");
        task1.setWorkflowInstanceId(workflow.getWorkflowId());
        task1.setTaskId("tid_01");
        task1.setStatus(TaskModel.Status.SCHEDULED);
        task1.setRetryCount(0);

        tasks.add(task1);

        when(executionDAOFacade.createTasks(tasks)).thenReturn(tasks);
        doThrow(new RuntimeException())
                .when(queueDAO)
                .push(anyString(), anyString(), anyInt(), anyLong());
        assertFalse(workflowExecutor.scheduleTask(workflow, tasks));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCompleteWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTasksCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            removeQueueEntryCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(queueDAO)
                .remove(anyString(), anyString());

        workflowExecutor.completeWorkflow(workflow);
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(0, updateTasksCalledCounter.get());
        assertEquals(0, removeQueueEntryCalledCounter.get());
        verify(workflowStatusListener, times(1))
                .onWorkflowCompletedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(0))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));

        def.setWorkflowStatusListenerEnabled(true);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflowExecutor.completeWorkflow(workflow);
        verify(workflowStatusListener, times(2))
                .onWorkflowCompletedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(0))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTerminateWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTasksCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            removeQueueEntryCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(queueDAO)
                .remove(anyString(), anyString());

        workflowExecutor.terminateWorkflow("workflowId", "reason");
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, removeQueueEntryCalledCounter.get());

        verify(workflowStatusListener, times(1))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(1))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));

        def.setWorkflowStatusListenerEnabled(true);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflowExecutor.completeWorkflow(workflow);
        verify(workflowStatusListener, times(1))
                .onWorkflowCompletedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(1))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    public void testUploadOutputFailuresDuringTerminateWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setWorkflowStatusListenerEnabled(true);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        List<TaskModel> tasks = new LinkedList<>();

        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(UUID.randomUUID().toString());
        task.setReferenceTaskName("t1");
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setTaskDefName("task1");
        task.setStatus(TaskModel.Status.IN_PROGRESS);

        tasks.add(task);
        workflow.setTasks(tasks);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        doThrow(new RuntimeException("any exception"))
                .when(externalPayloadStorageUtils)
                .verifyAndUpload(workflow, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT);

        workflowExecutor.terminateWorkflow(workflow.getWorkflowId(), "reason");
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        verify(workflowStatusListener, times(1))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQueueExceptionsIgnoredDuringTerminateWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");
        def.setWorkflowStatusListenerEnabled(true);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTasksCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        doThrow(new RuntimeException()).when(queueDAO).remove(anyString(), anyString());

        workflowExecutor.terminateWorkflow("workflowId", "reason");
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        verify(workflowStatusListener, times(1))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    public void testRestartWorkflow() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("test_task");
        workflowTask.setTaskReferenceName("task_ref");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testDef");
        workflowDef.setVersion(1);
        workflowDef.setRestartable(true);
        workflowDef.getTasks().add(workflowTask);

        TaskModel task_1 = new TaskModel();
        task_1.setTaskId(UUID.randomUUID().toString());
        task_1.setSeq(1);
        task_1.setStatus(TaskModel.Status.FAILED);
        task_1.setTaskDefName(workflowTask.getName());
        task_1.setReferenceTaskName(workflowTask.getTaskReferenceName());

        TaskModel task_2 = new TaskModel();
        task_2.setTaskId(UUID.randomUUID().toString());
        task_2.setSeq(2);
        task_2.setStatus(TaskModel.Status.FAILED);
        task_2.setTaskDefName(workflowTask.getName());
        task_2.setReferenceTaskName(workflowTask.getTaskReferenceName());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setWorkflowId("test-workflow-id");
        workflow.getTasks().addAll(Arrays.asList(task_1, task_2));
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setEndTime(500);
        workflow.setLastRetriedTime(100);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        doNothing().when(executionDAOFacade).removeTask(any());
        when(metadataDAO.getWorkflowDef(workflow.getWorkflowName(), workflow.getWorkflowVersion()))
                .thenReturn(Optional.of(workflowDef));
        when(metadataDAO.getTaskDef(workflowTask.getName())).thenReturn(new TaskDef());
        when(executionDAOFacade.updateWorkflow(any())).thenReturn("");

        workflowExecutor.restart(workflow.getWorkflowId(), false);
        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(0, workflow.getEndTime());
        assertEquals(0, workflow.getLastRetriedTime());
        verify(metadataDAO, never()).getLatestWorkflowDef(any());

        ArgumentCaptor<WorkflowModel> argumentCaptor = ArgumentCaptor.forClass(WorkflowModel.class);
        verify(executionDAOFacade, times(1)).createWorkflow(argumentCaptor.capture());
        assertEquals(
                workflow.getWorkflowId(), argumentCaptor.getAllValues().get(0).getWorkflowId());
        assertEquals(
                workflow.getWorkflowDefinition(),
                argumentCaptor.getAllValues().get(0).getWorkflowDefinition());

        // add a new version of the workflow definition and restart with latest
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setEndTime(500);
        workflow.setLastRetriedTime(100);
        workflowDef = new WorkflowDef();
        workflowDef.setName("testDef");
        workflowDef.setVersion(2);
        workflowDef.setRestartable(true);
        workflowDef.getTasks().addAll(Collections.singletonList(workflowTask));

        when(metadataDAO.getLatestWorkflowDef(workflow.getWorkflowName()))
                .thenReturn(Optional.of(workflowDef));
        workflowExecutor.restart(workflow.getWorkflowId(), true);
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(0, workflow.getEndTime());
        assertEquals(0, workflow.getLastRetriedTime());
        verify(metadataDAO, times(1)).getLatestWorkflowDef(anyString());

        argumentCaptor = ArgumentCaptor.forClass(WorkflowModel.class);
        verify(executionDAOFacade, times(2)).createWorkflow(argumentCaptor.capture());
        assertEquals(
                workflow.getWorkflowId(), argumentCaptor.getAllValues().get(1).getWorkflowId());
        assertEquals(workflowDef, argumentCaptor.getAllValues().get(1).getWorkflowDefinition());
    }

    @Test(expected = NotFoundException.class)
    public void testRetryNonTerminalWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryNonTerminalWorkflow");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.retry(workflow.getWorkflowId(), false);
    }

    @Test(expected = ConflictException.class)
    public void testRetryWorkflowNoTasks() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("ApplicationException");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setTasks(Collections.emptyList());
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.retry(workflow.getWorkflowId(), false);
    }

    @Test(expected = ConflictException.class)
    public void testRetryWorkflowNoFailedTasks() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        // add 2 failed task in 2 forks and 1 cancelled in the 3rd fork
        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(1);
        task_1_1.setRetryCount(0);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setTaskDefName("task1");
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_1_2 = new TaskModel();
        task_1_2.setTaskId(UUID.randomUUID().toString());
        task_1_2.setSeq(2);
        task_1_2.setRetryCount(1);
        task_1_2.setTaskType(TaskType.SIMPLE.toString());
        task_1_2.setStatus(TaskModel.Status.COMPLETED);
        task_1_2.setTaskDefName("task1");
        task_1_2.setReferenceTaskName("task1_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_1_2));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);
    }

    @Test
    public void testRetryWorkflow() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTasksCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        AtomicInteger updateTaskCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTaskCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTask(any());

        // add 2 failed task in 2 forks and 1 cancelled in the 3rd fork
        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setRetryCount(1);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.CANCELED);
        task_1_1.setRetried(true);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_1_2 = new TaskModel();
        task_1_2.setTaskId(UUID.randomUUID().toString());
        task_1_2.setSeq(21);
        task_1_2.setRetryCount(1);
        task_1_2.setTaskType(TaskType.SIMPLE.toString());
        task_1_2.setStatus(TaskModel.Status.FAILED);
        task_1_2.setTaskDefName("task1");
        task_1_2.setWorkflowTask(new WorkflowTask());
        task_1_2.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setRetryCount(1);
        task_2_1.setStatus(TaskModel.Status.FAILED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        TaskModel task_3_1 = new TaskModel();
        task_3_1.setTaskId(UUID.randomUUID().toString());
        task_3_1.setSeq(23);
        task_3_1.setRetryCount(1);
        task_3_1.setStatus(TaskModel.Status.CANCELED);
        task_3_1.setTaskType(TaskType.SIMPLE.toString());
        task_3_1.setTaskDefName("task3");
        task_3_1.setWorkflowTask(new WorkflowTask());
        task_3_1.setReferenceTaskName("task3_ref1");

        TaskModel task_4_1 = new TaskModel();
        task_4_1.setTaskId(UUID.randomUUID().toString());
        task_4_1.setSeq(122);
        task_4_1.setRetryCount(1);
        task_4_1.setStatus(TaskModel.Status.FAILED);
        task_4_1.setTaskType(TaskType.SIMPLE.toString());
        task_4_1.setTaskDefName("task1");
        task_4_1.setWorkflowTask(new WorkflowTask());
        task_4_1.setReferenceTaskName("task4_refABC");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_1_2, task_2_1, task_3_1, task_4_1));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        // then:
        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, updateTasksCalledCounter.get());
        assertEquals(0, updateTaskCalledCounter.get());
    }

    @Test
    public void testRetryWorkflowReturnsNoDuplicates() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(10);
        task_1_1.setRetryCount(0);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_1_2 = new TaskModel();
        task_1_2.setTaskId(UUID.randomUUID().toString());
        task_1_2.setSeq(11);
        task_1_2.setRetryCount(1);
        task_1_2.setTaskType(TaskType.SIMPLE.toString());
        task_1_2.setStatus(TaskModel.Status.COMPLETED);
        task_1_2.setTaskDefName("task1");
        task_1_2.setWorkflowTask(new WorkflowTask());
        task_1_2.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(21);
        task_2_1.setRetryCount(0);
        task_2_1.setStatus(TaskModel.Status.CANCELED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        TaskModel task_3_1 = new TaskModel();
        task_3_1.setTaskId(UUID.randomUUID().toString());
        task_3_1.setSeq(31);
        task_3_1.setRetryCount(1);
        task_3_1.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        task_3_1.setTaskType(TaskType.SIMPLE.toString());
        task_3_1.setTaskDefName("task1");
        task_3_1.setWorkflowTask(new WorkflowTask());
        task_3_1.setReferenceTaskName("task3_ref1");

        TaskModel task_4_1 = new TaskModel();
        task_4_1.setTaskId(UUID.randomUUID().toString());
        task_4_1.setSeq(41);
        task_4_1.setRetryCount(0);
        task_4_1.setStatus(TaskModel.Status.TIMED_OUT);
        task_4_1.setTaskType(TaskType.SIMPLE.toString());
        task_4_1.setTaskDefName("task1");
        task_4_1.setWorkflowTask(new WorkflowTask());
        task_4_1.setReferenceTaskName("task4_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_1_2, task_2_1, task_3_1, task_4_1));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(8, workflow.getTasks().size());
    }

    @Test
    public void testRetryWorkflowMultipleRetries() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(10);
        task_1_1.setRetryCount(0);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(20);
        task_2_1.setRetryCount(0);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setStatus(TaskModel.Status.CANCELED);
        task_2_1.setTaskDefName("task1");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_2_1));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(4, workflow.getTasks().size());

        // Reset Last Workflow Task to FAILED.
        TaskModel lastTask =
                workflow.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals("task1_ref1"))
                        .collect(
                                groupingBy(
                                        TaskModel::getReferenceTaskName,
                                        maxBy(comparingInt(TaskModel::getSeq))))
                        .values()
                        .stream()
                        .map(Optional::get)
                        .collect(Collectors.toList())
                        .get(0);
        lastTask.setStatus(TaskModel.Status.FAILED);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(5, workflow.getTasks().size());

        // Reset Last Workflow Task to FAILED.
        // Reset Last Workflow Task to FAILED.
        TaskModel lastTask2 =
                workflow.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals("task1_ref1"))
                        .collect(
                                groupingBy(
                                        TaskModel::getReferenceTaskName,
                                        maxBy(comparingInt(TaskModel::getSeq))))
                        .values()
                        .stream()
                        .map(Optional::get)
                        .collect(Collectors.toList())
                        .get(0);
        lastTask2.setStatus(TaskModel.Status.FAILED);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(6, workflow.getTasks().size());
    }

    @Test
    public void testRetryWorkflowWithJoinTask() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        TaskModel forkTask = new TaskModel();
        forkTask.setTaskType(TaskType.FORK_JOIN.toString());
        forkTask.setTaskId(UUID.randomUUID().toString());
        forkTask.setSeq(1);
        forkTask.setRetryCount(1);
        forkTask.setStatus(TaskModel.Status.COMPLETED);
        forkTask.setReferenceTaskName("task_fork");

        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setRetryCount(1);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setRetryCount(1);
        task_2_1.setStatus(TaskModel.Status.CANCELED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        TaskModel joinTask = new TaskModel();
        joinTask.setTaskType(TaskType.JOIN.toString());
        joinTask.setTaskId(UUID.randomUUID().toString());
        joinTask.setSeq(25);
        joinTask.setRetryCount(1);
        joinTask.setStatus(TaskModel.Status.CANCELED);
        joinTask.setReferenceTaskName("task_join");
        joinTask.getInputData()
                .put(
                        "joinOn",
                        Arrays.asList(
                                task_1_1.getReferenceTaskName(), task_2_1.getReferenceTaskName()));

        workflow.getTasks().addAll(Arrays.asList(forkTask, task_1_1, task_2_1, joinTask));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(6, workflow.getTasks().size());
        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void testRetryFromLastFailedSubWorkflowTaskThenStartWithLastFailedTask() {
        IDGenerator idGenerator = new IDGenerator();
        // given
        String id = idGenerator.generate();
        String workflowInstanceId = idGenerator.generate();
        TaskModel task = new TaskModel();
        task.setTaskType(TaskType.SIMPLE.name());
        task.setTaskDefName("task");
        task.setReferenceTaskName("task_ref");
        task.setWorkflowInstanceId(workflowInstanceId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setRetryCount(0);
        task.setWorkflowTask(new WorkflowTask());
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(id);
        task.setSeq(1);

        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(workflowInstanceId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.FAILED);
        task1.setRetryCount(0);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());
        task1.setSubWorkflowId(id);
        task1.setSeq(2);

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setWorkflowId(id);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("subworkflow");
        workflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(workflowDef);
        subWorkflow.setStatus(WorkflowModel.Status.FAILED);
        subWorkflow.getTasks().addAll(Arrays.asList(task, task1));
        subWorkflow.setParentWorkflowId("testRunWorkflowId");

        TaskModel task2 = new TaskModel();
        task2.setWorkflowInstanceId(subWorkflow.getWorkflowId());
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.FAILED);
        task2.setRetryCount(0);
        task2.setOutputData(new HashMap<>());
        task2.setSubWorkflowId(id);
        task2.setTaskType(TaskType.SUB_WORKFLOW.name());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setTasks(Collections.singletonList(task2));
        workflowDef = new WorkflowDef();
        workflowDef.setName("first_workflow");
        workflow.setWorkflowDefinition(workflowDef);

        // when
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(workflowDef));
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task1);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        workflowExecutor.retry(workflow.getWorkflowId(), true);

        // then
        assertEquals(task.getStatus(), TaskModel.Status.COMPLETED);
        assertEquals(task1.getStatus(), TaskModel.Status.IN_PROGRESS);
        assertEquals(workflow.getPreviousStatus(), WorkflowModel.Status.FAILED);
        assertEquals(workflow.getStatus(), WorkflowModel.Status.RUNNING);
        assertEquals(subWorkflow.getPreviousStatus(), WorkflowModel.Status.FAILED);
        assertEquals(subWorkflow.getStatus(), WorkflowModel.Status.RUNNING);
    }

    @Test
    public void testRetryTimedOutWorkflowWithoutFailedTasks() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.TIMED_OUT);

        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setRetryCount(1);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.COMPLETED);
        task_1_1.setRetried(true);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setRetryCount(1);
        task_2_1.setStatus(TaskModel.Status.COMPLETED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_2_1));

        AtomicInteger updateWorkflowCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateWorkflowCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());

        AtomicInteger updateTasksCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            updateTasksCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());
        // end of setup

        // when
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        // then
        assertEquals(WorkflowModel.Status.TIMED_OUT, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertTrue(workflow.getLastRetriedTime() > 0);
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, updateTasksCalledCounter.get());
    }

    @Test(expected = ConflictException.class)
    public void testRerunNonTerminalWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryNonTerminalWorkflow");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflow.getWorkflowId());
        workflowExecutor.rerun(rerunWorkflowRequest);
    }

    @Test
    public void testRerunWorkflow() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRerunWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRerunWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForIncompletion("task1 failed");
        workflow.setFailedReferenceTaskNames(
                new HashSet<>() {
                    {
                        add("task1_ref1");
                    }
                });
        workflow.setFailedTaskNames(
                new HashSet<>() {
                    {
                        add("task1");
                    }
                });

        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setRetryCount(1);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setRetried(true);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setRetryCount(1);
        task_2_1.setStatus(TaskModel.Status.CANCELED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_2_1));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflow.getWorkflowId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertNull(workflow.getReasonForIncompletion());
        assertEquals(new HashSet<>(), workflow.getFailedReferenceTaskNames());
        assertEquals(new HashSet<>(), workflow.getFailedTaskNames());
    }

    @Test
    public void testRerunSubWorkflow() {
        IDGenerator idGenerator = new IDGenerator();
        // setup
        String parentWorkflowId = idGenerator.generate();
        String subWorkflowId = idGenerator.generate();

        // sub workflow setup
        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(subWorkflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.SIMPLE.name());
        task2.setTaskDefName("task2");
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(subWorkflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.COMPLETED);
        task2.setWorkflowTask(new WorkflowTask());
        task2.setOutputData(new HashMap<>());

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setParentWorkflowId(parentWorkflowId);
        subWorkflow.setWorkflowId(subWorkflowId);
        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName("subworkflow");
        subworkflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(subworkflowDef);
        subWorkflow.setOwnerApp("junit_testRerunWorkflowId");
        subWorkflow.setStatus(WorkflowModel.Status.COMPLETED);
        subWorkflow.getTasks().addAll(Arrays.asList(task1, task2));

        // parent workflow setup
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(parentWorkflowId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(subWorkflowId);
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        task.setWorkflowTask(new WorkflowTask());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(parentWorkflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentworkflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.getTasks().addAll(Arrays.asList(task));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subWorkflow.getWorkflowId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // then:
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, subWorkflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, subWorkflow.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void testRerunWorkflowWithTaskId() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRerunWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForIncompletion("task1 failed");
        workflow.setFailedReferenceTaskNames(
                new HashSet<>() {
                    {
                        add("task1_ref1");
                    }
                });
        workflow.setFailedTaskNames(
                new HashSet<>() {
                    {
                        add("task1");
                    }
                });
        TaskModel task_1_1 = new TaskModel();
        task_1_1.setTaskId(UUID.randomUUID().toString());
        task_1_1.setSeq(20);
        task_1_1.setRetryCount(1);
        task_1_1.setTaskType(TaskType.SIMPLE.toString());
        task_1_1.setStatus(TaskModel.Status.FAILED);
        task_1_1.setRetried(true);
        task_1_1.setTaskDefName("task1");
        task_1_1.setWorkflowTask(new WorkflowTask());
        task_1_1.setReferenceTaskName("task1_ref1");

        TaskModel task_2_1 = new TaskModel();
        task_2_1.setTaskId(UUID.randomUUID().toString());
        task_2_1.setSeq(22);
        task_2_1.setRetryCount(1);
        task_2_1.setStatus(TaskModel.Status.CANCELED);
        task_2_1.setTaskType(TaskType.SIMPLE.toString());
        task_2_1.setTaskDefName("task2");
        task_2_1.setWorkflowTask(new WorkflowTask());
        task_2_1.setReferenceTaskName("task2_ref1");

        workflow.getTasks().addAll(Arrays.asList(task_1_1, task_2_1));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflow.getWorkflowId());
        rerunWorkflowRequest.setReRunFromTaskId(task_1_1.getTaskId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // when:
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertNull(workflow.getReasonForIncompletion());
        assertEquals(new HashSet<>(), workflow.getFailedReferenceTaskNames());
        assertEquals(new HashSet<>(), workflow.getFailedTaskNames());
    }

    @Test
    public void testRerunWorkflowWithSyncSystemTaskId() {
        IDGenerator idGenerator = new IDGenerator();
        // setup
        String workflowId = idGenerator.generate();

        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(workflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.JSON_JQ_TRANSFORM.name());
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(workflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId("system-task-id");
        task2.setStatus(TaskModel.Status.FAILED);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("workflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForIncompletion("task2 failed");
        workflow.setFailedReferenceTaskNames(
                new HashSet<>() {
                    {
                        add("task2_ref");
                    }
                });
        workflow.setFailedTaskNames(
                new HashSet<>() {
                    {
                        add("task2");
                    }
                });
        workflow.getTasks().addAll(Arrays.asList(task1, task2));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflow.getWorkflowId());
        rerunWorkflowRequest.setReRunFromTaskId(task2.getTaskId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // then:
        assertEquals(TaskModel.Status.COMPLETED, task2.getStatus());
        assertEquals(WorkflowModel.Status.FAILED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertNull(workflow.getReasonForIncompletion());
        assertEquals(new HashSet<>(), workflow.getFailedReferenceTaskNames());
        assertEquals(new HashSet<>(), workflow.getFailedTaskNames());
    }

    @Test
    public void testRerunSubWorkflowWithTaskId() {
        IDGenerator idGenerator = new IDGenerator();

        // setup
        String parentWorkflowId = idGenerator.generate();
        String subWorkflowId = idGenerator.generate();

        // sub workflow setup
        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(subWorkflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.SIMPLE.name());
        task2.setTaskDefName("task2");
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(subWorkflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.COMPLETED);
        task2.setWorkflowTask(new WorkflowTask());
        task2.setOutputData(new HashMap<>());

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setParentWorkflowId(parentWorkflowId);
        subWorkflow.setWorkflowId(subWorkflowId);
        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName("subworkflow");
        subworkflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(subworkflowDef);
        subWorkflow.setOwnerApp("junit_testRerunWorkflowId");
        subWorkflow.setStatus(WorkflowModel.Status.COMPLETED);
        subWorkflow.getTasks().addAll(Arrays.asList(task1, task2));

        // parent workflow setup
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(parentWorkflowId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(subWorkflowId);
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        task.setWorkflowTask(new WorkflowTask());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(parentWorkflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentworkflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.getTasks().addAll(Arrays.asList(task));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subWorkflow.getWorkflowId());
        rerunWorkflowRequest.setReRunFromTaskId(task2.getTaskId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // then:
        assertEquals(TaskModel.Status.SCHEDULED, task2.getStatus());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, subWorkflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, subWorkflow.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void testGetActiveDomain() throws Exception {
        String taskType = "test-task";
        String[] domains = new String[] {"domain1", "domain2"};

        PollData pollData1 =
                new PollData(
                        "queue1", domains[0], "worker1", System.currentTimeMillis() - 99 * 1000);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[0]))
                .thenReturn(pollData1);
        String activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertEquals(domains[0], activeDomain);
        Thread.sleep(2000L);

        PollData pollData2 =
                new PollData(
                        "queue2", domains[1], "worker2", System.currentTimeMillis() - 99 * 1000);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[1]))
                .thenReturn(pollData2);
        activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertEquals(domains[1], activeDomain);

        Thread.sleep(2000L);
        activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertEquals(domains[1], activeDomain);

        domains = new String[] {""};
        when(executionDAOFacade.getTaskPollDataByDomain(any(), any())).thenReturn(new PollData());
        activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertNotNull(activeDomain);
        assertEquals("", activeDomain);

        domains = new String[] {};
        activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertNull(activeDomain);

        activeDomain = workflowExecutor.getActiveDomain(taskType, null);
        assertNull(activeDomain);

        domains = new String[] {"test-domain"};
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), anyString())).thenReturn(null);
        activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertNotNull(activeDomain);
        assertEquals("test-domain", activeDomain);
    }

    @Test
    public void testInactiveDomains() {
        String taskType = "test-task";
        String[] domains = new String[] {"domain1", "domain2"};

        PollData pollData1 =
                new PollData(
                        "queue1", domains[0], "worker1", System.currentTimeMillis() - 99 * 10000);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[0]))
                .thenReturn(pollData1);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[1])).thenReturn(null);
        String activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertEquals("domain2", activeDomain);
    }

    @Test
    public void testDefaultDomain() {
        String taskType = "test-task";
        String[] domains = new String[] {"domain1", "domain2", "NO_DOMAIN"};

        PollData pollData1 =
                new PollData(
                        "queue1", domains[0], "worker1", System.currentTimeMillis() - 99 * 10000);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[0]))
                .thenReturn(pollData1);
        when(executionDAOFacade.getTaskPollDataByDomain(taskType, domains[1])).thenReturn(null);
        String activeDomain = workflowExecutor.getActiveDomain(taskType, domains);
        assertNull(activeDomain);
    }

    @Test
    public void testTaskToDomain() {
        WorkflowModel workflow = generateSampleWorkflow();
        List<TaskModel> tasks = generateSampleTasks(3);

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "mydomain");
        workflow.setTaskToDomain(taskToDomain);

        PollData pollData1 =
                new PollData(
                        "queue1", "mydomain", "worker1", System.currentTimeMillis() - 99 * 100);
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), anyString()))
                .thenReturn(pollData1);
        workflowExecutor.setTaskDomains(tasks, workflow);

        assertNotNull(tasks);
        tasks.forEach(task -> assertEquals("mydomain", task.getDomain()));
    }

    @Test
    public void testTaskToDomainsPerTask() {
        WorkflowModel workflow = generateSampleWorkflow();
        List<TaskModel> tasks = generateSampleTasks(2);

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "mydomain, NO_DOMAIN");
        workflow.setTaskToDomain(taskToDomain);

        PollData pollData1 =
                new PollData(
                        "queue1", "mydomain", "worker1", System.currentTimeMillis() - 99 * 100);
        when(executionDAOFacade.getTaskPollDataByDomain(eq("task1"), anyString()))
                .thenReturn(pollData1);
        when(executionDAOFacade.getTaskPollDataByDomain(eq("task2"), anyString())).thenReturn(null);
        workflowExecutor.setTaskDomains(tasks, workflow);

        assertEquals("mydomain", tasks.get(0).getDomain());
        assertNull(tasks.get(1).getDomain());
    }

    @Test
    public void testTaskToDomainOverrides() {
        WorkflowModel workflow = generateSampleWorkflow();
        List<TaskModel> tasks = generateSampleTasks(4);

        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "mydomain");
        taskToDomain.put("task2", "someInactiveDomain, NO_DOMAIN");
        taskToDomain.put("task3", "someActiveDomain, NO_DOMAIN");
        taskToDomain.put("task4", "someInactiveDomain, someInactiveDomain2");
        workflow.setTaskToDomain(taskToDomain);

        PollData pollData1 =
                new PollData(
                        "queue1", "mydomain", "worker1", System.currentTimeMillis() - 99 * 100);
        PollData pollData2 =
                new PollData(
                        "queue2",
                        "someActiveDomain",
                        "worker2",
                        System.currentTimeMillis() - 99 * 100);
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), eq("mydomain")))
                .thenReturn(pollData1);
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), eq("someInactiveDomain")))
                .thenReturn(null);
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), eq("someActiveDomain")))
                .thenReturn(pollData2);
        when(executionDAOFacade.getTaskPollDataByDomain(anyString(), eq("someInactiveDomain")))
                .thenReturn(null);
        workflowExecutor.setTaskDomains(tasks, workflow);

        assertEquals("mydomain", tasks.get(0).getDomain());
        assertNull(tasks.get(1).getDomain());
        assertEquals("someActiveDomain", tasks.get(2).getDomain());
        assertEquals("someInactiveDomain2", tasks.get(3).getDomain());
    }

    @Test
    public void testDedupAndAddTasks() {
        WorkflowModel workflow = new WorkflowModel();

        TaskModel task1 = new TaskModel();
        task1.setReferenceTaskName("task1");
        task1.setRetryCount(1);

        TaskModel task2 = new TaskModel();
        task2.setReferenceTaskName("task2");
        task2.setRetryCount(2);

        List<TaskModel> tasks = new ArrayList<>(Arrays.asList(task1, task2));

        List<TaskModel> taskList = workflowExecutor.dedupAndAddTasks(workflow, tasks);
        assertEquals(2, taskList.size());
        assertEquals(tasks, taskList);
        assertEquals(workflow.getTasks(), taskList);

        // Adding the same tasks again
        taskList = workflowExecutor.dedupAndAddTasks(workflow, tasks);
        assertEquals(0, taskList.size());
        assertEquals(workflow.getTasks(), tasks);

        // Adding 2 new tasks
        TaskModel newTask = new TaskModel();
        newTask.setReferenceTaskName("newTask");
        newTask.setRetryCount(0);

        taskList = workflowExecutor.dedupAndAddTasks(workflow, Collections.singletonList(newTask));
        assertEquals(1, taskList.size());
        assertEquals(newTask, taskList.get(0));
        assertEquals(3, workflow.getTasks().size());
    }

    @Test(expected = ConflictException.class)
    public void testTerminateCompletedWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testTerminateTerminalWorkflow");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.terminateWorkflow(
                workflow.getWorkflowId(), "test terminating terminal workflow");
    }

    @Test
    public void testResetCallbacksForWorkflowTasks() {
        String workflowId = "test-workflow-id";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel completedTask = new TaskModel();
        completedTask.setTaskType(TaskType.SIMPLE.name());
        completedTask.setReferenceTaskName("completedTask");
        completedTask.setWorkflowInstanceId(workflowId);
        completedTask.setScheduledTime(System.currentTimeMillis());
        completedTask.setCallbackAfterSeconds(300);
        completedTask.setTaskId("simple-task-id");
        completedTask.setStatus(TaskModel.Status.COMPLETED);

        TaskModel systemTask = new TaskModel();
        systemTask.setTaskType(TaskType.WAIT.name());
        systemTask.setReferenceTaskName("waitTask");
        systemTask.setWorkflowInstanceId(workflowId);
        systemTask.setScheduledTime(System.currentTimeMillis());
        systemTask.setTaskId("system-task-id");
        systemTask.setStatus(TaskModel.Status.SCHEDULED);

        TaskModel simpleTask = new TaskModel();
        simpleTask.setTaskType(TaskType.SIMPLE.name());
        simpleTask.setReferenceTaskName("simpleTask");
        simpleTask.setWorkflowInstanceId(workflowId);
        simpleTask.setScheduledTime(System.currentTimeMillis());
        simpleTask.setCallbackAfterSeconds(300);
        simpleTask.setTaskId("simple-task-id");
        simpleTask.setStatus(TaskModel.Status.SCHEDULED);

        TaskModel noCallbackTask = new TaskModel();
        noCallbackTask.setTaskType(TaskType.SIMPLE.name());
        noCallbackTask.setReferenceTaskName("noCallbackTask");
        noCallbackTask.setWorkflowInstanceId(workflowId);
        noCallbackTask.setScheduledTime(System.currentTimeMillis());
        noCallbackTask.setCallbackAfterSeconds(0);
        noCallbackTask.setTaskId("no-callback-task-id");
        noCallbackTask.setStatus(TaskModel.Status.SCHEDULED);

        workflow.getTasks()
                .addAll(Arrays.asList(completedTask, systemTask, simpleTask, noCallbackTask));
        when(executionDAOFacade.getWorkflowModel(workflowId, true)).thenReturn(workflow);

        workflowExecutor.resetCallbacksForWorkflow(workflowId);
        verify(queueDAO, times(1)).resetOffsetTime(anyString(), anyString());
    }

    @Test
    public void testUpdateParentWorkflowTask() {
        String parentWorkflowTaskId = "parent_workflow_task_id";
        String workflowId = "workflow_id";

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setWorkflowId(workflowId);
        subWorkflow.setParentWorkflowTaskId(parentWorkflowTaskId);
        subWorkflow.setStatus(WorkflowModel.Status.COMPLETED);

        TaskModel subWorkflowTask = new TaskModel();
        subWorkflowTask.setSubWorkflowId(workflowId);
        subWorkflowTask.setStatus(TaskModel.Status.IN_PROGRESS);
        subWorkflowTask.setExternalOutputPayloadStoragePath(null);

        when(executionDAOFacade.getTaskModel(parentWorkflowTaskId)).thenReturn(subWorkflowTask);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(subWorkflow);

        workflowExecutor.updateParentWorkflowTask(subWorkflow);
        ArgumentCaptor<TaskModel> argumentCaptor = ArgumentCaptor.forClass(TaskModel.class);
        verify(executionDAOFacade, times(1)).updateTask(argumentCaptor.capture());
        assertEquals(TaskModel.Status.COMPLETED, argumentCaptor.getAllValues().get(0).getStatus());
        assertEquals(workflowId, argumentCaptor.getAllValues().get(0).getSubWorkflowId());
    }

    @Test
    public void testScheduleNextIteration() {
        WorkflowModel workflow = generateSampleWorkflow();
        workflow.setTaskToDomain(
                new HashMap<>() {
                    {
                        put("TEST", "domain1");
                    }
                });
        TaskModel loopTask = mock(TaskModel.class);
        WorkflowTask loopWfTask = mock(WorkflowTask.class);
        when(loopTask.getWorkflowTask()).thenReturn(loopWfTask);
        List<WorkflowTask> loopOver =
                new ArrayList<>() {
                    {
                        WorkflowTask workflowTask = new WorkflowTask();
                        workflowTask.setType(TaskType.TASK_TYPE_SIMPLE);
                        workflowTask.setName("TEST");
                        workflowTask.setTaskDefinition(new TaskDef());
                        add(workflowTask);
                    }
                };
        when(loopWfTask.getLoopOver()).thenReturn(loopOver);

        workflowExecutor.scheduleNextIteration(loopTask, workflow);
        verify(executionDAOFacade).getTaskPollDataByDomain("TEST", "domain1");
    }

    @Test
    public void testCancelNonTerminalTasks() {
        WorkflowDef def = new WorkflowDef();
        def.setWorkflowStatusListenerEnabled(true);

        WorkflowModel workflow = generateSampleWorkflow();
        workflow.setWorkflowDefinition(def);

        TaskModel subWorkflowTask = new TaskModel();
        subWorkflowTask.setTaskId(UUID.randomUUID().toString());
        subWorkflowTask.setTaskType(TaskType.SUB_WORKFLOW.name());
        subWorkflowTask.setStatus(TaskModel.Status.IN_PROGRESS);

        TaskModel lambdaTask = new TaskModel();
        lambdaTask.setTaskId(UUID.randomUUID().toString());
        lambdaTask.setTaskType(TaskType.LAMBDA.name());
        lambdaTask.setStatus(TaskModel.Status.SCHEDULED);

        TaskModel simpleTask = new TaskModel();
        simpleTask.setTaskId(UUID.randomUUID().toString());
        simpleTask.setTaskType(TaskType.SIMPLE.name());
        simpleTask.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().addAll(Arrays.asList(subWorkflowTask, lambdaTask, simpleTask));

        List<String> erroredTasks = workflowExecutor.cancelNonTerminalTasks(workflow);
        assertTrue(erroredTasks.isEmpty());
        ArgumentCaptor<TaskModel> argumentCaptor = ArgumentCaptor.forClass(TaskModel.class);
        verify(executionDAOFacade, times(2)).updateTask(argumentCaptor.capture());
        assertEquals(2, argumentCaptor.getAllValues().size());
        assertEquals(
                TaskType.SUB_WORKFLOW.name(), argumentCaptor.getAllValues().get(0).getTaskType());
        assertEquals(TaskModel.Status.CANCELED, argumentCaptor.getAllValues().get(0).getStatus());
        assertEquals(TaskType.LAMBDA.name(), argumentCaptor.getAllValues().get(1).getTaskType());
        assertEquals(TaskModel.Status.CANCELED, argumentCaptor.getAllValues().get(1).getStatus());
        verify(workflowStatusListener, times(1))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    public void testPauseWorkflow() {
        when(executionLockService.acquireLock(anyString(), anyLong())).thenReturn(true);
        doNothing().when(executionLockService).releaseLock(anyString());

        String workflowId = "testPauseWorkflowId";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);

        // if workflow is in terminal state
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        try {
            workflowExecutor.pauseWorkflow(workflowId);
            fail("Expected " + ConflictException.class);
        } catch (ConflictException e) {
            verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
            verify(queueDAO, never()).remove(anyString(), anyString());
        }

        // if workflow is already PAUSED
        workflow.setStatus(WorkflowModel.Status.PAUSED);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        workflowExecutor.pauseWorkflow(workflowId);
        assertEquals(WorkflowModel.Status.PAUSED, workflow.getStatus());
        verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
        verify(queueDAO, never()).remove(anyString(), anyString());

        // if workflow is RUNNING
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        workflowExecutor.pauseWorkflow(workflowId);
        assertEquals(WorkflowModel.Status.PAUSED, workflow.getStatus());
        verify(executionDAOFacade, times(1)).updateWorkflow(any(WorkflowModel.class));
        verify(queueDAO, times(1)).remove(anyString(), anyString());
    }

    @Test
    public void testResumeWorkflow() {
        String workflowId = "testResumeWorkflowId";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);

        // if workflow is not in PAUSED state
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        try {
            workflowExecutor.resumeWorkflow(workflowId);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
            verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
            verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
        }

        // if workflow is in PAUSED state
        workflow.setStatus(WorkflowModel.Status.PAUSED);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        workflowExecutor.resumeWorkflow(workflowId);
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertTrue(workflow.getLastRetriedTime() > 0);
        verify(executionDAOFacade, times(1)).updateWorkflow(any(WorkflowModel.class));
        verify(queueDAO, times(1)).push(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTerminateWorkflowWithFailureWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("workflow");
        workflowDef.setFailureWorkflow("failure_workflow");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("1");
        workflow.setCorrelationId("testid");
        workflow.setWorkflowDefinition(new WorkflowDef());
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setOwnerApp("junit_test");
        workflow.setEndTime(100L);
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setWorkflowDefinition(workflowDef);

        TaskModel successTask = new TaskModel();
        successTask.setTaskId("taskid1");
        successTask.setReferenceTaskName("success");
        successTask.setStatus(TaskModel.Status.COMPLETED);

        TaskModel failedTask = new TaskModel();
        failedTask.setTaskId("taskid2");
        failedTask.setReferenceTaskName("failed");
        failedTask.setStatus(TaskModel.Status.FAILED);
        workflow.getTasks().addAll(Arrays.asList(successTask, failedTask));

        WorkflowDef failureWorkflowDef = new WorkflowDef();
        failureWorkflowDef.setName("failure_workflow");
        when(metadataDAO.getLatestWorkflowDef(failureWorkflowDef.getName()))
                .thenReturn(Optional.of(failureWorkflowDef));

        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);

        workflowExecutor.decide(workflow.getWorkflowId());

        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
        ArgumentCaptor<WorkflowCreationEvent> argumentCaptor =
                ArgumentCaptor.forClass(WorkflowCreationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(argumentCaptor.capture());
        StartWorkflowInput startWorkflowInput = argumentCaptor.getValue().getStartWorkflowInput();
        assertEquals(workflow.getCorrelationId(), startWorkflowInput.getCorrelationId());
        assertEquals(
                workflow.getWorkflowId(), startWorkflowInput.getWorkflowInput().get("workflowId"));
        assertEquals(
                failedTask.getTaskId(), startWorkflowInput.getWorkflowInput().get("failureTaskId"));
        assertNotNull(
                failedTask.getTaskId(),
                startWorkflowInput.getWorkflowInput().get("failedWorkflow"));
    }

    @Test
    public void testRerunOptionalSubWorkflow() {
        IDGenerator idGenerator = new IDGenerator();
        // setup
        String parentWorkflowId = idGenerator.generate();
        String subWorkflowId = idGenerator.generate();

        // sub workflow setup
        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(subWorkflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.SIMPLE.name());
        task2.setTaskDefName("task2");
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(subWorkflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.FAILED);
        task2.setWorkflowTask(new WorkflowTask());
        task2.setOutputData(new HashMap<>());

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setParentWorkflowId(parentWorkflowId);
        subWorkflow.setWorkflowId(subWorkflowId);
        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName("subworkflow");
        subworkflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(subworkflowDef);
        subWorkflow.setOwnerApp("junit_testRerunWorkflowId");
        subWorkflow.setStatus(WorkflowModel.Status.FAILED);
        subWorkflow.getTasks().addAll(Arrays.asList(task1, task2));

        // parent workflow setup
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(parentWorkflowId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(subWorkflowId);
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(true);
        task.setWorkflowTask(workflowTask);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(parentWorkflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentworkflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.getTasks().addAll(Arrays.asList(task));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(subWorkflow.getWorkflowId());
        workflowExecutor.rerun(rerunWorkflowRequest);

        // then: parent workflow remains the same
        assertEquals(WorkflowModel.Status.FAILED, subWorkflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, subWorkflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, task.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testRestartOptionalSubWorkflow() {
        IDGenerator idGenerator = new IDGenerator();
        // setup
        String parentWorkflowId = idGenerator.generate();
        String subWorkflowId = idGenerator.generate();

        // sub workflow setup
        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(subWorkflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.SIMPLE.name());
        task2.setTaskDefName("task2");
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(subWorkflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.FAILED);
        task2.setWorkflowTask(new WorkflowTask());
        task2.setOutputData(new HashMap<>());

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setParentWorkflowId(parentWorkflowId);
        subWorkflow.setWorkflowId(subWorkflowId);
        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName("subworkflow");
        subworkflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(subworkflowDef);
        subWorkflow.setOwnerApp("junit_testRerunWorkflowId");
        subWorkflow.setStatus(WorkflowModel.Status.FAILED);
        subWorkflow.getTasks().addAll(Arrays.asList(task1, task2));

        // parent workflow setup
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(parentWorkflowId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(subWorkflowId);
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(true);
        task.setWorkflowTask(workflowTask);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(parentWorkflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentworkflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.getTasks().addAll(Arrays.asList(task));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        workflowExecutor.restart(subWorkflowId, false);

        // then: parent workflow remains the same
        assertEquals(WorkflowModel.Status.FAILED, subWorkflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, subWorkflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, task.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testRetryOptionalSubWorkflow() {
        IDGenerator idGenerator = new IDGenerator();
        // setup
        String parentWorkflowId = idGenerator.generate();
        String subWorkflowId = idGenerator.generate();

        // sub workflow setup
        TaskModel task1 = new TaskModel();
        task1.setTaskType(TaskType.SIMPLE.name());
        task1.setTaskDefName("task1");
        task1.setReferenceTaskName("task1_ref");
        task1.setWorkflowInstanceId(subWorkflowId);
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(idGenerator.generate());
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setWorkflowTask(new WorkflowTask());
        task1.setOutputData(new HashMap<>());

        TaskModel task2 = new TaskModel();
        task2.setTaskType(TaskType.SIMPLE.name());
        task2.setTaskDefName("task2");
        task2.setReferenceTaskName("task2_ref");
        task2.setWorkflowInstanceId(subWorkflowId);
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setTaskId(idGenerator.generate());
        task2.setStatus(TaskModel.Status.FAILED);
        task2.setWorkflowTask(new WorkflowTask());
        task2.setOutputData(new HashMap<>());

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setParentWorkflowId(parentWorkflowId);
        subWorkflow.setWorkflowId(subWorkflowId);
        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName("subworkflow");
        subworkflowDef.setVersion(1);
        subWorkflow.setWorkflowDefinition(subworkflowDef);
        subWorkflow.setOwnerApp("junit_testRerunWorkflowId");
        subWorkflow.setStatus(WorkflowModel.Status.FAILED);
        subWorkflow.getTasks().addAll(Arrays.asList(task1, task2));

        // parent workflow setup
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(parentWorkflowId);
        task.setScheduledTime(System.currentTimeMillis());
        task.setTaskId(idGenerator.generate());
        task.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
        task.setOutputData(new HashMap<>());
        task.setSubWorkflowId(subWorkflowId);
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setOptional(true);
        task.setWorkflowTask(workflowTask);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(parentWorkflowId);
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentworkflow");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRerunWorkflowId");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.getTasks().addAll(Arrays.asList(task));
        // end of setup

        // when:
        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionDAOFacade.getWorkflowModel(task.getSubWorkflowId(), true))
                .thenReturn(subWorkflow);
        when(executionDAOFacade.getTaskModel(subWorkflow.getParentWorkflowTaskId()))
                .thenReturn(task);
        when(executionDAOFacade.getWorkflowModel(subWorkflow.getParentWorkflowId(), false))
                .thenReturn(workflow);

        workflowExecutor.retry(subWorkflowId, true);

        // then: parent workflow remains the same
        assertEquals(WorkflowModel.Status.FAILED, subWorkflow.getPreviousStatus());
        assertEquals(WorkflowModel.Status.RUNNING, subWorkflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, task.getStatus());
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testUpdateTaskWithCallbackAfterSeconds() {
        String workflowId = "test-workflow-id";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowDefinition(new WorkflowDef());

        TaskModel simpleTask = new TaskModel();
        simpleTask.setTaskType(TaskType.SIMPLE.name());
        simpleTask.setReferenceTaskName("simpleTask");
        simpleTask.setWorkflowInstanceId(workflowId);
        simpleTask.setScheduledTime(System.currentTimeMillis());
        simpleTask.setCallbackAfterSeconds(0);
        simpleTask.setTaskId("simple-task-id");
        simpleTask.setStatus(TaskModel.Status.IN_PROGRESS);

        workflow.getTasks().add(simpleTask);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        when(executionDAOFacade.getTaskModel(simpleTask.getTaskId())).thenReturn(simpleTask);

        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(simpleTask.getTaskId());
        taskResult.setWorkerId("test-worker-id");
        taskResult.log("not ready yet");
        taskResult.setCallbackAfterSeconds(300);
        taskResult.setStatus(TaskResult.Status.IN_PROGRESS);

        workflowExecutor.updateTask(taskResult);
        verify(queueDAO, times(1)).postpone(anyString(), anyString(), anyInt(), anyLong());
        ArgumentCaptor<TaskModel> argumentCaptor = ArgumentCaptor.forClass(TaskModel.class);
        verify(executionDAOFacade, times(1)).updateTask(argumentCaptor.capture());
        assertEquals(TaskModel.Status.SCHEDULED, argumentCaptor.getAllValues().get(0).getStatus());
        assertEquals(
                taskResult.getCallbackAfterSeconds(),
                argumentCaptor.getAllValues().get(0).getCallbackAfterSeconds());
        assertEquals(taskResult.getWorkerId(), argumentCaptor.getAllValues().get(0).getWorkerId());
    }

    @Test
    public void testUpdateTaskWithOutCallbackAfterSeconds() {
        String workflowId = "test-workflow-id";
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowDefinition(new WorkflowDef());

        TaskModel simpleTask = new TaskModel();
        simpleTask.setTaskType(TaskType.SIMPLE.name());
        simpleTask.setReferenceTaskName("simpleTask");
        simpleTask.setWorkflowInstanceId(workflowId);
        simpleTask.setScheduledTime(System.currentTimeMillis());
        simpleTask.setCallbackAfterSeconds(0);
        simpleTask.setTaskId("simple-task-id");
        simpleTask.setStatus(TaskModel.Status.IN_PROGRESS);

        workflow.getTasks().add(simpleTask);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        when(executionDAOFacade.getTaskModel(simpleTask.getTaskId())).thenReturn(simpleTask);

        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(workflowId);
        taskResult.setTaskId(simpleTask.getTaskId());
        taskResult.setWorkerId("test-worker-id");
        taskResult.log("not ready yet");
        taskResult.setStatus(TaskResult.Status.IN_PROGRESS);

        workflowExecutor.updateTask(taskResult);
        verify(queueDAO, times(1)).postpone(anyString(), anyString(), anyInt(), anyLong());
        ArgumentCaptor<TaskModel> argumentCaptor = ArgumentCaptor.forClass(TaskModel.class);
        verify(executionDAOFacade, times(1)).updateTask(argumentCaptor.capture());
        assertEquals(TaskModel.Status.SCHEDULED, argumentCaptor.getAllValues().get(0).getStatus());
        assertEquals(0, argumentCaptor.getAllValues().get(0).getCallbackAfterSeconds());
        assertEquals(taskResult.getWorkerId(), argumentCaptor.getAllValues().get(0).getWorkerId());
    }

    @Test
    public void testIsLazyEvaluateWorkflow() {
        // setup
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("lazyEvaluate");
        workflowDef.setVersion(1);

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setType(SIMPLE.name());
        simpleTask.setName("simple");
        simpleTask.setTaskReferenceName("simple");

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType(FORK_JOIN.name());
        forkTask.setName("fork");
        forkTask.setTaskReferenceName("fork");

        WorkflowTask branchTask1 = new WorkflowTask();
        branchTask1.setType(SIMPLE.name());
        branchTask1.setName("branchTask1");
        branchTask1.setTaskReferenceName("branchTask1");

        WorkflowTask branchTask2 = new WorkflowTask();
        branchTask2.setType(SIMPLE.name());
        branchTask2.setName("branchTask2");
        branchTask2.setTaskReferenceName("branchTask2");

        forkTask.getForkTasks().add(Arrays.asList(branchTask1, branchTask2));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType(JOIN.name());
        joinTask.setName("join");
        joinTask.setTaskReferenceName("join");
        joinTask.setJoinOn(List.of("branchTask2"));

        WorkflowTask doWhile = new WorkflowTask();
        doWhile.setType(DO_WHILE.name());
        doWhile.setName("doWhile");
        doWhile.setTaskReferenceName("doWhile");

        WorkflowTask loopTask = new WorkflowTask();
        loopTask.setType(SIMPLE.name());
        loopTask.setName("loopTask");
        loopTask.setTaskReferenceName("loopTask");

        doWhile.setLoopOver(List.of(loopTask));

        workflowDef.getTasks().addAll(List.of(simpleTask, forkTask, joinTask, doWhile));

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.COMPLETED);

        // when:
        task.setReferenceTaskName("dynamic");
        assertTrue(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

        task.setReferenceTaskName("branchTask1");
        assertFalse(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

        task.setReferenceTaskName("branchTask2");
        assertTrue(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

        task.setReferenceTaskName("simple");
        assertFalse(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

        task.setReferenceTaskName("loopTask__1");
        task.setIteration(1);
        assertFalse(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

        task.setReferenceTaskName("branchTask1");
        task.setStatus(TaskModel.Status.FAILED);
        assertFalse(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));
    }

    @Test
    public void testTaskExtendLease() {
        TaskModel simpleTask = new TaskModel();
        simpleTask.setTaskType(TaskType.SIMPLE.name());
        simpleTask.setReferenceTaskName("simpleTask");
        simpleTask.setWorkflowInstanceId("test-workflow-id");
        simpleTask.setScheduledTime(System.currentTimeMillis());
        simpleTask.setCallbackAfterSeconds(0);
        simpleTask.setTaskId("simple-task-id");
        simpleTask.setStatus(TaskModel.Status.IN_PROGRESS);
        when(executionDAOFacade.getTaskModel(simpleTask.getTaskId())).thenReturn(simpleTask);

        TaskResult taskResult = new TaskResult();
        taskResult.setWorkflowInstanceId(simpleTask.getWorkflowInstanceId());
        taskResult.setTaskId(simpleTask.getTaskId());
        taskResult.log("extend lease");
        taskResult.setExtendLease(true);

        workflowExecutor.updateTask(taskResult);
        verify(executionDAOFacade, times(1)).extendLease(simpleTask);
        verify(queueDAO, times(0)).postpone(anyString(), anyString(), anyInt(), anyLong());
        verify(executionDAOFacade, times(0)).updateTask(any());
    }

    private WorkflowModel generateSampleWorkflow() {
        // setup
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowId");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowId");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setOwnerApp("junit_testRetryWorkflowId");
        workflow.setCreateTime(10L);
        workflow.setEndTime(100L);
        //noinspection unchecked
        workflow.setOutput(Collections.EMPTY_MAP);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        return workflow;
    }

    private List<TaskModel> generateSampleTasks(int count) {
        if (count == 0) {
            return null;
        }
        List<TaskModel> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TaskModel task = new TaskModel();
            task.setTaskId(UUID.randomUUID().toString());
            task.setSeq(i);
            task.setRetryCount(1);
            task.setTaskType("task" + (i + 1));
            task.setStatus(TaskModel.Status.COMPLETED);
            task.setTaskDefName("taskX");
            task.setReferenceTaskName("task_ref" + (i + 1));
            tasks.add(task);
        }

        return tasks;
    }
}
