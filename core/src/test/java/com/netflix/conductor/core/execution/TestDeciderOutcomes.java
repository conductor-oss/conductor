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

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.unit.DataSize;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.HTTPTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.SwitchTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.tasks.Decision;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.Switch;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.DECISION;
import static com.netflix.conductor.common.metadata.tasks.TaskType.DYNAMIC;
import static com.netflix.conductor.common.metadata.tasks.TaskType.EVENT;
import static com.netflix.conductor.common.metadata.tasks.TaskType.FORK_JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.FORK_JOIN_DYNAMIC;
import static com.netflix.conductor.common.metadata.tasks.TaskType.HTTP;
import static com.netflix.conductor.common.metadata.tasks.TaskType.JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.SIMPLE;
import static com.netflix.conductor.common.metadata.tasks.TaskType.SUB_WORKFLOW;
import static com.netflix.conductor.common.metadata.tasks.TaskType.SWITCH;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DECISION;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SWITCH;
import static com.netflix.conductor.common.metadata.tasks.TaskType.USER_DEFINED;
import static com.netflix.conductor.common.metadata.tasks.TaskType.WAIT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            TestDeciderOutcomes.TestConfiguration.class
        })
@RunWith(SpringRunner.class)
public class TestDeciderOutcomes {

    private DeciderService deciderService;

    private NativeContinuationService nativeContinuationService;

    @Autowired private Map<String, Evaluator> evaluators;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private SystemTaskRegistry systemTaskRegistry;

    @Configuration
    @ComponentScan(basePackageClasses = {Evaluator.class}) // load all Evaluator beans.
    public static class TestConfiguration {

        @Bean(TASK_TYPE_DECISION)
        public Decision decision() {
            return new Decision();
        }

        @Bean(TASK_TYPE_SWITCH)
        public Switch switchTask() {
            return new Switch();
        }

        @Bean(TASK_TYPE_JOIN)
        public Join join() {
            return new Join(new ConductorProperties());
        }

        @Bean
        public SystemTaskRegistry systemTaskRegistry(Set<WorkflowSystemTask> tasks) {
            return new SystemTaskRegistry(tasks);
        }
    }

    @Before
    public void init() {
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);

        ExternalPayloadStorageUtils externalPayloadStorageUtils =
                mock(ExternalPayloadStorageUtils.class);
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getTaskInputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10L));
        when(properties.getMaxTaskInputPayloadSizeThreshold())
                .thenReturn(DataSize.ofKilobytes(10240L));

        TaskDef taskDef = new TaskDef();
        taskDef.setRetryCount(1);
        taskDef.setName("mockTaskDef");
        taskDef.setResponseTimeoutSeconds(60 * 60);
        when(metadataDAO.getTaskDef(anyString())).thenReturn(taskDef);
        ParametersUtils parametersUtils = new ParametersUtils(objectMapper);
        Map<String, TaskMapper> taskMappers = new HashMap<>();
        taskMappers.put(DECISION.name(), new DecisionTaskMapper());
        taskMappers.put(SWITCH.name(), new SwitchTaskMapper(evaluators));
        taskMappers.put(DYNAMIC.name(), new DynamicTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(FORK_JOIN.name(), new ForkJoinTaskMapper());
        taskMappers.put(JOIN.name(), new JoinTaskMapper());
        taskMappers.put(
                FORK_JOIN_DYNAMIC.name(),
                new ForkJoinDynamicTaskMapper(
                        new IDGenerator(),
                        parametersUtils,
                        objectMapper,
                        metadataDAO,
                        systemTaskRegistry));
        taskMappers.put(
                USER_DEFINED.name(), new UserDefinedTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(SIMPLE.name(), new SimpleTaskMapper(parametersUtils));
        taskMappers.put(
                SUB_WORKFLOW.name(), new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(EVENT.name(), new EventTaskMapper(parametersUtils));
        taskMappers.put(WAIT.name(), new WaitTaskMapper(parametersUtils));
        taskMappers.put(HTTP.name(), new HTTPTaskMapper(parametersUtils, metadataDAO));
        taskMappers.put(
                TaskType.TERMINATE.name(),
                new com.netflix.conductor.core.execution.mapper.TerminateTaskMapper(
                        parametersUtils));

        this.nativeContinuationService =
                new NativeContinuationService(
                        objectMapper,
                        new ConductorProperties(),
                        new MetadataMapperService(metadataDAO),
                        taskMappers);
        this.deciderService =
                new DeciderService(
                        new IDGenerator(),
                        parametersUtils,
                        metadataDAO,
                        externalPayloadStorageUtils,
                        systemTaskRegistry,
                        nativeContinuationService,
                        taskMappers,
                        new HashMap<>(),
                        Duration.ofMinutes(60));
    }

    @Test
    public void testWorkflowWithNoTasks() throws Exception {
        InputStream stream = new ClassPathResource("./conditional_flow.json").getInputStream();
        WorkflowDef def = objectMapper.readValue(stream, WorkflowDef.class);
        assertNotNull(def);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(0L);
        workflow.getInput().put("param1", "nested");
        workflow.getInput().put("param2", "one");

        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertFalse(outcome.isComplete);
        assertTrue(outcome.tasksToBeUpdated.isEmpty());
        assertEquals(3, outcome.tasksToBeScheduled.size());

        outcome.tasksToBeScheduled.forEach(t -> t.setStatus(TaskModel.Status.COMPLETED));
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        outcome = deciderService.decide(workflow);
        assertFalse(outcome.isComplete);
        assertEquals(outcome.tasksToBeUpdated.toString(), 3, outcome.tasksToBeUpdated.size());
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals("DECISION", outcome.tasksToBeScheduled.get(0).getTaskDefName());
    }

    @Test
    public void testWorkflowWithNoTasksWithSwitch() throws Exception {
        InputStream stream =
                new ClassPathResource("./conditional_flow_with_switch.json").getInputStream();
        WorkflowDef def = objectMapper.readValue(stream, WorkflowDef.class);
        assertNotNull(def);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(0L);
        workflow.getInput().put("param1", "nested");
        workflow.getInput().put("param2", "one");

        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertFalse(outcome.isComplete);
        assertTrue(outcome.tasksToBeUpdated.isEmpty());
        assertEquals(3, outcome.tasksToBeScheduled.size());

        outcome.tasksToBeScheduled.forEach(t -> t.setStatus(TaskModel.Status.COMPLETED));
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        outcome = deciderService.decide(workflow);
        assertFalse(outcome.isComplete);
        assertEquals(outcome.tasksToBeUpdated.toString(), 3, outcome.tasksToBeUpdated.size());
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals("SWITCH", outcome.tasksToBeScheduled.get(0).getTaskDefName());
    }

    @Test
    public void testRetries() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("test_task");
        workflowTask.setType("USER_TASK");
        workflowTask.setTaskReferenceName("t0");
        workflowTask.getInputParameters().put("taskId", "${CPEWF_TASK_ID}");
        workflowTask.getInputParameters().put("requestId", "${workflow.input.requestId}");
        workflowTask.setTaskDefinition(new TaskDef("test_task"));

        def.getTasks().add(workflowTask);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", 123);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(
                workflowTask.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());

        String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();
        assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));
        assertEquals(123, outcome.tasksToBeScheduled.get(0).getInputData().get("requestId"));

        outcome.tasksToBeScheduled.get(0).setStatus(TaskModel.Status.FAILED);
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);

        outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        assertEquals(1, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
        assertNotSame(task1Id, outcome.tasksToBeScheduled.get(0).getTaskId());
        assertEquals(
                outcome.tasksToBeScheduled.get(0).getTaskId(),
                outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));
        assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getRetriedTaskId());
        assertEquals(123, outcome.tasksToBeScheduled.get(0).getInputData().get("requestId"));

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork0");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN_DYNAMIC);
        fork.setTaskReferenceName("fork0");
        fork.setDynamicForkTasksInputParamName("forkedInputs");
        fork.setDynamicForkTasksParam("forks");
        fork.getInputParameters().put("forks", "${workflow.input.forks}");
        fork.getInputParameters().put("forkedInputs", "${workflow.input.forkedInputs}");

        WorkflowTask join = new WorkflowTask();
        join.setName("join0");
        join.setType("JOIN");
        join.setTaskReferenceName("join0");

        def.getTasks().clear();
        def.getTasks().add(fork);
        def.getTasks().add(join);

        List<WorkflowTask> forks = new LinkedList<>();
        Map<String, Map<String, Object>> forkedInputs = new HashMap<>();

        for (int i = 0; i < 1; i++) {
            WorkflowTask wft = new WorkflowTask();
            wft.setName("f" + i);
            wft.setTaskReferenceName("f" + i);
            wft.setWorkflowTaskType(TaskType.SIMPLE);
            wft.getInputParameters().put("requestId", "${workflow.input.requestId}");
            wft.getInputParameters().put("taskId", "${CPEWF_TASK_ID}");
            wft.setTaskDefinition(new TaskDef("f" + i));
            forks.add(wft);
            Map<String, Object> input = new HashMap<>();
            input.put("k", "v");
            input.put("k1", 1);
            forkedInputs.put(wft.getTaskReferenceName(), input);
        }
        workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", 123);
        workflow.setCreateTime(System.currentTimeMillis());

        workflow.getInput().put("forks", forks);
        workflow.getInput().put("forkedInputs", forkedInputs);

        outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(3, outcome.tasksToBeScheduled.size());
        assertEquals(0, outcome.tasksToBeUpdated.size());

        assertEquals("v", outcome.tasksToBeScheduled.get(1).getInputData().get("k"));
        assertEquals(1, outcome.tasksToBeScheduled.get(1).getInputData().get("k1"));
        assertEquals(
                outcome.tasksToBeScheduled.get(1).getTaskId(),
                outcome.tasksToBeScheduled.get(1).getInputData().get("taskId"));
        task1Id = outcome.tasksToBeScheduled.get(1).getTaskId();

        outcome.tasksToBeScheduled.get(1).setStatus(TaskModel.Status.FAILED);
        for (TaskModel taskToBeScheduled : outcome.tasksToBeScheduled) {
            taskToBeScheduled.setUpdateTime(System.currentTimeMillis());
        }
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);

        outcome = deciderService.decide(workflow);
        assertTrue(
                outcome.tasksToBeScheduled.stream()
                        .anyMatch(task1 -> task1.getReferenceTaskName().equals("f0")));

        Optional<TaskModel> optionalTask =
                outcome.tasksToBeScheduled.stream()
                        .filter(t -> t.getReferenceTaskName().equals("f0"))
                        .findFirst();
        assertTrue(optionalTask.isPresent());
        TaskModel task = optionalTask.get();
        assertEquals("v", task.getInputData().get("k"));
        assertEquals(1, task.getInputData().get("k1"));
        assertEquals(task.getTaskId(), task.getInputData().get("taskId"));
        assertNotSame(task1Id, task.getTaskId());
        assertEquals(task1Id, task.getRetriedTaskId());
    }

    /**
     * The failure matrix: for each terminal status, with and without optional, in a standard and in
     * a native workflow, whether the task is retried, what status it ends with, and whether the
     * workflow ends or continues to the next task.
     */
    private static final class Outcome {
        boolean retried;
        boolean terminated;
        TaskModel.Status finalStatus;
        Object failedStatus;
        String next;
    }

    private Outcome failOnce(boolean nativeWf, boolean optional, TaskModel.Status status) {
        WorkflowDef def = new WorkflowDef();
        def.setName("matrix");
        def.setSchemaVersion(2);
        if (nativeWf) {
            def.getMetadata().put("native", Map.of("sdk", "test"));
        }
        TaskDef retries = new TaskDef("t0def");
        retries.setRetryCount(1);
        WorkflowTask t0 = new WorkflowTask();
        t0.setName("t0def");
        t0.setType("SIMPLE");
        t0.setTaskReferenceName("t0");
        t0.setOptional(optional);
        t0.setTaskDefinition(retries);
        WorkflowTask t1 = new WorkflowTask();
        t1.setName("t1def");
        t1.setType("SIMPLE");
        t1.setTaskReferenceName("t1");
        t1.setTaskDefinition(new TaskDef("t1def"));
        def.getTasks().add(t0);
        def.getTasks().add(t1);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        TaskModel task = workflow.getTasks().get(0);
        task.setStatus(status);

        Outcome result = new Outcome();
        try {
            outcome = deciderService.decide(workflow);
        } catch (com.netflix.conductor.core.exception.TerminateWorkflowException e) {
            result.terminated = true;
            result.finalStatus = task.getStatus();
            result.failedStatus = task.getOutputData().get("conductor.failedStatus");
            return result;
        }
        result.retried =
                outcome.tasksToBeScheduled.stream()
                        .anyMatch(t -> "t0".equals(t.getReferenceTaskName()));
        if (result.retried) {
            // exhaust the retry with the same status
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            task = workflow.getTasks().get(workflow.getTasks().size() - 1);
            task.setStatus(status);
            try {
                outcome = deciderService.decide(workflow);
            } catch (com.netflix.conductor.core.exception.TerminateWorkflowException e) {
                result.terminated = true;
                result.finalStatus = task.getStatus();
                result.failedStatus = task.getOutputData().get("conductor.failedStatus");
                return result;
            }
        }
        result.finalStatus = task.getStatus();
        result.failedStatus = task.getOutputData().get("conductor.failedStatus");
        result.next =
                outcome.tasksToBeScheduled.isEmpty()
                        ? null
                        : outcome.tasksToBeScheduled.get(0).getReferenceTaskName();
        return result;
    }

    @Test
    public void failureMatrixForStandardWorkflowsIsUnchanged() {
        // not optional: retried if retriable, then the workflow ends
        Outcome failed = failOnce(false, false, TaskModel.Status.FAILED);
        assertTrue(failed.retried);
        assertTrue(failed.terminated);
        Outcome terminal = failOnce(false, false, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertFalse(terminal.retried);
        assertTrue(terminal.terminated);
        // optional: retried if retriable, then COMPLETED_WITH_ERRORS and the next task runs
        Outcome optFailed = failOnce(false, true, TaskModel.Status.FAILED);
        assertTrue(optFailed.retried);
        assertFalse(optFailed.terminated);
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, optFailed.finalStatus);
        assertEquals("t1", optFailed.next);
        Outcome optTerminal = failOnce(false, true, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertFalse(optTerminal.retried);
        assertFalse(optTerminal.terminated);
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, optTerminal.finalStatus);
    }

    @Test
    public void failureMatrixForNativeWorkflows() {
        // native tasks are optional: retried if retriable, then COMPLETED_WITH_ERRORS, with how it
        // failed in the output, and the next task (the continuation, which raises TaskFailure) runs
        for (TaskModel.Status status :
                List.of(TaskModel.Status.FAILED, TaskModel.Status.TIMED_OUT)) {
            Outcome o = failOnce(true, true, status);
            assertTrue(status + " retried", o.retried);
            assertFalse(o.terminated);
            assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, o.finalStatus);
            assertEquals(status.name(), o.failedStatus);
            assertEquals("t1", o.next);
        }
        Outcome terminal = failOnce(true, true, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertFalse("terminal errors are not retried", terminal.retried);
        assertFalse(terminal.terminated);
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, terminal.finalStatus);
        assertEquals("FAILED_WITH_TERMINAL_ERROR", terminal.failedStatus);
        assertEquals("t1", terminal.next);
        // a non-optional task in a native workflow (a CODE task) still ends the workflow
        Outcome code = failOnce(true, false, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertFalse(code.retried);
        assertTrue(code.terminated);
    }

    @Test
    public void nativeWorkflowRetriesAnOptionalTaskThenCompletesItWithErrors() {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-test");
        def.getMetadata().put("native", Map.of("sdk", "test"));

        TaskDef retryTwice = new TaskDef("task0");
        retryTwice.setRetryCount(2);
        WorkflowTask task1 = new WorkflowTask();
        task1.setName("task0");
        task1.setType("SIMPLE");
        task1.setTaskReferenceName("t0");
        task1.setOptional(true);
        task1.setTaskDefinition(retryTwice);

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("task1");
        task2.setType("SIMPLE");
        task2.setTaskReferenceName("t1");
        task2.setTaskDefinition(new TaskDef("task1"));

        def.getTasks().add(task1);
        def.getTasks().add(task2);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);

        // two retries, like any task
        for (int i = 0; i < 2; i++) {
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            workflow.getTasks()
                    .get(workflow.getTasks().size() - 1)
                    .setStatus(TaskModel.Status.FAILED);
            outcome = deciderService.decide(workflow);
            assertEquals("t0", outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
            assertEquals(i + 1, outcome.tasksToBeScheduled.get(0).getRetryCount());
        }

        // retries exhausted: the next task is scheduled, and the task completes with errors
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        TaskModel last = workflow.getTasks().get(workflow.getTasks().size() - 1);
        last.setStatus(TaskModel.Status.FAILED);
        outcome = deciderService.decide(workflow);
        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, last.getStatus());
        assertEquals("FAILED", last.getOutputData().get("conductor.failedStatus"));
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals("t1", outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertFalse(outcome.isComplete);
    }

    /** A native execution with one finished task the code paused on. */
    private WorkflowModel nativeWorkflowPausedOn(TaskModel.Status taskStatus) {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-test");
        def.setSchemaVersion(2);
        def.getMetadata().put("native", Map.of("protocol", 2, "codeType", "native-test.v1.code"));
        WorkflowTask entry = new WorkflowTask();
        entry.setName("task0");
        entry.setType("SIMPLE");
        entry.setTaskReferenceName("t0");
        entry.setOptional(true);
        entry.getMetadata().put("lane", "main");
        entry.getMetadata().put("continuation", Map.of("lane", "main", "n", 2));
        entry.setTaskDefinition(new TaskDef("task0"));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-native");
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        nativeContinuationService.initialize(workflow); // starts the execution's copy empty
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        def.getTasks().add(entry); // ... which the bootstrap continuation then grew by one entry

        TaskModel task = new TaskModel();
        task.setTaskId("t0-id");
        task.setReferenceTaskName("t0");
        task.setTaskType("task0");
        task.setTaskDefName("task0");
        task.setWorkflowTask(entry);
        task.setStatus(taskStatus);
        task.setContinuation(new TaskContinuation("main", 2));
        workflow.getTasks().add(task);
        return workflow;
    }

    @Test
    public void nativeWorkflowWithNoTasksYetIsNotCompleteAndSchedulesNothing() {
        WorkflowDef def = new WorkflowDef();
        def.setName("native-empty");
        def.setSchemaVersion(2);
        def.getMetadata().put("native", Map.of("protocol", 2));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-empty");
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        nativeContinuationService.initialize(workflow);

        DeciderOutcome outcome = deciderService.decide(workflow);

        assertFalse(outcome.isComplete); // the bootstrap continuation appends the first tasks
        assertTrue(outcome.tasksToBeScheduled.isEmpty());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void evaluatingTheTaskTheCodePausedOnWakesItsContinuation() {
        WorkflowModel workflow = nativeWorkflowPausedOn(TaskModel.Status.COMPLETED);
        TaskModel task = workflow.getTasks().get(0);

        DeciderOutcome outcome = deciderService.decide(workflow);

        assertTrue(task.isExecuted());
        assertTrue(outcome.tasksToBeUpdated.contains(task));
        assertFalse(outcome.isComplete); // every task is terminal, but the code has not returned
        assertTrue(outcome.tasksToBeScheduled.isEmpty());
        // the executor wakes it in the same decide
        assertEquals(
                List.of(task.getTaskId()),
                deciderService
                        .getNativeContinuationService()
                        .repairAndWake(workflow)
                        .offers
                        .stream()
                        .map(NativeContinuationService.Offer::messageId)
                        .toList());
        assertTrue(task.getContinuation().isLeased());
    }

    @Test
    public void aNativeWorkflowCompletesOnlyAfterWhatItsLastContinuationAppendedHasRun() {
        // the code returned (marker done) and appended one more task after the task it paused on
        WorkflowModel workflow = nativeWorkflowPausedOn(TaskModel.Status.COMPLETED);
        TaskModel task = workflow.getTasks().get(0);
        task.getContinuation().setState(TaskContinuation.APPLIED);
        task.getContinuation().setDone(true);
        task.getContinuation().setResult(Map.of("answer", 42));
        WorkflowTask trailing = new WorkflowTask();
        trailing.setName("task1");
        trailing.setType("SIMPLE");
        trailing.setTaskReferenceName("t1");
        trailing.getMetadata().put("lane", "main");
        trailing.setTaskDefinition(new TaskDef("task1"));
        workflow.getWorkflowDefinition().getTasks().add(trailing);
        task.setExecuted(false); // the completion re-evaluates the task the code paused on

        DeciderOutcome outcome = deciderService.decide(workflow);

        assertFalse(outcome.isComplete); // t1 still has to run
        assertEquals(
                List.of("t1"),
                outcome.tasksToBeScheduled.stream().map(TaskModel::getReferenceTaskName).toList());

        // t1 finished: now the workflow completes, with the code's return value as its output
        TaskModel t1 = outcome.tasksToBeScheduled.get(0);
        t1.setStatus(TaskModel.Status.COMPLETED);
        workflow.getTasks().add(t1);
        outcome = deciderService.decide(workflow);
        assertTrue(outcome.isComplete);
        assertEquals(Map.of("answer", 42), workflow.getOutput());
    }

    @Test
    public void aRetriedAttemptCarriesAFreshMarker() {
        WorkflowModel workflow = nativeWorkflowPausedOn(TaskModel.Status.FAILED);
        workflow.getTasks().get(0).getWorkflowTask().getTaskDefinition().setRetryCount(1);

        DeciderOutcome outcome = deciderService.decide(workflow);

        TaskModel retry =
                outcome.tasksToBeScheduled.stream()
                        .filter(t -> "t0".equals(t.getReferenceTaskName()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, retry.getRetryCount());
        assertEquals("main", retry.getContinuation().getLane());
        assertEquals(2, retry.getContinuation().getN());
        assertTrue(retry.getContinuation().isWaiting());
    }

    @Test
    public void testOptional() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowTask task1 = new WorkflowTask();
        task1.setName("task0");
        task1.setType("SIMPLE");
        task1.setTaskReferenceName("t0");
        task1.getInputParameters().put("taskId", "${CPEWF_TASK_ID}");
        task1.setOptional(true);
        task1.setTaskDefinition(new TaskDef("task0"));

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("task1");
        task2.setType("SIMPLE");
        task2.setTaskReferenceName("t1");
        task2.setTaskDefinition(new TaskDef("task1"));

        def.getTasks().add(task1);
        def.getTasks().add(task2);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(
                task1.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());

        for (int i = 0; i < 3; i++) {
            String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();
            assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));

            workflow.getTasks().clear();
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            workflow.getTasks().get(0).setStatus(TaskModel.Status.FAILED);

            outcome = deciderService.decide(workflow);

            assertNotNull(outcome);
            assertEquals(1, outcome.tasksToBeUpdated.size());
            assertEquals(1, outcome.tasksToBeScheduled.size());

            assertEquals(TaskModel.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
            assertEquals(
                    task1.getTaskReferenceName(),
                    outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
            assertEquals(i + 1, outcome.tasksToBeScheduled.get(0).getRetryCount());
        }

        String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();

        workflow.getTasks().clear();
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        workflow.getTasks().get(0).setStatus(TaskModel.Status.FAILED);

        outcome = deciderService.decide(workflow);

        assertNotNull(outcome);
        assertEquals(1, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());

        assertEquals(
                TaskModel.Status.COMPLETED_WITH_ERRORS, workflow.getTasks().get(0).getStatus());
        assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
        assertEquals(
                task2.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
    }

    @Test
    public void testPermissive() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test-permissive");

        WorkflowTask task1 = new WorkflowTask();
        task1.setName("task0");
        task1.setPermissive(true);
        task1.setTaskReferenceName("t0");
        task1.getInputParameters().put("taskId", "${CPEWF_TASK_ID}");
        task1.setTaskDefinition(new TaskDef("task0"));

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("task1");
        task2.setPermissive(true);
        task2.setTaskReferenceName("t1");
        task2.setTaskDefinition(new TaskDef("task1"));

        def.getTasks().add(task1);
        def.getTasks().add(task2);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(
                task1.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());

        for (int i = 0; i < 3; i++) {
            String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();
            assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));

            workflow.getTasks().clear();
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            workflow.getTasks().get(0).setStatus(TaskModel.Status.FAILED);

            outcome = deciderService.decide(workflow);

            assertNotNull(outcome);
            assertEquals(1, outcome.tasksToBeUpdated.size());
            assertEquals(1, outcome.tasksToBeScheduled.size());

            assertEquals(TaskModel.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
            assertEquals(
                    task1.getTaskReferenceName(),
                    outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
            assertEquals(i + 1, outcome.tasksToBeScheduled.get(0).getRetryCount());
        }

        String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();

        workflow.getTasks().clear();
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        workflow.getTasks().get(0).setStatus(TaskModel.Status.FAILED);

        outcome = deciderService.decide(workflow);

        assertNotNull(outcome);
        assertEquals(1, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());

        assertEquals(TaskModel.Status.FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
        assertEquals(
                task2.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
    }

    @Test
    public void testOptionalWithDynamicFork() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowTask task1 = new WorkflowTask();
        task1.setName("fork0");
        task1.setWorkflowTaskType(TaskType.FORK_JOIN_DYNAMIC);
        task1.setTaskReferenceName("fork0");
        task1.setDynamicForkTasksInputParamName("forkedInputs");
        task1.setDynamicForkTasksParam("forks");
        task1.getInputParameters().put("forks", "${workflow.input.forks}");
        task1.getInputParameters().put("forkedInputs", "${workflow.input.forkedInputs}");

        WorkflowTask task2 = new WorkflowTask();
        task2.setName("join0");
        task2.setType("JOIN");
        task2.setTaskReferenceName("join0");

        def.getTasks().add(task1);
        def.getTasks().add(task2);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        List<WorkflowTask> forks = new LinkedList<>();
        Map<String, Map<String, Object>> forkedInputs = new HashMap<>();

        for (int i = 0; i < 3; i++) {
            WorkflowTask workflowTask = new WorkflowTask();
            workflowTask.setName("f" + i);
            workflowTask.getInputParameters().put("joinOn", new ArrayList<>());
            workflowTask.setTaskReferenceName("f" + i);
            workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
            workflowTask.setOptional(true);
            workflowTask.setTaskDefinition(new TaskDef("f" + i));
            forks.add(workflowTask);

            forkedInputs.put(workflowTask.getTaskReferenceName(), new HashMap<>());
        }
        workflow.getInput().put("forks", forks);
        workflow.getInput().put("forkedInputs", forkedInputs);

        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(5, outcome.tasksToBeScheduled.size());
        assertEquals(0, outcome.tasksToBeUpdated.size());
        assertEquals(TASK_TYPE_FORK, outcome.tasksToBeScheduled.get(0).getTaskType());
        assertEquals(TaskModel.Status.COMPLETED, outcome.tasksToBeScheduled.get(0).getStatus());

        for (int retryCount = 0; retryCount < 3; retryCount++) {

            for (TaskModel taskToBeScheduled : outcome.tasksToBeScheduled) {
                if (taskToBeScheduled.getTaskDefName().equals("join0")) {
                    assertEquals(TaskModel.Status.IN_PROGRESS, taskToBeScheduled.getStatus());
                } else if (taskToBeScheduled.getTaskType().matches("(f0|f1|f2)")) {
                    assertEquals(TaskModel.Status.SCHEDULED, taskToBeScheduled.getStatus());
                    taskToBeScheduled.setStatus(TaskModel.Status.FAILED);
                }

                taskToBeScheduled.setUpdateTime(System.currentTimeMillis());
            }
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            outcome = deciderService.decide(workflow);
            assertNotNull(outcome);
        }
        assertEquals("f0", outcome.tasksToBeScheduled.get(0).getTaskType());

        for (int i = 0; i < 3; i++) {
            assertEquals(TaskModel.Status.FAILED, outcome.tasksToBeUpdated.get(i).getStatus());
            assertEquals("f" + (i), outcome.tasksToBeUpdated.get(i).getTaskDefName());
        }

        assertEquals(TaskModel.Status.SCHEDULED, outcome.tasksToBeScheduled.get(0).getStatus());
        System.out.println(outcome.tasksToBeScheduled.get(0));
        new Join(new ConductorProperties())
                .execute(workflow, outcome.tasksToBeScheduled.get(0), null);
        assertEquals(TaskModel.Status.COMPLETED, outcome.tasksToBeScheduled.get(0).getStatus());
    }

    @Test
    public void testDecisionCases() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test");

        WorkflowTask even = new WorkflowTask();
        even.setName("even");
        even.setType("SIMPLE");
        even.setTaskReferenceName("even");
        even.setTaskDefinition(new TaskDef("even"));

        WorkflowTask odd = new WorkflowTask();
        odd.setName("odd");
        odd.setType("SIMPLE");
        odd.setTaskReferenceName("odd");
        odd.setTaskDefinition(new TaskDef("odd"));

        WorkflowTask defaultt = new WorkflowTask();
        defaultt.setName("defaultt");
        defaultt.setType("SIMPLE");
        defaultt.setTaskReferenceName("defaultt");
        defaultt.setTaskDefinition(new TaskDef("defaultt"));

        WorkflowTask decide = new WorkflowTask();
        decide.setName("decide");
        decide.setWorkflowTaskType(TaskType.DECISION);
        decide.setTaskReferenceName("d0");
        decide.getInputParameters().put("Id", "${workflow.input.Id}");
        decide.getInputParameters().put("location", "${workflow.input.location}");
        decide.setCaseExpression(
                "if ($.Id == null) 'bad input'; else if ( ($.Id != null && $.Id % 2 == 0) || $.location == 'usa') 'even'; else 'odd'; ");

        decide.getDecisionCases().put("even", Collections.singletonList(even));
        decide.getDecisionCases().put("odd", Collections.singletonList(odd));
        decide.setDefaultCase(Collections.singletonList(defaultt));

        def.getTasks().add(decide);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(
                decide.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(
                defaultt.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(1).getReferenceTaskName()); // default
        assertEquals(
                Collections.singletonList("bad input"),
                outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));

        workflow.getInput().put("Id", 9);
        workflow.getInput().put("location", "usa");
        outcome = deciderService.decide(workflow);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(
                decide.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(
                even.getTaskReferenceName(),
                outcome.tasksToBeScheduled
                        .get(1)
                        .getReferenceTaskName()); // even because of location == usa
        assertEquals(
                Collections.singletonList("even"),
                outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));

        workflow.getInput().put("Id", 9);
        workflow.getInput().put("location", "canada");
        outcome = deciderService.decide(workflow);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(
                decide.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(
                odd.getTaskReferenceName(),
                outcome.tasksToBeScheduled.get(1).getReferenceTaskName()); // odd
        assertEquals(
                Collections.singletonList("odd"),
                outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));
    }
}
