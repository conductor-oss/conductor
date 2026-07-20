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
package com.netflix.conductor.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.ClaimedSystemTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.secrets.RuntimeMetadataResolver;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class ExecutionServiceTest {

    @Mock private WorkflowExecutor workflowExecutor;
    @Mock private ExecutionDAOFacade executionDAOFacade;
    @Mock private QueueDAO queueDAO;
    @Mock private ConductorProperties conductorProperties;
    @Mock private ExternalPayloadStorage externalPayloadStorage;
    @Mock private SystemTaskRegistry systemTaskRegistry;
    @Mock private TaskStatusListener taskStatusListener;
    @Mock private ParametersUtils parametersUtils;
    @Mock private RuntimeMetadataResolver runtimeMetadataResolver;

    private ExecutionService executionService;

    private Workflow workflow1;
    private Workflow workflow2;
    private Task taskWorkflow1;
    private Task taskWorkflow2;
    private final List<String> sort = Collections.singletonList("Sort");

    @Before
    public void setup() {
        when(conductorProperties.getTaskExecutionPostponeDuration())
                .thenReturn(Duration.ofSeconds(60));
        when(parametersUtils.substituteSecrets(any())).thenAnswer(inv -> inv.getArgument(0));
        executionService =
                new ExecutionService(
                        workflowExecutor,
                        executionDAOFacade,
                        queueDAO,
                        conductorProperties,
                        externalPayloadStorage,
                        systemTaskRegistry,
                        taskStatusListener,
                        parametersUtils,
                        runtimeMetadataResolver);
        WorkflowDef workflowDef = new WorkflowDef();
        workflow1 = new Workflow();
        workflow1.setWorkflowId("wf1");
        workflow1.setWorkflowDefinition(workflowDef);
        workflow2 = new Workflow();
        workflow2.setWorkflowId("wf2");
        workflow2.setWorkflowDefinition(workflowDef);
        taskWorkflow1 = new Task();
        taskWorkflow1.setTaskId("task1");
        taskWorkflow1.setWorkflowInstanceId("wf1");
        taskWorkflow2 = new Task();
        taskWorkflow2.setTaskId("task2");
        taskWorkflow2.setWorkflowInstanceId("wf2");
    }

    @Test
    public void workflowSearchTest() {
        when(executionDAOFacade.searchWorkflowSummary("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        new WorkflowSummary(workflow1),
                                        new WorkflowSummary(workflow2))));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<WorkflowSummary> searchResult =
                executionService.search("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
        assertEquals(workflow2.getWorkflowId(), searchResult.getResults().get(1).getWorkflowId());
    }

    @Test
    public void workflowSearchV2Test() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<Workflow> searchResult = executionService.searchV2("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(workflow1, workflow2), searchResult.getResults());
    }

    @Test
    public void workflowSearchV2ExceptionTest() {
        when(executionDAOFacade.searchWorkflows("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        workflow1.getWorkflowId(), workflow2.getWorkflowId())));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenThrow(new RuntimeException());
        SearchResult<Workflow> searchResult = executionService.searchV2("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow1), searchResult.getResults());
    }

    @Test
    public void workflowSearchByTasksTest() {
        when(executionDAOFacade.searchTaskSummary("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        new TaskSummary(taskWorkflow1),
                                        new TaskSummary(taskWorkflow2))));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<WorkflowSummary> searchResult =
                executionService.searchWorkflowByTasks("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
        assertEquals(workflow2.getWorkflowId(), searchResult.getResults().get(1).getWorkflowId());
    }

    @Test
    public void workflowSearchByTasksExceptionTest() {
        when(executionDAOFacade.searchTaskSummary("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        new TaskSummary(taskWorkflow1),
                                        new TaskSummary(taskWorkflow2))));
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getTask(workflow2.getWorkflowId()))
                .thenThrow(new RuntimeException());
        SearchResult<WorkflowSummary> searchResult =
                executionService.searchWorkflowByTasks("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(1, searchResult.getResults().size());
        assertEquals(workflow1.getWorkflowId(), searchResult.getResults().get(0).getWorkflowId());
    }

    @Test
    public void workflowSearchByTasksV2Test() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        when(executionDAOFacade.getWorkflow(workflow2.getWorkflowId(), false))
                .thenReturn(workflow2);
        SearchResult<Workflow> searchResult =
                executionService.searchWorkflowByTasksV2("query", "*", 0, 2, sort);
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(workflow1, workflow2), searchResult.getResults());
    }

    @Test
    public void workflowSearchByTasksV2ExceptionTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        when(executionDAOFacade.getWorkflow(workflow1.getWorkflowId(), false))
                .thenReturn(workflow1);
        SearchResult<Workflow> searchResult =
                executionService.searchWorkflowByTasksV2("query", "*", 0, 2, sort);
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow1), searchResult.getResults());
    }

    @Test
    public void TaskSearchTest() {
        List<TaskSummary> taskList =
                Arrays.asList(new TaskSummary(taskWorkflow1), new TaskSummary(taskWorkflow2));
        when(executionDAOFacade.searchTaskSummary("query", "*", 0, 2, sort))
                .thenReturn(new SearchResult<>(2, taskList));
        SearchResult<TaskSummary> searchResult =
                executionService.getSearchTasks("query", "*", 0, 2, "Sort");
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(2, searchResult.getResults().size());
        assertEquals(taskWorkflow1.getTaskId(), searchResult.getResults().get(0).getTaskId());
        assertEquals(taskWorkflow2.getTaskId(), searchResult.getResults().get(1).getTaskId());
    }

    @Test
    public void TaskSearchV2Test() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId())).thenReturn(taskWorkflow2);
        SearchResult<Task> searchResult =
                executionService.getSearchTasksV2("query", "*", 0, 2, "Sort");
        assertEquals(2, searchResult.getTotalHits());
        assertEquals(Arrays.asList(taskWorkflow1, taskWorkflow2), searchResult.getResults());
    }

    @Test
    public void TaskSearchV2ExceptionTest() {
        when(executionDAOFacade.searchTasks("query", "*", 0, 2, sort))
                .thenReturn(
                        new SearchResult<>(
                                2,
                                Arrays.asList(
                                        taskWorkflow1.getTaskId(), taskWorkflow2.getTaskId())));
        when(executionDAOFacade.getTask(taskWorkflow1.getTaskId())).thenReturn(taskWorkflow1);
        when(executionDAOFacade.getTask(taskWorkflow2.getTaskId()))
                .thenThrow(new RuntimeException());
        SearchResult<Task> searchResult =
                executionService.getSearchTasksV2("query", "*", 0, 2, "Sort");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(taskWorkflow1), searchResult.getResults());
    }

    @Test
    public void testGetLastPollTaskAcksOnlyOnce() {
        // Setup: create a TaskModel that poll() will process
        String taskType = "test_task";
        String workerId = "worker1";
        String domain = null;
        String taskId = "task-123";
        String queueName = taskType; // QueueUtils.getQueueName with null domain = taskType

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId(taskId);
        taskModel.setTaskType(taskType);
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        taskModel.setWorkflowInstanceId("wf-123");

        // Mock: queueDAO.pop returns the task ID
        when(queueDAO.pop(eq(queueName), eq(1), anyInt()))
                .thenReturn(Collections.singletonList(taskId));

        // Mock: executionDAOFacade returns the TaskModel (called twice: once in poll loop,
        // once in the taskStatusListener notification block)
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);
        when(executionDAOFacade.exceedsInProgressLimit(taskModel)).thenReturn(false);

        // Mock: ack returns true (standard behavior for most QueueDAO implementations)
        when(queueDAO.ack(eq(queueName), eq(taskId))).thenReturn(true);

        // Act
        Task result = executionService.getLastPollTask(taskType, workerId, domain);

        // Assert: task was returned
        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());

        // Assert: queueDAO.ack was called exactly ONCE (inside poll()), not twice.
        // This is the core assertion — before the fix, ack was called twice:
        // once in poll() via tasks.forEach(this::ackTaskReceived), and again
        // redundantly in getLastPollTask() after poll() returned.
        verify(queueDAO, times(1)).ack(queueName, taskId);
    }

    /*
     * When a worker polls a task (SCHEDULED -> IN_PROGRESS), adjustDeciderQueuePostpone() moves the
     * workflow's DECIDER_QUEUE re-evaluation out to responseTimeoutSeconds via setUnackTimeoutIfShorter.
     * This is normally harmless (task completion fires an expedited decide), but it is why a missed
     * post-completion decide leaves the workflow parked for responseTimeoutSeconds — the multi-minute
     * pause that the decide() re-queue fix addresses.
     */
    @Test
    public void testPollPostponesDeciderQueueToResponseTimeout() {
        String taskType = "simple_task";
        String workerId = "worker1";
        String domain = null;
        String taskId = "task-123";
        String queueName = taskType;
        String workflowId = "wf-123";

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId(taskId);
        taskModel.setTaskType(taskType);
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        taskModel.setWorkflowInstanceId(workflowId);
        taskModel.setResponseTimeoutSeconds(600);

        when(queueDAO.pop(eq(queueName), eq(1), anyInt()))
                .thenReturn(Collections.singletonList(taskId));
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);
        when(executionDAOFacade.exceedsInProgressLimit(taskModel)).thenReturn(false);
        when(queueDAO.ack(eq(queueName), eq(taskId))).thenReturn(true);

        executionService.poll(taskType, workerId, domain, 1, 100);

        verify(queueDAO).setUnackTimeoutIfShorter(DECIDER_QUEUE, workflowId, 600L * 1000);
    }

    @Test
    public void testGetLastPollTaskReturnsNullWhenEmpty() {
        String taskType = "test_task";
        String workerId = "worker1";
        String domain = null;
        String queueName = taskType;

        // Mock: queueDAO.pop returns empty list (no tasks available)
        when(queueDAO.pop(eq(queueName), eq(1), anyInt())).thenReturn(Collections.emptyList());

        // Act
        Task result = executionService.getLastPollTask(taskType, workerId, domain);

        // Assert: null returned, no ack called
        assertNull(result);
        verify(queueDAO, times(0)).ack(anyString(), anyString());
    }

    @Test
    public void testPollSubstitutesSecretsOnOutgoingTask() {
        String taskType = "t";
        String taskId = "task-1";
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId(taskId);
        taskModel.setTaskType(taskType);
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        Map<String, Object> literal = new HashMap<>();
        literal.put("pwd", "${workflow.secrets.DB_PASSWORD}");
        taskModel.setInputData(literal);

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of(taskId));
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);
        Map<String, Object> resolved = new HashMap<>();
        resolved.put("pwd", "s3cr3t");
        when(parametersUtils.substituteSecrets(any())).thenReturn(resolved);

        List<Task> polled = executionService.poll(taskType, "worker", null, 1, 100);

        assertEquals(1, polled.size());
        assertEquals("s3cr3t", polled.get(0).getInputData().get("pwd"));
        // persisted model keeps the literal
        assertEquals("${workflow.secrets.DB_PASSWORD}", taskModel.getInputData().get("pwd"));
    }

    @Test
    public void testPollInjectsDeclaredValuesOntoOutgoingTask() {
        String taskType = "t";
        String taskId = "task-1";

        TaskDef taskDef = new TaskDef();
        taskDef.setRuntimeMetadata(List.of("API_KEY"));
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId(taskId);
        taskModel.setTaskType(taskType);
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        taskModel.setWorkflowTask(workflowTask);

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of(taskId));
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);
        when(runtimeMetadataResolver.resolve(List.of("API_KEY")))
                .thenReturn(Map.of("API_KEY", "token-value-123"));

        List<Task> polled = executionService.poll(taskType, "worker", null, 1, 100);

        assertEquals(1, polled.size());
        Task returnedTask = polled.get(0);
        assertEquals("token-value-123", returnedTask.getRuntimeMetadata().get("API_KEY"));
        verify(runtimeMetadataResolver).resolve(List.of("API_KEY"));
    }

    @Test
    public void testPollDeliversTaskWhenResolutionThrows() {
        String taskType = "t";
        String taskId = "task-1";

        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId(taskId);
        taskModel.setTaskType(taskType);
        taskModel.setStatus(TaskModel.Status.SCHEDULED);

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of(taskId));
        when(executionDAOFacade.getTaskModel(taskId)).thenReturn(taskModel);
        when(parametersUtils.substituteSecrets(any()))
                .thenThrow(new RuntimeException("resolution failed"));

        List<Task> polled = executionService.poll(taskType, "worker", null, 1, 100);

        // The task is still delivered (resolution error is isolated) — not stranded in a
        // re-enqueue loop, and its resolved values are simply absent.
        assertEquals(1, polled.size());
        assertEquals(taskId, polled.get(0).getTaskId());
        assertNull(polled.get(0).getRuntimeMetadata().get("API_KEY"));
        // the outer catch's re-enqueue (postpone) is NOT triggered
        verify(queueDAO, times(0)).postpone(anyString(), eq(taskId), anyInt(), anyLong());
    }

    @Test
    public void testClaimSystemTasksClaimsScheduledTask() {
        when(conductorProperties.getSystemTaskClaimLeaseDuration())
                .thenReturn(Duration.ofSeconds(3600));
        String queueName = "LLM_CHAT_COMPLETE";
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("t1");
        taskModel.setTaskType(queueName);
        taskModel.setTaskDefName(queueName);
        taskModel.setWorkflowInstanceId("wf1");
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        when(queueDAO.pop(queueName, 1, 100)).thenReturn(List.of("t1"));
        when(executionDAOFacade.getTaskModel("t1")).thenReturn(taskModel);

        List<ClaimedSystemTask> claimed = executionService.claimSystemTasks(queueName, 1, 100);

        assertEquals(1, claimed.size());
        assertEquals(TaskModel.Status.SCHEDULED, claimed.get(0).preClaimStatus());
        assertEquals(TaskModel.Status.IN_PROGRESS, taskModel.getStatus());
        assertEquals(3600L, taskModel.getCallbackAfterSeconds());
        assertEquals(1, taskModel.getPollCount());
        assertNotNull(taskModel.getWorkerId());
        assertNotEquals(0L, taskModel.getStartTime());
        // secrets must NOT be substituted at claim time — the executor substitutes with a
        // literal-input restore; substituting here would persist resolved values
        verify(parametersUtils, times(0)).substituteSecrets(any());
        verify(executionDAOFacade).updateTask(taskModel);
        // the lease replaces the ACK: message postponed by the lease, never removed
        verify(queueDAO).postpone(queueName, "t1", 0, 3600L);
        verify(queueDAO, times(0)).remove(anyString(), anyString());
        verify(taskStatusListener).onTaskInProgress(taskModel);
    }

    @Test
    public void testClaimSystemTasksPostponesInFlightTaskWithoutClaiming() {
        String queueName = "LLM_CHAT_COMPLETE";
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("t1");
        taskModel.setTaskType(queueName);
        taskModel.setWorkflowInstanceId("wf1");
        taskModel.setStatus(TaskModel.Status.IN_PROGRESS);
        taskModel.setCallbackAfterSeconds(600);
        taskModel.setUpdateTime(System.currentTimeMillis());
        when(queueDAO.pop(queueName, 1, 100)).thenReturn(List.of("t1"));
        when(executionDAOFacade.getTaskModel("t1")).thenReturn(taskModel);

        List<ClaimedSystemTask> claimed = executionService.claimSystemTasks(queueName, 1, 100);

        // live lease → early redelivery: renew the remaining lease, do not claim
        assertEquals(0, claimed.size());
        assertEquals(600L, taskModel.getCallbackAfterSeconds());
        verify(executionDAOFacade, times(0)).updateTask(any(TaskModel.class));
        ArgumentCaptor<Long> postponed = ArgumentCaptor.forClass(Long.class);
        verify(queueDAO).postpone(eq(queueName), eq("t1"), eq(0), postponed.capture());
        assertTrue(postponed.getValue() >= 1 && postponed.getValue() <= 600);
    }

    @Test
    public void testClaimSystemTasksReclaimsTaskWithExpiredLease() {
        when(conductorProperties.getSystemTaskClaimLeaseDuration())
                .thenReturn(Duration.ofSeconds(3600));
        String queueName = "LLM_CHAT_COMPLETE";
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("t1");
        taskModel.setTaskType(queueName);
        taskModel.setWorkflowInstanceId("wf1");
        taskModel.setStatus(TaskModel.Status.IN_PROGRESS);
        taskModel.setCallbackAfterSeconds(600);
        // lease expired long ago: crash recovery or a due multi-turn callback
        taskModel.setUpdateTime(System.currentTimeMillis() - 700_000L);
        taskModel.setStartTime(123L);
        when(queueDAO.pop(queueName, 1, 100)).thenReturn(List.of("t1"));
        when(executionDAOFacade.getTaskModel("t1")).thenReturn(taskModel);

        List<ClaimedSystemTask> claimed = executionService.claimSystemTasks(queueName, 1, 100);

        assertEquals(1, claimed.size());
        assertEquals(TaskModel.Status.IN_PROGRESS, claimed.get(0).preClaimStatus());
        assertEquals(3600L, taskModel.getCallbackAfterSeconds());
        assertEquals(123L, taskModel.getStartTime());
        verify(executionDAOFacade).updateTask(taskModel);
        verify(queueDAO).postpone(queueName, "t1", 0, 3600L);
    }

    @Test
    public void testClaimSystemTasksUsesResponseTimeoutAsLease() {
        String queueName = "LLM_CHAT_COMPLETE";
        TaskDef taskDef = new TaskDef(queueName);
        taskDef.setResponseTimeoutSeconds(900);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskDefinition(taskDef);
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("t1");
        taskModel.setTaskType(queueName);
        taskModel.setWorkflowInstanceId("wf1");
        taskModel.setStatus(TaskModel.Status.SCHEDULED);
        taskModel.setWorkflowTask(workflowTask);
        when(queueDAO.pop(queueName, 1, 100)).thenReturn(List.of("t1"));
        when(executionDAOFacade.getTaskModel("t1")).thenReturn(taskModel);

        List<ClaimedSystemTask> claimed = executionService.claimSystemTasks(queueName, 1, 100);

        assertEquals(1, claimed.size());
        assertEquals(900L, taskModel.getCallbackAfterSeconds());
        verify(queueDAO).postpone(queueName, "t1", 0, 900L);
        verify(conductorProperties, times(0)).getSystemTaskClaimLeaseDuration();
    }

    @Test
    public void testClaimSystemTasksRemovesTerminalTask() {
        String queueName = "LLM_CHAT_COMPLETE";
        TaskModel taskModel = new TaskModel();
        taskModel.setTaskId("t1");
        taskModel.setStatus(TaskModel.Status.COMPLETED);
        when(queueDAO.pop(queueName, 1, 100)).thenReturn(List.of("t1"));
        when(executionDAOFacade.getTaskModel("t1")).thenReturn(taskModel);

        List<ClaimedSystemTask> claimed = executionService.claimSystemTasks(queueName, 1, 100);

        assertEquals(0, claimed.size());
        verify(queueDAO).remove(queueName, "t1");
        verify(executionDAOFacade, times(0)).updateTask(any(TaskModel.class));
    }
}
