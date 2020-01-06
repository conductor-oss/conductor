/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.HTTPTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.MetadataDAO;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Viren
 *
 */
public class TestDeciderOutcomes {

    private DeciderService deciderService;

    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    @Before
    public void init() {
        MetadataDAO metadataDAO = mock(MetadataDAO.class);

        ExternalPayloadStorageUtils externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        Configuration configuration = mock(Configuration.class);
        when(configuration.getTaskInputPayloadSizeThresholdKB()).thenReturn(10L);
        when(configuration.getMaxTaskInputPayloadSizeThresholdKB()).thenReturn(10240L);

        TaskDef taskDef = new TaskDef();
        taskDef.setRetryCount(1);
        taskDef.setName("mockTaskDef");
        taskDef.setResponseTimeoutSeconds(60 * 60);
        when(metadataDAO.getTaskDef(anyString())).thenReturn(taskDef);
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
        taskMappers.put("HTTP", new HTTPTaskMapper(parametersUtils, metadataDAO));

        this.deciderService = new DeciderService(parametersUtils, metadataDAO,  externalPayloadStorageUtils, taskMappers, configuration);
    }

    @Test
    public void testWorkflowWithNoTasks() throws Exception {
        InputStream stream = TestDeciderOutcomes.class.getResourceAsStream("/conditional_flow.json");
        WorkflowDef def = objectMapper.readValue(stream, WorkflowDef.class);
        assertNotNull(def);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setStartTime(0);
        workflow.getInput().put("param1", "nested");
        workflow.getInput().put("param2", "one");

        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertFalse(outcome.isComplete);
        assertTrue(outcome.tasksToBeUpdated.isEmpty());
        assertEquals(3, outcome.tasksToBeScheduled.size());
        System.out.println(outcome.tasksToBeScheduled);

        outcome.tasksToBeScheduled.forEach(t -> t.setStatus(Status.COMPLETED));
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        outcome = deciderService.decide(workflow);
        assertFalse(outcome.isComplete);
        assertEquals(outcome.tasksToBeUpdated.toString(), 3, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals("junit_task_3", outcome.tasksToBeScheduled.get(0).getTaskDefName());
        System.out.println(outcome.tasksToBeScheduled);
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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", 123);
        workflow.setStartTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(workflowTask.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());

        String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();
        assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));
        assertEquals(123, outcome.tasksToBeScheduled.get(0).getInputData().get("requestId"));

        outcome.tasksToBeScheduled.get(0).setStatus(Status.FAILED);
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);

        outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        assertEquals(1, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
        assertNotSame(task1Id, outcome.tasksToBeScheduled.get(0).getTaskId());
        assertEquals(outcome.tasksToBeScheduled.get(0).getTaskId(), outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));
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
        workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", 123);
        workflow.setStartTime(System.currentTimeMillis());

        workflow.getInput().put("forks", forks);
        workflow.getInput().put("forkedInputs", forkedInputs);

        outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(3, outcome.tasksToBeScheduled.size());
        assertEquals(0, outcome.tasksToBeUpdated.size());

        assertEquals("v", outcome.tasksToBeScheduled.get(1).getInputData().get("k"));
        assertEquals(1, outcome.tasksToBeScheduled.get(1).getInputData().get("k1"));
        assertEquals(outcome.tasksToBeScheduled.get(1).getTaskId(), outcome.tasksToBeScheduled.get(1).getInputData().get("taskId"));
        System.out.println(outcome.tasksToBeScheduled.get(1).getInputData());
        task1Id = outcome.tasksToBeScheduled.get(1).getTaskId();

        outcome.tasksToBeScheduled.get(1).setStatus(Status.FAILED);
        for(Task taskToBeScheduled : outcome.tasksToBeScheduled) {
            taskToBeScheduled.setUpdateTime(System.currentTimeMillis());
        }
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);

        outcome = deciderService.decide(workflow);
        assertTrue(outcome.tasksToBeScheduled.stream().anyMatch(task1 -> task1.getReferenceTaskName().equals("f0")));

        //noinspection ConstantConditions
        Task task1 = outcome.tasksToBeScheduled.stream().filter(t -> t.getReferenceTaskName().equals("f0")).findFirst().get();
        assertEquals("v", task1.getInputData().get("k"));
        assertEquals(1, task1.getInputData().get("k1"));
        assertEquals(task1.getTaskId(), task1.getInputData().get("taskId"));
        assertNotSame(task1Id, task1.getTaskId());
        assertEquals(task1Id, task1.getRetriedTaskId());
        System.out.println(task1.getInputData());

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


        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setStartTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        System.out.println("Schedule after starting: " + outcome.tasksToBeScheduled);
        assertEquals(1, outcome.tasksToBeScheduled.size());
        assertEquals(task1.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        System.out.println("TaskId of the scheduled task in input: " + outcome.tasksToBeScheduled.get(0).getInputData());

        for (int i = 0; i < 3; i++) {
            String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();
            assertEquals(task1Id, outcome.tasksToBeScheduled.get(0).getInputData().get("taskId"));

            workflow.getTasks().clear();
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);
            workflow.getTasks().get(0).setStatus(Status.FAILED);

            outcome = deciderService.decide(workflow);

            assertNotNull(outcome);
            System.out.println("Schedule: " + outcome.tasksToBeScheduled);
            System.out.println("Update: " + outcome.tasksToBeUpdated);

            assertEquals(1, outcome.tasksToBeUpdated.size());
            assertEquals(1, outcome.tasksToBeScheduled.size());

            assertEquals(Task.Status.FAILED, workflow.getTasks().get(0).getStatus());
            assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
            assertEquals(task1.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
            assertEquals(i + 1, outcome.tasksToBeScheduled.get(0).getRetryCount());
        }

        String task1Id = outcome.tasksToBeScheduled.get(0).getTaskId();

        workflow.getTasks().clear();
        workflow.getTasks().addAll(outcome.tasksToBeScheduled);
        workflow.getTasks().get(0).setStatus(Status.FAILED);

        outcome = deciderService.decide(workflow);

        assertNotNull(outcome);
        System.out.println("Schedule: " + outcome.tasksToBeScheduled);
        System.out.println("Update: " + outcome.tasksToBeUpdated);

        assertEquals(1, outcome.tasksToBeUpdated.size());
        assertEquals(1, outcome.tasksToBeScheduled.size());

        assertEquals(Task.Status.COMPLETED_WITH_ERRORS, workflow.getTasks().get(0).getStatus());
        assertEquals(task1Id, outcome.tasksToBeUpdated.get(0).getTaskId());
        assertEquals(task2.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());

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


        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        List<WorkflowTask> forks = new LinkedList<>();
        Map<String, Map<String, Object>> forkedInputs = new HashMap<>();

        for (int i = 0; i < 3; i++) {
            WorkflowTask workflowTask = new WorkflowTask();
            workflowTask.setName("f" + i);
            workflowTask.setTaskReferenceName("f" + i);
            workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
            workflowTask.setOptional(true);
            workflowTask.setTaskDefinition(new TaskDef("f" + i));
            forks.add(workflowTask);

            forkedInputs.put(workflowTask.getTaskReferenceName(), new HashMap<>());
        }
        workflow.getInput().put("forks", forks);
        workflow.getInput().put("forkedInputs", forkedInputs);


        workflow.setStartTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);
        assertEquals(5, outcome.tasksToBeScheduled.size());
        assertEquals(0, outcome.tasksToBeUpdated.size());

        assertEquals(SystemTaskType.FORK.name(), outcome.tasksToBeScheduled.get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, outcome.tasksToBeScheduled.get(0).getStatus());

        for (int retryCount = 0; retryCount < 4; retryCount++) {

            for (Task taskToBeScheduled : outcome.tasksToBeScheduled) {
                if (taskToBeScheduled.getTaskDefName().equals("join0")) {
                    assertEquals(Task.Status.IN_PROGRESS, taskToBeScheduled.getStatus());
                } else if (taskToBeScheduled.getTaskType().matches("(f0|f1|f2)")) {
                    assertEquals(Task.Status.SCHEDULED, taskToBeScheduled.getStatus());
                    taskToBeScheduled.setStatus(Status.FAILED);
                }

                taskToBeScheduled.setUpdateTime(System.currentTimeMillis());
            }
            workflow.getTasks().addAll(outcome.tasksToBeScheduled);


            outcome = deciderService.decide(workflow);
            assertNotNull(outcome);
        }

        assertEquals(SystemTaskType.JOIN.name(), outcome.tasksToBeScheduled.get(0).getTaskType());

        for (int i = 0; i < 3; i++) {
            assertEquals(Task.Status.COMPLETED_WITH_ERRORS, outcome.tasksToBeUpdated.get(i).getStatus());
            assertEquals("f" + (i), outcome.tasksToBeUpdated.get(i).getTaskDefName());
        }

        assertEquals(Task.Status.IN_PROGRESS, outcome.tasksToBeScheduled.get(0).getStatus());
        new Join().execute(workflow, outcome.tasksToBeScheduled.get(0), null);
        assertEquals(Task.Status.COMPLETED, outcome.tasksToBeScheduled.get(0).getStatus());

        outcome.tasksToBeScheduled.stream().map(task -> task.getStatus() + ":" + task.getTaskType() + ":").forEach(System.out::println);
        outcome.tasksToBeUpdated.stream().map(task -> task.getStatus() + ":" + task.getTaskType() + ":").forEach(System.out::println);
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
        decide.setCaseExpression("if ($.Id == null) 'bad input'; else if ( ($.Id != null && $.Id % 2 == 0) || $.location == 'usa') 'even'; else 'odd'; ");

        decide.getDecisionCases().put("even", Arrays.asList(even));
        decide.getDecisionCases().put("odd", Arrays.asList(odd));
        decide.setDefaultCase(Arrays.asList(defaultt));

        def.getTasks().add(decide);
        def.setSchemaVersion(2);


        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setStartTime(System.currentTimeMillis());
        DeciderOutcome outcome = deciderService.decide(workflow);
        assertNotNull(outcome);

        System.out.println("Schedule after starting: " + outcome.tasksToBeScheduled);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(decide.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(defaultt.getTaskReferenceName(), outcome.tasksToBeScheduled.get(1).getReferenceTaskName());        //default
        System.out.println(outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));
        assertEquals(Arrays.asList("bad input"), outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));

        workflow.getInput().put("Id", 9);
        workflow.getInput().put("location", "usa");
        outcome = deciderService.decide(workflow);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(decide.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(even.getTaskReferenceName(), outcome.tasksToBeScheduled.get(1).getReferenceTaskName());        //even because of location == usa
        assertEquals(Arrays.asList("even"), outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));

        workflow.getInput().put("Id", 9);
        workflow.getInput().put("location", "canada");
        outcome = deciderService.decide(workflow);
        assertEquals(2, outcome.tasksToBeScheduled.size());
        assertEquals(decide.getTaskReferenceName(), outcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertEquals(odd.getTaskReferenceName(), outcome.tasksToBeScheduled.get(1).getReferenceTaskName());            //odd
        assertEquals(Arrays.asList("odd"), outcome.tasksToBeScheduled.get(0).getOutputData().get("caseOutput"));
    }
}
