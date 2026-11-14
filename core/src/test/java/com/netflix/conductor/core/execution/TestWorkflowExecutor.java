/*
 * Copyright 2022 Conductor Authors.
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.service.SchemaService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.mapper.*;
import com.netflix.conductor.core.execution.tasks.*;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import tools.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;
import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

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

    private WorkflowExecutorOps workflowExecutor;
    private NativeContinuationService nativeService;
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
            return new SubWorkflow(objectMapper, new IDGenerator());
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

        @Bean(TASK_TYPE_SWITCH)
        public Switch switchBean() {
            return new Switch();
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

    @Before
    public void init() {
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        metadataDAO = mock(MetadataDAO.class);
        queueDAO = mock(QueueDAO.class);
        workflowStatusListener = mock(WorkflowStatusListener.class);
        taskStatusListener = mock(TaskStatusListener.class);
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        executionLockService = mock(ExecutionLockService.class);
        when(executionDAOFacade.updateTaskIfStatus(any(), any())).thenReturn(true);

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
                        idGenerator,
                        parametersUtils,
                        objectMapper,
                        metadataDAO,
                        mock(SystemTaskRegistry.class)));
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
                        nativeService =
                                new NativeContinuationService(
                                        objectMapper,
                                        new ConductorProperties(),
                                        new MetadataMapperService(metadataDAO),
                                        taskMappers),
                        taskMappers,
                        new HashMap<>(),
                        Duration.ofMinutes(60));
        MetadataMapperService metadataMapperService = new MetadataMapperService(metadataDAO);

        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getActiveWorkerLastPollTimeout()).thenReturn(Duration.ofSeconds(100));
        when(properties.getTaskExecutionPostponeDuration()).thenReturn(Duration.ofSeconds(60));
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(30));
        when(properties.getLockTimeToTry()).thenReturn(Duration.ofMillis(500));

        workflowExecutor =
                new WorkflowExecutorOps(
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
                        Optional.empty(),
                        mock(SchemaService.class));
    }

    @Test
    public void testDecideReQueuesWorkflowOnLockMiss() {
        String workflowId = "contended-workflow-id";
        when(executionLockService.acquireLock(workflowId)).thenReturn(false);

        WorkflowModel result = workflowExecutor.decide(workflowId);

        // decide() must not silently drop the wake-up on a lock miss: it re-queues the workflow for
        // a
        // prompt retry (lockTimeToTry(500ms)/2 = 250ms) so a missed decide from a completion event
        // or
        // the sweeper does not park until the responseTimeout-postponed decider entry fires.
        assertNull(result);
        verify(queueDAO).push(DECIDER_QUEUE, workflowId, 0, Duration.ofMillis(250));
        verify(executionLockService, never()).releaseLock(workflowId);
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
    public void anOpenContinuationReplacesALongerDeciderPostpone() {
        // an earlier decide postponed the workflow by an hour; a continuation is now runnable, and
        // the next decide must come soon enough to move it off a worker that has gone
        WorkflowDef def = new WorkflowDef();
        def.setName("native-postpone");
        def.setSchemaVersion(2);
        def.getMetadata()
                .put("native", Map.of("protocol", 2, "codeType", "native-postpone.v1.code"));
        WorkflowTask entry = new WorkflowTask();
        entry.setName("t0");
        entry.setType("SIMPLE");
        entry.setTaskReferenceName("t0");
        entry.getMetadata().put("lane", "main");
        def.getTasks().add(entry);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("native-postpone-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        workflow.getNativeState().setCodeType("native-postpone.v1.code");
        TaskModel task = new TaskModel();
        task.setTaskId("t0-id");
        task.setReferenceTaskName("t0");
        task.setTaskType("t0");
        task.setWorkflowTask(entry);
        task.setWorkflowInstanceId("native-postpone-1");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setExecuted(true);
        com.netflix.conductor.common.metadata.tasks.TaskContinuation marker =
                new com.netflix.conductor.common.metadata.tasks.TaskContinuation("main", 2);
        marker.setState(com.netflix.conductor.common.metadata.tasks.TaskContinuation.RUNNABLE);
        task.setContinuation(marker);
        workflow.getTasks().add(task);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        workflowExecutor.decide("native-postpone-1");

        verify(queueDAO).setUnackTimeoutIfShorter(DECIDER_QUEUE, "native-postpone-1", 30_000L);
    }

    @Test
    public void aContinuationCompletionAppendsTheFragmentAndSavesTheWorkflowFirst() {
        // The code paused on task t0 (its marker is leased); its completion arrives through the
        // ordinary task update with the next piece of the definition in the output.
        when(metadataDAO.getTaskDef(anyString()))
                .thenAnswer(
                        i ->
                                new com.netflix.conductor.common.metadata.tasks.TaskDef(
                                        i.getArgument(0)));
        WorkflowDef def = new WorkflowDef();
        def.setName("native-order");
        def.setSchemaVersion(2);
        def.getMetadata().put("native", Map.of("protocol", 2, "codeType", "native-order.v1.code"));
        WorkflowTask entry = new WorkflowTask();
        entry.setName("t0");
        entry.setType("SIMPLE");
        entry.setTaskReferenceName("t0");
        entry.getMetadata().put("lane", "main");
        def.getTasks().add(entry);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("native-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        workflow.getNativeState().setCodeType("native-order.v1.code");
        workflow.getNativeState().setActiveContinuation("t0-id");
        TaskModel awaited = new TaskModel();
        awaited.setTaskId("t0-id");
        awaited.setReferenceTaskName("t0");
        awaited.setTaskType("t0");
        awaited.setWorkflowTask(entry);
        awaited.setWorkflowInstanceId("native-1");
        awaited.setStatus(TaskModel.Status.COMPLETED);
        awaited.setExecuted(true);
        com.netflix.conductor.common.metadata.tasks.TaskContinuation marker =
                new com.netflix.conductor.common.metadata.tasks.TaskContinuation("main", 2);
        marker.setState(com.netflix.conductor.common.metadata.tasks.TaskContinuation.LEASED);
        marker.setEpoch(4);
        marker.setQueue("native-order.v1.code:w1");
        awaited.setContinuation(marker);
        workflow.getTasks().add(awaited);

        when(executionLockService.acquireLock(anyString())).thenReturn(true);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(executionDAOFacade.getTaskModel("t0-id")).thenReturn(awaited);
        when(executionDAOFacade.getTask("t0-id")).thenReturn(awaited.toTask());
        List<String> writes = new ArrayList<>();
        doAnswer(
                        i -> {
                            WorkflowModel w = i.getArgument(0);
                            writes.add(
                                    "workflow:"
                                            + (w.getWorkflowDefinition().getTaskByRefName("t1")
                                                    != null));
                            return null;
                        })
                .when(executionDAOFacade)
                .updateWorkflow(any());
        doAnswer(
                        i -> {
                            writes.add("tasks");
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId("native-1");
        result.setTaskId("t0-id");
        result.setWorkerId("w1");
        result.setStatus(TaskResult.Status.COMPLETED);
        result.getOutputData().put("protocol", 2);
        result.getOutputData().put("n", 2);
        result.getOutputData().put("epoch", 4L);
        result.getOutputData()
                .put(
                        "fragment",
                        List.of(
                                Map.of(
                                        "name", "t1",
                                        "taskReferenceName", "t1",
                                        "type", "SIMPLE",
                                        "metadata",
                                                Map.of(
                                                        "lane",
                                                        "main",
                                                        "continuation",
                                                        Map.of("lane", "main", "n", 3)))));
        workflowExecutor.updateTask(result);

        assertTrue(writes.toString(), writes.indexOf("workflow:true") >= 0);
        assertTrue(writes.toString(), writes.indexOf("workflow:true") < writes.indexOf("tasks"));
        assertTrue(marker.isApplied());
        // the decide that follows re-evaluates t0 and schedules the appended t1 after it
        assertTrue(awaited.isExecuted());
        assertTrue(
                workflow.getTasks().stream().anyMatch(t -> "t1".equals(t.getReferenceTaskName())));
        assertEquals(
                TaskModel.Status.COMPLETED, awaited.getStatus()); // the task's own result stands
        verify(queueDAO).remove("native-order.v1.code:w1", "t0-id");
    }

    /** A native workflow whose code paused on t0, with t0's marker in {@code state}. */
    private TaskModel pausedOnT0(String state, TaskModel.Status taskStatus) {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-route");
        def.getMetadata().put("native", Map.of("codeType", "native-route.v1.code"));
        WorkflowTask entry = new WorkflowTask();
        entry.setName("t0");
        entry.setType("SIMPLE");
        entry.setTaskReferenceName("t0");
        def.getTasks().add(entry);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("native-route-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        workflow.getNativeState().setCodeType("native-route.v1.code");
        workflow.getNativeState().setActiveContinuation("t0-id");
        TaskModel t0 = taskOf(entry, "t0", 1);
        t0.setTaskId("t0-id");
        t0.setWorkflowInstanceId("native-route-1");
        t0.setStatus(taskStatus);
        TaskContinuation marker = new TaskContinuation("main", 2);
        marker.setState(state);
        marker.setEpoch(4);
        marker.setQueue("native-route.v1.code");
        t0.setContinuation(marker);
        workflow.getTasks().add(t0);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(executionDAOFacade.getTaskModel("t0-id")).thenReturn(t0);
        return t0;
    }

    private static TaskResult continuationResult(TaskResult.Status status) {
        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId("native-route-1");
        result.setTaskId("t0-id");
        result.setWorkerId("w1");
        result.setStatus(status);
        result.getOutputData().put("protocol", 2);
        result.getOutputData().put("n", 2);
        result.getOutputData().put("epoch", 4L);
        return result;
    }

    @Test
    public void aStaleContinuationCompletionAfterARerunIsRefusedAndLeavesTheTaskAlone() {
        // a rerun reset t0 to SCHEDULED and its marker to WAITING; the old continuation's
        // completion arrives late
        TaskModel t0 = pausedOnT0(TaskContinuation.WAITING, TaskModel.Status.SCHEDULED);

        assertThrows(
                ConflictException.class,
                () -> workflowExecutor.updateTask(continuationResult(TaskResult.Status.COMPLETED)));

        assertEquals(TaskModel.Status.SCHEDULED, t0.getStatus());
        verify(executionDAOFacade, never()).updateTask(any());
    }

    @Test
    public void aDeferredContinuationIsOfferedAgainOnlyAfterItsAckIsSaved() {
        TaskModel t0 = pausedOnT0(TaskContinuation.LEASED, TaskModel.Status.COMPLETED);
        nativeService.setQueueDAO(queueDAO);

        // a worker error: the continuation runs again after a backoff, under the same message id
        workflowExecutor.updateTask(continuationResult(TaskResult.Status.FAILED));

        org.mockito.InOrder order = inOrder(executionDAOFacade, queueDAO);
        order.verify(executionDAOFacade).updateWorkflow(any());
        order.verify(executionDAOFacade).updateTasks(any());
        order.verify(queueDAO).remove("native-route.v1.code", "t0-id");
        order.verify(queueDAO)
                .pushIfNotExists(eq("native-route.v1.code"), eq("t0-id"), anyInt(), anyLong());
        assertTrue(t0.getContinuation().isLeased());
    }

    @Test
    public void aPlainTaskWhoseOutputSaysProtocolCompletesAsATask() {
        WorkflowDef def = new WorkflowDef();
        def.setName("plain");
        WorkflowTask entry = new WorkflowTask();
        entry.setName("p");
        entry.setTaskReferenceName("p");
        def.getTasks().add(entry);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("plain-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        TaskModel p = taskOf(entry, "p", 1);
        p.setTaskId("p-id");
        p.setWorkflowInstanceId("plain-1");
        p.setStatus(TaskModel.Status.IN_PROGRESS);
        workflow.getTasks().add(p);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(executionDAOFacade.getTaskModel("p-id")).thenReturn(p);
        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId("plain-1");
        result.setTaskId("p-id");
        result.setStatus(TaskResult.Status.COMPLETED);
        result.getOutputData().put("protocol", "https");

        workflowExecutor.updateTask(result);

        assertEquals(TaskModel.Status.COMPLETED, p.getStatus());
        assertEquals("https", p.getOutputData().get("protocol"));
        verify(executionDAOFacade).updateTaskIfStatus(p, TaskModel.Status.IN_PROGRESS);
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
    @SuppressWarnings("unchecked")
    public void testTerminateAlreadyTerminatedWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("1");
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
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

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            removeQueueEntryCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(queueDAO)
                .remove(anyString(), anyString());

        // Attempt to terminate an already terminated workflow
        workflowExecutor.terminateWorkflow("workflowId", "reason");

        // Verify workflow status remains TERMINATED
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());

        // Verify no database updates occurred (should be 0, not 1)
        assertEquals(0, updateWorkflowCalledCounter.get());

        // Verify no queue removals occurred (should be 0, not 1)
        assertEquals(0, removeQueueEntryCalledCounter.get());

        // Verify no workflow status notifications were sent
        verify(workflowStatusListener, times(0))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(0))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTerminateWorkflowIdempotencyWithNoSideEffects() {
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

        AtomicInteger removeQueueEntryCalledCounter = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            removeQueueEntryCalledCounter.incrementAndGet();
                            return null;
                        })
                .when(queueDAO)
                .remove(anyString(), anyString());

        // First termination: workflow is RUNNING, should work normally
        workflowExecutor.terminateWorkflow("workflowId", "first termination");

        // Verify workflow was terminated successfully
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());
        assertEquals(1, updateWorkflowCalledCounter.get());
        assertEquals(1, removeQueueEntryCalledCounter.get());

        // Verify workflow status notifications were sent
        verify(workflowStatusListener, times(1))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(1))
                .onWorkflowFinalizedIfEnabled(any(WorkflowModel.class));

        // Reset the mock to clear interaction history for clearer verification
        reset(workflowStatusListener);

        // Second termination: workflow is already TERMINATED, should be idempotent
        workflowExecutor.terminateWorkflow("workflowId", "second termination attempt");

        // Verify workflow status is still TERMINATED
        assertEquals(WorkflowModel.Status.TERMINATED, workflow.getStatus());

        // Verify counters didn't increase (no additional operations)
        assertEquals(1, updateWorkflowCalledCounter.get()); // Still 1, not 2 - no additional update
        assertEquals(
                1, removeQueueEntryCalledCounter.get()); // Still 1, not 2 - no additional removal

        // Verify NO additional workflow status notifications were sent
        verify(workflowStatusListener, times(0))
                .onWorkflowTerminatedIfEnabled(any(WorkflowModel.class));
        verify(workflowStatusListener, times(0))
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

    @Test
    public void restartWithLatestNonNativeDefinitionClearsNativeState() {
        WorkflowTask task = new WorkflowTask();
        task.setName("test_task");
        task.setTaskReferenceName("task_ref");
        WorkflowDef nativeDef = new WorkflowDef();
        nativeDef.setName("was_native");
        nativeDef.setVersion(1);
        nativeDef.getMetadata().put("native", Map.of("codeType", "was_native.v1.code"));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(nativeDef);
        workflow.setWorkflowId("was-native-1");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        WorkflowDef latest = new WorkflowDef();
        latest.setName("was_native");
        latest.setVersion(2);
        latest.setRestartable(true);
        latest.getTasks().add(task);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getLatestWorkflowDef("was_native")).thenReturn(Optional.of(latest));
        when(metadataDAO.getTaskDef("test_task")).thenReturn(new TaskDef());

        workflowExecutor.restart(workflow.getWorkflowId(), true);

        assertEquals(latest, workflow.getWorkflowDefinition());
        assertNull(workflow.getNativeState());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void testStartWorkflowIdempotentReturnsExistingWorkflowModel() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("existing-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("existing-workflow-id");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("existing-workflow-id");
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        when(executionLockService.acquireLock("existing-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("existing-workflow-id", false))
                .thenReturn(workflow);

        WorkflowModel found = workflowExecutor.startWorkflowIdempotent(input);

        assertSame(workflow, found);
        verify(executionDAOFacade, never()).createWorkflow(any());
        verify(executionDAOFacade, never()).getWorkflowModel("existing-workflow-id", false);
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());
        verify(queueDAO, never()).postpone(anyString(), anyString(), anyInt(), anyLong());
        verify(executionLockService).releaseLock("existing-workflow-id");
    }

    @Test
    public void testStartAgentExecutionLoadsRegisteredVersionAndReusesIdempotentExecution() {
        WorkflowTask workerTask = new WorkflowTask();
        workerTask.setName("static_worker");
        workerTask.setTaskReferenceName("static_worker_ref");
        workerTask.setType(TaskType.TASK_TYPE_SIMPLE);

        WorkflowDef agentDef = new WorkflowDef();
        agentDef.setName("registered-agent");
        agentDef.setVersion(3);
        agentDef.setTasks(List.of(workerTask));
        agentDef.setMetadata(
                Map.of(
                        "agent_sdk",
                        "openai",
                        "agentDef",
                        Map.of("name", "raw-framework-agent", "frameworkOnly", true),
                        "normalizedAgentDef",
                        Map.of(
                                "name",
                                "registered-agent",
                                "strategy",
                                "swarm",
                                "agents",
                                List.of(Map.of("name", "peer")))));

        String idempotentWorkflowId =
                UUID.nameUUIDFromBytes(
                                ("agent:registered-agent:agent-request-1")
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        WorkflowModel existing = new WorkflowModel();
        existing.setWorkflowId(idempotentWorkflowId);
        when(metadataDAO.getWorkflowDef("registered-agent", 3)).thenReturn(Optional.of(agentDef));
        when(executionLockService.acquireLock(idempotentWorkflowId)).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO(idempotentWorkflowId, false))
                .thenReturn(existing);

        AgentStartResponse response =
                workflowExecutor.startAgentExecution(
                        AgentStartRequest.builder()
                                .name("registered-agent")
                                .version(3)
                                .prompt("do the work")
                                .idempotencyKey("agent-request-1")
                                .build());

        assertEquals(idempotentWorkflowId, response.getExecutionId());
        assertEquals("registered-agent", response.getAgentName());
        assertEquals(Set.of("static_worker"), new HashSet<>(response.getRequiredWorkers()));
        verify(metadataDAO, never()).getLatestWorkflowDef(anyString());
        verify(executionDAOFacade, never()).createWorkflow(any());
        verify(executionLockService).releaseLock(idempotentWorkflowId);
    }

    @Test
    public void testApplyModelOverrideRewritesOnlyLlmChatCompleteTasks() {
        WorkflowTask llmTask = new WorkflowTask();
        llmTask.setName("LLM_CHAT_COMPLETE");
        llmTask.setTaskReferenceName("llm_ref");
        llmTask.setType("LLM_CHAT_COMPLETE");
        llmTask.setInputParameters(
                new HashMap<>(Map.of("llmProvider", "anthropic", "model", "claude-3-opus")));

        WorkflowTask workerTask = new WorkflowTask();
        workerTask.setName("static_worker");
        workerTask.setTaskReferenceName("static_worker_ref");
        workerTask.setType(TaskType.TASK_TYPE_SIMPLE);
        workerTask.setInputParameters(new HashMap<>(Map.of("model", "should-not-change")));

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork_ref");
        fork.setType("FORK_JOIN");
        WorkflowTask nestedLlmTask = new WorkflowTask();
        nestedLlmTask.setName("LLM_CHAT_COMPLETE");
        nestedLlmTask.setTaskReferenceName("nested_llm_ref");
        nestedLlmTask.setType("LLM_CHAT_COMPLETE");
        nestedLlmTask.setInputParameters(
                new HashMap<>(Map.of("llmProvider", "openai", "model", "gpt-4o")));
        fork.setForkTasks(List.of(List.of(nestedLlmTask)));

        WorkflowDef def = new WorkflowDef();
        def.setTasks(List.of(llmTask, workerTask, fork));

        WorkflowExecutorOps.applyModelOverride(def, "openai/gpt-5");

        assertEquals("openai", llmTask.getInputParameters().get("llmProvider"));
        assertEquals("gpt-5", llmTask.getInputParameters().get("model"));
        assertEquals("openai", nestedLlmTask.getInputParameters().get("llmProvider"));
        assertEquals("gpt-5", nestedLlmTask.getInputParameters().get("model"));
        assertEquals(
                "a non-LLM task's own 'model' input parameter must be left untouched",
                "should-not-change",
                workerTask.getInputParameters().get("model"));
    }

    @Test(expected = NonTransientException.class)
    public void testStartWorkflowIdempotentRejectsExistingWorkflowOwnedByDifferentParentTask() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("owned-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("owned-workflow-id");
        input.setParentWorkflowId("parent-workflow");
        input.setParentWorkflowTaskId("parent-task");

        WorkflowModel existingWorkflow = new WorkflowModel();
        existingWorkflow.setWorkflowId("owned-workflow-id");
        existingWorkflow.setStatus(WorkflowModel.Status.RUNNING);
        existingWorkflow.setParentWorkflowId("other-parent-workflow");
        existingWorkflow.setParentWorkflowTaskId("other-parent-task");

        when(executionLockService.acquireLock("owned-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("owned-workflow-id", false))
                .thenReturn(existingWorkflow);

        try {
            workflowExecutor.startWorkflowIdempotent(input);
        } finally {
            verify(executionDAOFacade, never()).createWorkflow(any());
            verify(executionDAOFacade, never()).removeWorkflow(anyString(), anyBoolean());
            verify(executionLockService).releaseLock("owned-workflow-id");
        }
    }

    @Test
    public void testStartWorkflowIdempotentCreatesWorkflowWhenExistingWorkflowIsNotFound() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("new-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("new-workflow-id");

        when(executionLockService.acquireLock("new-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("new-workflow-id", false))
                .thenThrow(new NotFoundException("missing"));

        WorkflowModel created = workflowExecutor.startWorkflowIdempotent(input);

        assertEquals("new-workflow-id", created.getWorkflowId());
        verify(executionDAOFacade).createWorkflow(any());
        verify(executionDAOFacade, never()).getWorkflowModel("new-workflow-id", false);
        verify(executionLockService, atLeastOnce()).releaseLock("new-workflow-id");
    }

    /**
     * A workflow that was written is not deleted when a later step of the start fails (Jepsen's
     * "lost workflows"): it stays queued for the sweeper, and the start reports it.
     */
    @Test
    public void startDoesNotDeleteAWorkflowItCreatedWhenALaterStepFails() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("kept-workflow");
        workflowDef.setVersion(1);
        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("kept-workflow-id");

        when(executionLockService.acquireLock("kept-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("kept-workflow-id", false))
                .thenThrow(new NotFoundException("missing"));
        org.mockito.Mockito.doThrow(new RuntimeException("database hiccup"))
                .when(executionDAOFacade)
                .populateWorkflowAndTaskPayloadData(any());

        WorkflowModel created = workflowExecutor.startWorkflowIdempotent(input);

        assertEquals("kept-workflow-id", created.getWorkflowId());
        verify(executionDAOFacade, never()).removeWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void startDoesNotDeleteWhenWritingTheWorkflowFails() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("ambiguous-workflow");
        workflowDef.setVersion(1);
        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("ambiguous-id");

        when(executionLockService.acquireLock("ambiguous-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("ambiguous-id", false))
                .thenThrow(new NotFoundException("missing"));
        org.mockito.Mockito.doThrow(new RuntimeException("connection reset"))
                .when(executionDAOFacade)
                .createWorkflow(any());

        try {
            workflowExecutor.startWorkflowIdempotent(input);
            fail("the start's outcome is indefinite and must be reported as an error");
        } catch (RuntimeException expected) {
            // indefinite: the row may or may not exist
        }
        verify(executionDAOFacade, never()).removeWorkflow(anyString(), anyBoolean());
    }

    @Test
    public void aNativeTaskUpdateTakesNoLockAndIsSavedOnlyIfTheTaskIsAsRead() {
        // Found by the durability bench: a late completion overwrote a timeout that decide had
        // just applied, so a superseded attempt counted as completed next to its retry. The
        // update is saved only if the task is still as it was read.
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-n");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        WorkflowDef def = new WorkflowDef();
        def.setName("native-lock");
        workflow.setWorkflowDefinition(def);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        TaskModel task = new TaskModel();
        task.setTaskId("t-1");
        task.setTaskType("step");
        task.setReferenceTaskName("step");
        task.setWorkflowInstanceId("wf-n");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        when(executionDAOFacade.getTaskModel("t-1")).thenReturn(task);
        when(executionDAOFacade.updateTaskIfStatus(task, TaskModel.Status.IN_PROGRESS))
                .thenReturn(false);

        TaskResult result = new TaskResult();
        result.setTaskId("t-1");
        result.setWorkflowInstanceId("wf-n");
        result.setStatus(TaskResult.Status.COMPLETED);
        workflowExecutor.updateTask(result);

        verify(executionDAOFacade).updateTaskIfStatus(task, TaskModel.Status.IN_PROGRESS);
        verify(executionDAOFacade, never()).updateTask(any());
        verify(executionLockService, never()).acquireLock(anyString());
    }

    @Test
    public void classifiesStorageOutagesAsTransient() {
        assertTrue(
                WorkflowExecutorOps.isTransientStorageFailure(
                        new com.netflix.conductor.core.exception.NonTransientException(
                                "wrapped",
                                new java.sql.SQLException("connection refused", "08001"))));
        assertTrue(
                WorkflowExecutorOps.isTransientStorageFailure(
                        new RuntimeException(
                                new java.sql.SQLException("deadlock detected", "40P01"))));
        assertTrue(
                WorkflowExecutorOps.isTransientStorageFailure(
                        new RuntimeException(
                                new java.sql.SQLException("terminating connection", "57P01"))));
        assertTrue( // HikariCP, no SQL state
                WorkflowExecutorOps.isTransientStorageFailure(
                        new com.netflix.conductor.core.exception.NonTransientException(
                                "Connection is closed",
                                new java.sql.SQLException("Connection is closed"))));
        assertFalse(
                WorkflowExecutorOps.isTransientStorageFailure(
                        new RuntimeException(new java.sql.SQLException("syntax error", "42601"))));
        assertFalse(
                WorkflowExecutorOps.isTransientStorageFailure(
                        new IllegalArgumentException("bad task")));
    }

    @Test
    public void testStartWorkflowIdempotentIgnoresIndexOnlyWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("indexed-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("indexed-workflow-id");

        when(executionLockService.acquireLock("indexed-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("indexed-workflow-id", false))
                .thenThrow(new NotFoundException("missing"));
        when(executionDAOFacade.getWorkflowModel("indexed-workflow-id", false))
                .thenReturn(new WorkflowModel());

        WorkflowModel created = workflowExecutor.startWorkflowIdempotent(input);

        assertEquals("indexed-workflow-id", created.getWorkflowId());
        verify(executionDAOFacade).createWorkflow(any());
        verify(executionDAOFacade, never()).getWorkflowModel("indexed-workflow-id", false);
        verify(executionLockService, atLeastOnce()).releaseLock("indexed-workflow-id");
    }

    @Test(expected = TransientException.class)
    public void testStartWorkflowIdempotentPropagatesExecutionStoreLookupFailure() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("transient-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("transient-workflow-id");

        when(executionLockService.acquireLock("transient-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("transient-workflow-id", false))
                .thenThrow(new TransientException("execution store unavailable"));

        try {
            workflowExecutor.startWorkflowIdempotent(input);
        } finally {
            verify(executionDAOFacade, never()).createWorkflow(any());
            verify(executionDAOFacade, never()).removeWorkflow(anyString(), anyBoolean());
            verify(executionLockService).releaseLock("transient-workflow-id");
        }
    }

    @Test
    public void testStartWorkflowIdempotentCreatesWorkflowAndExpeditesDecider() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("queued-workflow");
        workflowDef.setVersion(1);

        StartWorkflowInput input = new StartWorkflowInput();
        input.setWorkflowDefinition(workflowDef);
        input.setName(workflowDef.getName());
        input.setVersion(workflowDef.getVersion());
        input.setWorkflowInput(Collections.emptyMap());
        input.setWorkflowId("queued-workflow-id");

        when(executionLockService.acquireLock("queued-workflow-id")).thenReturn(true);
        when(executionDAOFacade.getWorkflowModelFromExecutionDAO("queued-workflow-id", false))
                .thenThrow(new NotFoundException("missing"));
        when(queueDAO.containsMessage("_deciderQueue", "queued-workflow-id")).thenReturn(true);

        WorkflowModel created = workflowExecutor.startWorkflowIdempotent(input);

        assertEquals("queued-workflow-id", created.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, created.getStatus());
        verify(executionDAOFacade).createWorkflow(any());
        verify(queueDAO).postpone("_deciderQueue", "queued-workflow-id", 10, 0);
        verify(executionLockService, atLeastOnce()).releaseLock("queued-workflow-id");
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
        // noinspection unchecked
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
        // noinspection unchecked
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
        // noinspection unchecked
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
        // noinspection unchecked
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
        // noinspection unchecked
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

    /**
     * A retry reopens a cancelled JOIN in place. It reaches the system-task queue only once the
     * retry is saved: otherwise the async executor can run it on the stored workflow, where the
     * attempt the retry replaces is not yet marked retried, and fail the workflow again on it.
     */
    @Test
    public void testRetryQueuesAReopenedJoinOnlyAfterSavingTheRetry() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryJoinOrder");
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryJoinOrder");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowModel.Status.FAILED);

        TaskModel forkTask = new TaskModel();
        forkTask.setTaskType(TaskType.FORK_JOIN.toString());
        forkTask.setTaskId(UUID.randomUUID().toString());
        forkTask.setSeq(1);
        forkTask.setStatus(TaskModel.Status.COMPLETED);
        forkTask.setReferenceTaskName("task_fork");

        TaskModel branch = new TaskModel();
        branch.setTaskId(UUID.randomUUID().toString());
        branch.setSeq(2);
        branch.setTaskType(TaskType.SIMPLE.toString());
        branch.setStatus(TaskModel.Status.FAILED);
        branch.setTaskDefName("task1");
        branch.setWorkflowTask(new WorkflowTask());
        branch.setReferenceTaskName("task1_ref");

        TaskModel joinTask = new TaskModel();
        joinTask.setTaskType(TaskType.JOIN.toString());
        joinTask.setTaskId(UUID.randomUUID().toString());
        joinTask.setSeq(3);
        joinTask.setStatus(TaskModel.Status.CANCELED);
        joinTask.setReferenceTaskName("task_join");
        joinTask.getInputData().put("joinOn", List.of(branch.getReferenceTaskName()));

        workflow.getTasks().addAll(Arrays.asList(forkTask, branch, joinTask));
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        assertEquals(TaskModel.Status.IN_PROGRESS, joinTask.getStatus());
        org.mockito.InOrder order = inOrder(executionDAOFacade, queueDAO);
        order.verify(executionDAOFacade).updateTasks(any());
        order.verify(queueDAO).push(anyString(), eq(joinTask.getTaskId()), anyInt(), anyLong());
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
        // noinspection unchecked
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

    @Test
    public void testRetryWorkflowClearsLegacySubWorkflowIdFromRetriedTask() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("testRetryWorkflowClearsLegacySubWorkflowIdFromRetriedTask");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setPriority(0);
        workflow.setReasonForIncompletion("subworkflow failed");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testRetryWorkflowClearsLegacySubWorkflowIdFromRetriedTask");
        workflowDef.setVersion(1);
        workflow.setWorkflowDefinition(workflowDef);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("integration_test_wf");
        workflowTask.setTaskReferenceName("sub1");
        workflowTask.setType(SUB_WORKFLOW.name());
        workflowTask.setTaskDefinition(new TaskDef("integration_test_wf"));

        TaskModel failedSubWorkflowTask = new TaskModel();
        failedSubWorkflowTask.setTaskId("failed-subworkflow-task");
        failedSubWorkflowTask.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);
        failedSubWorkflowTask.setTaskDefName("integration_test_wf");
        failedSubWorkflowTask.setReferenceTaskName("sub1");
        failedSubWorkflowTask.setWorkflowTask(workflowTask);
        failedSubWorkflowTask.setWorkflowInstanceId(workflow.getWorkflowId());
        failedSubWorkflowTask.setStatus(TaskModel.Status.FAILED);
        failedSubWorkflowTask.setSeq(1);
        failedSubWorkflowTask.setReasonForIncompletion("subworkflow failed");
        failedSubWorkflowTask.setSubWorkflowId("old-subworkflow-id");
        failedSubWorkflowTask.setExternalOutputPayloadStoragePath("old-output.json");
        failedSubWorkflowTask.getInputData().put("subWorkflowId", "old-subworkflow-id");
        failedSubWorkflowTask.getOutputData().put("subWorkflowId", "old-subworkflow-id");
        failedSubWorkflowTask.getOutputData().put("result", "stale");

        workflow.getTasks().add(failedSubWorkflowTask);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(workflowDef));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        TaskModel retriedTask =
                workflow.getTasks().stream()
                        .filter(task -> "sub1".equals(task.getReferenceTaskName()))
                        .filter(task -> !task.isRetried())
                        .findFirst()
                        .orElseThrow();

        assertNull(retriedTask.getSubWorkflowId());
        assertFalse(retriedTask.getInputData().containsKey("subWorkflowId"));
        assertFalse(retriedTask.getOutputData().containsKey("subWorkflowId"));
        assertNull(retriedTask.getExternalOutputPayloadStoragePath());
        assertTrue(retriedTask.getOutputData().isEmpty());
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
        // noinspection unchecked
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
    public void aRerunTaskIsNotWrittenAgainAfterItIsQueued() {
        // a worker can poll and complete the task as soon as it is queued: a later write of the
        // rerun's copy would put it back to SCHEDULED, and it would run again
        WorkflowTask entry = new WorkflowTask();
        entry.setName("task1");
        entry.setTaskReferenceName("task1_ref");
        WorkflowDef def = new WorkflowDef();
        def.setName("rerun_order");
        def.setVersion(1);
        def.getTasks().add(entry);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("rerun-order-1");
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        TaskModel task = new TaskModel();
        task.setTaskId("task1-id");
        task.setSeq(1);
        task.setTaskType(TaskType.SIMPLE.toString());
        task.setTaskDefName("task1");
        task.setReferenceTaskName("task1_ref");
        task.setWorkflowTask(entry);
        task.setStatus(TaskModel.Status.FAILED);
        workflow.getTasks().add(task);
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt())).thenReturn(Optional.of(def));
        List<String> events = new ArrayList<>();
        doAnswer(
                        i -> {
                            if ("task1-id".equals(i.getArgument(1))) {
                                events.add("queued");
                            }
                            return null;
                        })
                .when(queueDAO)
                .push(anyString(), anyString(), anyInt(), anyLong());
        doAnswer(
                        i -> {
                            if (((TaskModel) i.getArgument(0)).getTaskId().equals("task1-id")) {
                                events.add("written");
                            }
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTask(any());
        doAnswer(
                        i -> {
                            List<TaskModel> tasks = i.getArgument(0);
                            if (tasks.stream().anyMatch(t -> t.getTaskId().equals("task1-id"))) {
                                events.add("written");
                            }
                            return null;
                        })
                .when(executionDAOFacade)
                .updateTasks(any());

        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(workflow.getWorkflowId());
        request.setReRunFromTaskId("task1-id");
        workflowExecutor.rerun(request);

        assertEquals("queued", events.get(events.size() - 1));
        assertEquals(1, events.stream().filter("queued"::equals).count());
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
        // noinspection unchecked
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

    /**
     * A native rerun from a task inside a fork reopens the fork's JOIN. The JOIN reaches the
     * system-task queue only once it and the rerun task are saved: otherwise the async executor can
     * run it on the stored workflow, where the rerun task is still COMPLETED, and close it again.
     */
    @Test
    public void nativeRerunQueuesReopenedJoinOnlyAfterSavingIt() {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-rerun");
        def.setSchemaVersion(2);
        def.getMetadata().put("native", Map.of("protocol", 2, "codeType", "native-rerun.v1.code"));
        WorkflowTask a = new WorkflowTask();
        a.setName("a");
        a.setType("SIMPLE");
        a.setTaskReferenceName("a");
        a.getMetadata().put("lane", "L1");
        WorkflowTask fork = new WorkflowTask();
        fork.setName("f");
        fork.setType(TaskType.FORK_JOIN.name());
        fork.setTaskReferenceName("f");
        fork.getMetadata().put("lane", "main");
        fork.setForkTasks(List.of(new ArrayList<>(List.of(a))));
        WorkflowTask join = new WorkflowTask();
        join.setName("f_join");
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("f_join");
        join.getMetadata().put("lane", "main");
        join.setJoinOn(List.of("a"));
        def.getTasks().addAll(List.of(fork, join));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("native-rerun-1");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        workflow.getNativeState().setCodeType("native-rerun.v1.code");
        TaskModel forkTask = taskOf(fork, TaskType.TASK_TYPE_FORK, 1);
        TaskModel aTask = taskOf(a, "a", 2);
        TaskModel joinTask = taskOf(join, TaskType.TASK_TYPE_JOIN, 3);
        workflow.getTasks().addAll(List.of(forkTask, aTask, joinTask));
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);

        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(workflow.getWorkflowId());
        request.setReRunFromTaskId(aTask.getTaskId());
        workflowExecutor.rerun(request);

        assertEquals(TaskModel.Status.IN_PROGRESS, joinTask.getStatus());
        org.mockito.InOrder order = inOrder(executionDAOFacade, queueDAO);
        order.verify(executionDAOFacade).updateTask(aTask);
        order.verify(executionDAOFacade).updateTask(joinTask);
        order.verify(queueDAO).push(anyString(), eq(joinTask.getTaskId()), anyInt(), anyLong());
    }

    @Test
    public void retryAfterTheCodeThrewRemovesWhatThatContinuationDrew() {
        WorkflowModel workflow = threwAfterAnIf();
        TaskModel a = workflow.getTasks().get(0);

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        // the if, its case and the mark go: the code draws them again when it runs again
        assertEquals(
                List.of("a"),
                workflow.getWorkflowDefinition().collectTasks().stream()
                        .map(WorkflowTask::getTaskReferenceName)
                        .toList());
        assertEquals(List.of(a), workflow.getTasks());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(TaskContinuation.RUNNABLE, a.getContinuation().getState());
        assertFalse(a.getContinuation().hasError());
        verify(executionDAOFacade).removeTask("thrown-id");
    }

    @Test
    public void retryAfterTheCodeThrewKeepsWhatAlreadyHasATask() {
        WorkflowModel workflow = threwAfterAnIf();
        WorkflowTask ifEntry = workflow.getWorkflowDefinition().getTasks().get(1);
        workflow.getTasks().add(1, taskOf(ifEntry, TaskType.TASK_TYPE_SWITCH, 2));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        // not the code's own throw in one completion: only the mark goes
        assertEquals(
                List.of("a", "if_x", "b"),
                workflow.getWorkflowDefinition().collectTasks().stream()
                        .map(WorkflowTask::getTaskReferenceName)
                        .toList());
    }

    /** a, then an if the code went past, then a throw: one continuation (main#2) drew both. */
    private WorkflowModel threwAfterAnIf() {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-throw");
        def.setSchemaVersion(2);
        def.getMetadata().put("native", Map.of("codeType", "native-throw.v1.code"));
        WorkflowTask a = new WorkflowTask();
        a.setName("a");
        a.setType("SIMPLE");
        a.setTaskReferenceName("a");
        WorkflowTask b = new WorkflowTask();
        b.setName("b");
        b.setType("SIMPLE");
        b.setTaskReferenceName("b");
        WorkflowTask ifEntry = new WorkflowTask();
        ifEntry.setName("if_x");
        ifEntry.setType(TaskType.SWITCH.name());
        ifEntry.setTaskReferenceName("if_x");
        ifEntry.setEvaluatorType("native-code");
        ifEntry.setExpression("c");
        ifEntry.setDecisionCases(new HashMap<>(Map.of("true", new ArrayList<>(List.of(b)))));
        ifEntry.getMetadata().put(NativeContinuationService.APPENDED_BY, "main#2");
        WorkflowTask mark = new WorkflowTask();
        mark.setName("thrown");
        mark.setType(TaskType.TERMINATE.name());
        mark.setTaskReferenceName("thrown");
        mark.getMetadata().put(NativeContinuationService.APPENDED_BY, "main#2");
        def.getTasks().addAll(List.of(a, ifEntry, mark));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId("native-rerun-1");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setNativeState(new com.netflix.conductor.common.run.NativeState());
        TaskModel aTask = taskOf(a, "a", 1);
        TaskContinuation marker = new TaskContinuation("main", 2);
        marker.setState(TaskContinuation.APPLIED);
        marker.setDone(true);
        marker.setError(new HashMap<>(Map.of("message", "boom")));
        aTask.setContinuation(marker);
        TaskModel thrown = taskOf(mark, TaskType.TASK_TYPE_TERMINATE, 2);
        workflow.getTasks().addAll(new ArrayList<>(List.of(aTask, thrown)));
        workflow.setFailedTaskId(thrown.getTaskId());
        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        return workflow;
    }

    private static TaskModel taskOf(WorkflowTask entry, String type, int seq) {
        TaskModel task = new TaskModel();
        task.setTaskId(entry.getTaskReferenceName() + "-id");
        task.setReferenceTaskName(entry.getTaskReferenceName());
        task.setTaskType(type);
        task.setTaskDefName(entry.getName());
        task.setWorkflowTask(entry);
        task.setWorkflowInstanceId("native-rerun-1");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setExecuted(true);
        task.setSeq(seq);
        return task;
    }

    @Test
    public void testRerunFromATaskInsideACompletedLoopReopensTheLoop() {
        WorkflowTask body = new WorkflowTask();
        body.setName("a");
        body.setTaskReferenceName("a");
        body.setType(TaskType.SIMPLE.name());
        WorkflowTask loopEntry = new WorkflowTask();
        loopEntry.setName("loop");
        loopEntry.setTaskReferenceName("loop");
        loopEntry.setType(TaskType.DO_WHILE.name());
        loopEntry.setLoopOver(List.of(body));
        WorkflowTask afterEntry = new WorkflowTask();
        afterEntry.setName("after");
        afterEntry.setTaskReferenceName("after");
        afterEntry.setType(TaskType.SIMPLE.name());
        WorkflowDef def = new WorkflowDef();
        def.setName("loop_rerun");
        def.setVersion(1);
        def.setTasks(new ArrayList<>(List.of(loopEntry, afterEntry)));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("loop-rerun");
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.COMPLETED);

        TaskModel loop = new TaskModel();
        loop.setTaskId("loop-id");
        loop.setSeq(1);
        loop.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
        loop.setReferenceTaskName("loop");
        loop.setWorkflowTask(loopEntry);
        loop.setStatus(TaskModel.Status.COMPLETED);
        loop.setExecuted(true);
        loop.setEndTime(500);
        loop.setIteration(3);
        loop.getOutputData().put("iteration", 3);
        for (int i = 1; i <= 3; i++) {
            loop.getOutputData().put(String.valueOf(i), Map.of("a", Map.of("n", i)));
        }
        workflow.getTasks().add(loop);
        for (int i = 1; i <= 3; i++) {
            TaskModel a = new TaskModel();
            a.setTaskId("a-" + i);
            a.setSeq(1 + i);
            a.setTaskType(TaskType.SIMPLE.name());
            a.setTaskDefName("a");
            a.setReferenceTaskName("a__" + i);
            a.setIteration(i);
            a.setWorkflowTask(body);
            a.setStatus(TaskModel.Status.COMPLETED);
            workflow.getTasks().add(a);
        }
        TaskModel after = new TaskModel();
        after.setTaskId("after-id");
        after.setSeq(5);
        after.setTaskType(TaskType.SIMPLE.name());
        after.setTaskDefName("after");
        after.setReferenceTaskName("after");
        after.setWorkflowTask(afterEntry);
        after.setStatus(TaskModel.Status.COMPLETED);
        workflow.getTasks().add(after);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(workflow.getWorkflowId());
        request.setReRunFromTaskId("a-2");
        workflowExecutor.rerun(request);

        // the rest of iteration 2, iteration 3 and the task after the loop are gone
        assertEquals(
                List.of("loop", "a__1", "a__2"),
                workflow.getTasks().stream().map(TaskModel::getReferenceTaskName).toList());
        // the loop goes on from iteration 2: it waits for it, then decides by its condition
        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(2, loop.getIteration());
        assertEquals(0, loop.getEndTime());
        assertFalse(loop.isExecuted());
        assertEquals(Set.of("iteration", "1"), loop.getOutputData().keySet());
        assertEquals(2, loop.getOutputData().get("iteration"));
    }

    private static WorkflowTask simpleEntry(String ref) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(ref);
        entry.setTaskReferenceName(ref);
        entry.setType(TaskType.SIMPLE.name());
        TaskDef taskDef = new TaskDef(ref);
        taskDef.setRetryCount(0);
        entry.setTaskDefinition(taskDef);
        return entry;
    }

    private static TaskModel ran(WorkflowTask entry, String ref, int seq, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(ref + "-" + seq);
        task.setSeq(seq);
        task.setTaskType(
                TaskType.FORK_JOIN.name().equals(entry.getType())
                        ? TaskType.TASK_TYPE_FORK
                        : entry.getType());
        task.setTaskDefName(entry.getName());
        task.setReferenceTaskName(ref);
        task.setWorkflowTask(entry);
        task.setStatus(status);
        task.setExecuted(true);
        task.setScheduledTime(seq);
        task.setFirstScheduledTime(seq);
        task.setEndTime(100 + seq);
        return task;
    }

    /** fork of [b1a, b1b] and [b2a, b2b], then a JOIN on b1b and b2b */
    private WorkflowModel forkOfTwoBranchesOfTwo(String id) {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType(TaskType.FORK_JOIN.name());
        fork.setForkTasks(
                List.of(
                        List.of(simpleEntry("b1a"), simpleEntry("b1b")),
                        List.of(simpleEntry("b2a"), simpleEntry("b2b"))));
        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join");
        join.setType(TaskType.JOIN.name());
        join.setJoinOn(List.of("b1b", "b2b"));
        WorkflowDef def = new WorkflowDef();
        def.setName("fork_rerun");
        def.setVersion(1);
        def.setTasks(new ArrayList<>(List.of(fork, join)));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        return workflow;
    }

    @Test
    public void testRerunFromABranchSchedulesAgainWhatTheOtherBranchScheduledAfterIt() {
        WorkflowModel workflow = forkOfTwoBranchesOfTwo("fork-rerun");
        WorkflowTask fork = workflow.getWorkflowDefinition().getTasks().get(0);
        WorkflowTask joinEntry = workflow.getWorkflowDefinition().getTasks().get(1);
        workflow.getTasks().add(ran(fork, "fork", 1, TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(0).get(0), "b1a", 2, TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(1).get(0), "b2a", 3, TaskModel.Status.COMPLETED));
        TaskModel join = ran(joinEntry, "join", 4, TaskModel.Status.COMPLETED);
        join.setTaskType(TaskType.TASK_TYPE_JOIN);
        join.getInputData().put("joinOn", List.of("b1b", "b2b"));
        workflow.getTasks().add(join);
        TaskModel b1b =
                ran(fork.getForkTasks().get(0).get(1), "b1b", 5, TaskModel.Status.COMPLETED);
        workflow.getTasks().add(b1b);
        // the other branch's second task was scheduled after b1b
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(1).get(1), "b2b", 6, TaskModel.Status.COMPLETED));

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(workflow.getWorkflowId());
        request.setReRunFromTaskId(b1b.getTaskId());
        workflowExecutor.rerun(request);

        // b2b, removed because it was scheduled later, is scheduled again (DECIDER_BUGS item 3)
        TaskModel b2b = workflow.getTaskByRefName("b2b");
        assertNotNull(b2b);
        assertEquals(TaskModel.Status.SCHEDULED, b2b.getStatus());
        assertNotEquals("b2b-6", b2b.getTaskId());
        // the JOIN waits again, for b1b's new run and for b2b
        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
        assertFalse(join.isExecuted());
        assertEquals(TaskModel.Status.SCHEDULED, b1b.getStatus());
        // a rerun gives the task a fresh total time budget (DECIDER_BUGS item 12)
        assertTrue(b1b.getFirstScheduledTime() > 1_000);
        assertEquals(
                List.of("fork", "b1a", "b2a", "join", "b1b", "b2b"),
                workflow.getTasks().stream().map(TaskModel::getReferenceTaskName).toList());
    }

    @Test
    public void testRerunFromAnAttemptARetryReplacedRerunsFromTheLatestAttempt() {
        WorkflowModel workflow = forkOfTwoBranchesOfTwo("old-attempt-rerun");
        WorkflowTask fork = workflow.getWorkflowDefinition().getTasks().get(0);
        WorkflowTask joinEntry = workflow.getWorkflowDefinition().getTasks().get(1);
        WorkflowTask b1bEntry = fork.getForkTasks().get(0).get(1);
        workflow.getTasks().add(ran(fork, "fork", 1, TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(0).get(0), "b1a", 2, TaskModel.Status.COMPLETED));
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(1).get(0), "b2a", 3, TaskModel.Status.COMPLETED));
        TaskModel join = ran(joinEntry, "join", 4, TaskModel.Status.COMPLETED);
        join.setTaskType(TaskType.TASK_TYPE_JOIN);
        join.getInputData().put("joinOn", List.of("b1b", "b2b"));
        workflow.getTasks().add(join);
        TaskModel firstAttempt = ran(b1bEntry, "b1b", 5, TaskModel.Status.FAILED);
        firstAttempt.setExecuted(false);
        firstAttempt.setRetried(true);
        workflow.getTasks().add(firstAttempt);
        workflow.getTasks()
                .add(ran(fork.getForkTasks().get(1).get(1), "b2b", 6, TaskModel.Status.COMPLETED));
        TaskModel secondAttempt = ran(b1bEntry, "b1b", 7, TaskModel.Status.COMPLETED);
        secondAttempt.setRetryCount(1);
        workflow.getTasks().add(secondAttempt);

        when(executionDAOFacade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(workflow.getWorkflowId());
        request.setReRunFromTaskId(firstAttempt.getTaskId());
        workflowExecutor.rerun(request);

        // the rerun starts from the latest attempt; the replaced one stays as it was
        // (DECIDER_BUGS item 8)
        assertEquals(TaskModel.Status.SCHEDULED, secondAttempt.getStatus());
        assertEquals(TaskModel.Status.FAILED, firstAttempt.getStatus());
        assertTrue(firstAttempt.isRetried());
        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
        // b2b was scheduled before the latest attempt, so it is kept
        assertEquals(TaskModel.Status.COMPLETED, workflow.getTaskByRefName("b2b").getStatus());
    }

    @Test
    public void testAnOptionalTaskThatFailsLastIsSavedAsCompletedWithErrors() {
        WorkflowTask first = simpleEntry("first");
        first.setOptional(true);
        WorkflowTask last = simpleEntry("last");
        last.setOptional(true);
        WorkflowDef def = new WorkflowDef();
        def.setName("optional_last");
        def.setVersion(1);
        def.setTasks(new ArrayList<>(List.of(first, last)));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("optional-last");
        workflow.setCreateTime(1L);
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.getTasks().add(ran(first, "first", 1, TaskModel.Status.COMPLETED_WITH_ERRORS));
        TaskModel failed = ran(last, "last", 2, TaskModel.Status.FAILED);
        failed.setExecuted(false);
        workflow.getTasks().add(failed);

        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);
        workflowExecutor.decide(workflow.getWorkflowId());

        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, failed.getStatus());
        // the change is saved, not only made in memory (DECIDER_BUGS item 1)
        verify(executionDAOFacade)
                .updateTasks(
                        org.mockito.ArgumentMatchers.argThat(
                                (List<TaskModel> tasks) -> tasks.contains(failed)));
    }

    private WorkflowModel running(String id, WorkflowTask... tasks) {
        WorkflowDef def = new WorkflowDef();
        def.setName(id.replace('-', '_'));
        def.setVersion(1);
        def.setTasks(new ArrayList<>(List.of(tasks)));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setCreateTime(1L);
        when(executionDAOFacade.getWorkflowModel(eq(id), anyBoolean())).thenReturn(workflow);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);
        return workflow;
    }

    private static TaskModel skipped(String ref) {
        TaskModel task = new TaskModel();
        task.setTaskId(ref + "-skipped");
        task.setReferenceTaskName(ref);
        task.setTaskType(ref);
        task.setStatus(TaskModel.Status.SKIPPED);
        return task;
    }

    private static List<String> refs(WorkflowModel workflow) {
        return workflow.getTasks().stream().map(TaskModel::getReferenceTaskName).toList();
    }

    @Test
    public void testASkippedFirstTaskOfABranchLetsTheBranchGoOn() {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType(TaskType.FORK_JOIN.name());
        fork.setForkTasks(
                List.of(List.of(simpleEntry("a"), simpleEntry("b")), List.of(simpleEntry("c"))));
        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join");
        join.setType(TaskType.JOIN.name());
        join.setJoinOn(List.of("b", "c"));
        WorkflowModel workflow = running("skip-branch-head", fork, join);
        workflow.getTasks().add(skipped("a"));

        workflowExecutor.decide(workflow.getWorkflowId());

        // the skipped task counts as finished when reached: its branch goes on with b
        // (DECIDER_BUGS item 13)
        assertEquals(List.of("a", "fork", "b", "c", "join"), refs(workflow));
        assertEquals(TaskModel.Status.SCHEDULED, workflow.getTaskByRefName("b").getStatus());
    }

    @Test
    public void testASkippedFirstTaskOfACaseLetsTheCaseGoOn() {
        WorkflowTask choose = new WorkflowTask();
        choose.setName("choose");
        choose.setTaskReferenceName("choose");
        choose.setType(TaskType.SWITCH.name());
        choose.setEvaluatorType("value-param");
        choose.setExpression("pick");
        choose.getInputParameters().put("pick", "x");
        choose.setDecisionCases(Map.of("x", List.of(simpleEntry("a"), simpleEntry("b"))));
        WorkflowModel workflow = running("skip-case-head", choose, simpleEntry("after"));
        workflow.getTasks().add(skipped("a"));

        workflowExecutor.decide(workflow.getWorkflowId());

        assertTrue(refs(workflow).contains("b"));
        assertEquals(TaskModel.Status.SCHEDULED, workflow.getTaskByRefName("b").getStatus());
        assertFalse(refs(workflow).contains("after"));
    }

    @Test
    public void testSkippingAForkSkipsItsJoinAndTheFlowGoesOnAfterIt() {
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType(TaskType.FORK_JOIN.name());
        fork.setForkTasks(List.of(List.of(simpleEntry("a")), List.of(simpleEntry("b"))));
        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setTaskReferenceName("join");
        join.setType(TaskType.JOIN.name());
        join.setJoinOn(List.of("a", "b"));
        WorkflowModel workflow =
                running("skip-fork", simpleEntry("pre"), fork, join, simpleEntry("after"));
        TaskModel pre = ran(simpleEntry("pre"), "pre", 1, TaskModel.Status.IN_PROGRESS);
        pre.setExecuted(false);
        pre.setStartTime(System.currentTimeMillis());
        pre.setUpdateTime(System.currentTimeMillis());
        pre.setEndTime(0);
        workflow.getTasks().add(pre);

        workflowExecutor.skipTaskFromWorkflow(workflow.getWorkflowId(), "fork", null);

        org.mockito.ArgumentCaptor<List<TaskModel>> created =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(executionDAOFacade).createTasks(created.capture());
        assertEquals(
                List.of("fork", "join"),
                created.getValue().stream().map(TaskModel::getReferenceTaskName).toList());
        assertTrue(
                created.getValue().stream()
                        .allMatch(t -> t.getStatus() == TaskModel.Status.SKIPPED));

        // once pre finishes, the flow goes on after the JOIN, not to a JOIN waiting alone
        workflow.getTasks().addAll(created.getValue());
        pre.setStatus(TaskModel.Status.COMPLETED);
        workflowExecutor.decide(workflow.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(List.of("pre", "fork", "join", "after"), refs(workflow));
    }

    @Test
    public void testATerminateThatRanEndsTheWorkflowAsItSaysAfterARetry() {
        WorkflowTask terminate = new WorkflowTask();
        terminate.setName("stop");
        terminate.setTaskReferenceName("stop");
        terminate.setType(TaskType.TERMINATE.name());
        WorkflowTask joinEntry = new WorkflowTask();
        joinEntry.setName("join");
        joinEntry.setTaskReferenceName("join");
        joinEntry.setType(TaskType.JOIN.name());
        WorkflowModel workflow = running("terminate-retry", terminate, joinEntry);
        TaskModel stop = ran(terminate, "stop", 1, TaskModel.Status.COMPLETED);
        stop.setTaskType(TaskType.TASK_TYPE_TERMINATE);
        stop.getInputData().put("terminationStatus", "FAILED");
        workflow.getTasks().add(stop);
        // what a retry reopens: the JOIN the TERMINATE cancelled, judged again
        TaskModel join = ran(joinEntry, "join", 2, TaskModel.Status.CANCELED);
        join.setTaskType(TaskType.TASK_TYPE_JOIN);
        join.setExecuted(false);
        workflow.getTasks().add(join);

        workflowExecutor.decide(workflow.getWorkflowId());

        // DECIDER_BUGS item 6: the TERMINATE decides, not the cancelled JOIN
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
    }

    @Test
    public void testACompletedWorkflowCannotBeRetried() {
        // a TERMINATE with status COMPLETED ended it and cancelled the other branch's task
        WorkflowTask entry = simpleEntry("t");
        WorkflowModel workflow = running("completed-retry", entry);
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        TaskModel cancelled = ran(entry, "t", 1, TaskModel.Status.CANCELED);
        workflow.getTasks().add(cancelled);

        // DECIDER_BUGS item 11
        assertThrows(
                ConflictException.class,
                () -> workflowExecutor.retry(workflow.getWorkflowId(), false));
        assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
        assertEquals(List.of(cancelled), workflow.getTasks());
        verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
    }

    @Test
    public void testAWorkflowThatAlreadyEndedCannotBeTerminated() {
        // DECIDER_BUGS item 10
        for (WorkflowModel.Status ended :
                List.of(WorkflowModel.Status.FAILED, WorkflowModel.Status.TIMED_OUT)) {
            WorkflowModel workflow = running("ended-" + ended, simpleEntry("t"));
            workflow.setStatus(ended);
            workflow.setReasonForIncompletion("card declined");

            assertThrows(
                    ConflictException.class,
                    () -> workflowExecutor.terminateWorkflow(workflow.getWorkflowId(), "cleanup"));
            assertEquals(ended, workflow.getStatus());
            assertEquals("card declined", workflow.getReasonForIncompletion());
        }
        verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
    }

    @Test
    public void testAnOperatorRetryGivesTheTaskAFreshTotalTimeBudget() {
        WorkflowTask entry = simpleEntry("t");
        WorkflowModel workflow = running("retry-budget", entry);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        TaskModel failed = ran(entry, "t", 1, TaskModel.Status.FAILED);
        failed.setFirstScheduledTime(5);
        workflow.getTasks().add(failed);
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(workflow.getWorkflowDefinition()));

        workflowExecutor.retry(workflow.getWorkflowId(), false);

        TaskModel attempt =
                workflow.getTasks().stream()
                        .filter(t -> !t.isRetried() && t != failed)
                        .findFirst()
                        .orElseThrow();
        // DECIDER_BUGS item 12
        assertTrue(attempt.getFirstScheduledTime() > 1_000);
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
    public void testUpdateParentWorkflowTaskDropsSupersededGeneration() {
        String parentWorkflowTaskId = "superseded_task_id";
        String childId = "child_workflow_id";
        String parentId = "parent_workflow_id";

        WorkflowModel subWorkflow = new WorkflowModel();
        subWorkflow.setWorkflowId(childId);
        subWorkflow.setParentWorkflowTaskId(parentWorkflowTaskId);
        subWorkflow.setStatus(WorkflowModel.Status.FAILED);

        TaskModel staleTask = new TaskModel();
        staleTask.setTaskId(parentWorkflowTaskId);
        staleTask.setSubWorkflowId(childId);
        staleTask.setWorkflowInstanceId(parentId);
        staleTask.setStatus(TaskModel.Status.IN_PROGRESS);

        // The parent's current task list does NOT contain the stale task (a rerun replaced the
        // fork generation) — the late child failure must be dropped, not propagated.
        TaskModel freshTask = new TaskModel();
        freshTask.setTaskId("fresh_task_id");
        freshTask.setWorkflowInstanceId(parentId);
        freshTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowModel parentWorkflow = new WorkflowModel();
        parentWorkflow.setWorkflowId(parentId);
        parentWorkflow.setStatus(WorkflowModel.Status.RUNNING);
        parentWorkflow.getTasks().add(freshTask);

        when(executionDAOFacade.getTaskModel(parentWorkflowTaskId)).thenReturn(staleTask);
        when(executionDAOFacade.getWorkflowModel(parentId, true)).thenReturn(parentWorkflow);

        workflowExecutor.updateParentWorkflowTask(subWorkflow);

        verify(executionDAOFacade, never()).updateTask(any(TaskModel.class));
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
        subWorkflowTask.setSubWorkflowId("child-workflow-id");
        subWorkflowTask.setStatus(TaskModel.Status.IN_PROGRESS);
        WorkflowModel childWorkflow = new WorkflowModel();
        childWorkflow.setWorkflowDefinition(new WorkflowDef());
        when(executionDAOFacade.getWorkflowModel("child-workflow-id", true))
                .thenReturn(childWorkflow);

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
        verify(workflowStatusListener, times(2))
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
    public void testScheduleTaskQueuesAsyncSubWorkflowWithoutInlineStart() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("parent-workflow");
        workflow.setWorkflowDefinition(new WorkflowDef());

        TaskModel subWorkflowTask = new TaskModel();
        subWorkflowTask.setTaskId("sub-workflow-task");
        subWorkflowTask.setTaskType(TaskType.SUB_WORKFLOW.name());
        subWorkflowTask.setTaskDefName(TaskType.SUB_WORKFLOW.name());
        subWorkflowTask.setReferenceTaskName("sub_workflow_ref");
        subWorkflowTask.setWorkflowInstanceId(workflow.getWorkflowId());
        subWorkflowTask.setStatus(TaskModel.Status.SCHEDULED);
        subWorkflowTask.setInputData(new HashMap<>());

        boolean stateChanged =
                workflowExecutor.scheduleTask(workflow, Collections.singletonList(subWorkflowTask));

        assertFalse(stateChanged);
        verify(executionDAOFacade).createTasks(Collections.singletonList(subWorkflowTask));
        verify(queueDAO)
                .push(
                        eq(TaskType.SUB_WORKFLOW.name()),
                        eq(subWorkflowTask.getTaskId()),
                        eq(subWorkflowTask.getWorkflowPriority()),
                        eq(0L));
        verify(executionDAOFacade, never()).updateTask(subWorkflowTask);
    }

    @Test
    public void testResumeWorkflow() {
        when(executionLockService.acquireLock(anyString(), anyLong())).thenReturn(true);
        doNothing().when(executionLockService).releaseLock(anyString());

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

        // if workflow is already RUNNING (e.g. a racing resume call already resumed it)
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        when(executionDAOFacade.getWorkflowModel(workflowId, false)).thenReturn(workflow);
        workflowExecutor.resumeWorkflow(workflowId);
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        verify(executionDAOFacade, never()).updateWorkflow(any(WorkflowModel.class));
        verify(queueDAO, never()).push(anyString(), anyString(), anyInt(), anyLong());

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
        // Given a workflow with a failure compensation definition
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

        // When applying "decide" to terminate workflow
        workflowExecutor.decide(workflow.getWorkflowId());

        // Then should assert workflow properties
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
        assertTrue(workflow.getOutput().containsKey("conductor.failure_workflow"));
        assertNotNull(workflow.getFailedTaskId());
        assertTrue(!workflow.getFailedReferenceTaskNames().isEmpty());

        // And verify that the failure workflow definition was fetched without version
        verify(metadataDAO).getLatestWorkflowDef("failure_workflow");
        assertNull(workflow.getWorkflowDefinition().getFailureWorkflowVersion());

        // And the failure workflow input carries failedWorkflow as a Map, not a raw
        // WorkflowModel POJO, so nested ${workflow.input.failedWorkflow.<field>} references
        // are resolvable by JsonPath (issue #1164)
        ArgumentCaptor<WorkflowModel> failureWorkflowCaptor =
                ArgumentCaptor.forClass(WorkflowModel.class);
        verify(executionDAOFacade, atLeastOnce()).createWorkflow(failureWorkflowCaptor.capture());
        Object failedWorkflowInput =
                failureWorkflowCaptor.getValue().getInput().get("failedWorkflow");
        assertTrue(
                "failedWorkflow input must be a Map, was: " + failedWorkflowInput.getClass(),
                failedWorkflowInput instanceof Map);
        assertEquals("1", ((Map<String, Object>) failedWorkflowInput).get("workflowId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTerminateWorkflowWithCustomFailureWorkflowVersion() {
        // Given a workflow with a failure compensation definition
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("workflow");
        workflowDef.setFailureWorkflow("failure_workflow_with_custom_version");
        workflowDef.setFailureWorkflowVersion(10);

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
        failureWorkflowDef.setName("failure_workflow_with_custom_version");
        failureWorkflowDef.setVersion(10);

        when(metadataDAO.getWorkflowDef(
                        failureWorkflowDef.getName(), failureWorkflowDef.getVersion()))
                .thenReturn(Optional.of(failureWorkflowDef));

        when(executionDAOFacade.getWorkflowModel(workflow.getWorkflowId(), true))
                .thenReturn(workflow);
        when(executionLockService.acquireLock(anyString())).thenReturn(true);

        // When applying "decide" to terminate workflow
        workflowExecutor.decide(workflow.getWorkflowId());

        // Then should assert workflow properties
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
        assertTrue(workflow.getOutput().containsKey("conductor.failure_workflow"));
        assertNotNull(workflow.getFailedTaskId());
        assertTrue(!workflow.getFailedReferenceTaskNames().isEmpty());

        // And verify that the failure workflow definition was fetched with a custom version
        verify(metadataDAO).getWorkflowDef("failure_workflow_with_custom_version", 10);

        assertEquals(
                workflowDef.getFailureWorkflowVersion(),
                workflow.getWorkflowDefinition().getFailureWorkflowVersion());
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
        verify(executionDAOFacade, times(1)).updateTaskIfStatus(argumentCaptor.capture(), any());
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
        verify(executionDAOFacade, times(1)).updateTaskIfStatus(argumentCaptor.capture(), any());
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
        // Dynamic tasks (not in static def, e.g. FORK_JOIN_DYNAMIC forked tasks) now always
        // trigger decide to avoid workflow stalls caused by the sweeper's 30+ second delay.
        task.setReferenceTaskName("dynamic");
        assertFalse(workflowExecutor.isLazyEvaluateWorkflow(workflowDef, task));

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
        // noinspection unchecked
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

    private TaskModel switchTaskModel(WorkflowModel workflow, String ref, String evaluatorType) {
        WorkflowTask wt = new WorkflowTask();
        wt.setType(TASK_TYPE_SWITCH);
        wt.setName(ref);
        wt.setTaskReferenceName(ref);
        wt.setEvaluatorType(evaluatorType);
        wt.setExpression("route");
        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_SWITCH);
        task.setTaskDefName(TASK_TYPE_SWITCH);
        task.setReferenceTaskName(ref);
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setTaskId(new IDGenerator().generate());
        task.setWorkflowTask(wt);
        return task;
    }

    /** SWITCH evaluation happens in its mapper, so it is never put on a system-task queue. */
    @Test
    public void testDecisionSwitchIsNeverQueued() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("sw-decision");
        WorkflowDef def = new WorkflowDef();
        def.setName("sw");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        TaskModel regular = switchTaskModel(workflow, "r1", "decision");
        regular.setStatus(TaskModel.Status.IN_PROGRESS); // as SwitchTaskMapper produces it

        workflowExecutor.scheduleTask(workflow, List.of(regular));

        verify(queueDAO, never()).push(eq(TASK_TYPE_SWITCH), anyString(), anyInt(), anyLong());
        verify(queueDAO, never()).push(eq(TASK_TYPE_SWITCH), anyString(), anyLong());
        assertEquals(TaskModel.Status.IN_PROGRESS, regular.getStatus());
    }
}
