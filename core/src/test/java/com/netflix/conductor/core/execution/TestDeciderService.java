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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(
        classes = {TestObjectMapperConfiguration.class, TestDeciderService.TestConfiguration.class})
@RunWith(SpringRunner.class)
public class TestDeciderService {

    @Configuration
    @ComponentScan(basePackageClasses = TaskMapper.class) // loads all TaskMapper beans
    public static class TestConfiguration {

        @Bean(TASK_TYPE_SUB_WORKFLOW)
        public SubWorkflow subWorkflow(ObjectMapper objectMapper) {
            return new SubWorkflow(objectMapper);
        }

        @Bean("asyncCompleteSystemTask")
        public WorkflowSystemTaskStub asyncCompleteSystemTask() {
            return new WorkflowSystemTaskStub("asyncCompleteSystemTask") {
                @Override
                public boolean isAsyncComplete(TaskModel task) {
                    return true;
                }
            };
        }

        @Bean
        public SystemTaskRegistry systemTaskRegistry(Set<WorkflowSystemTask> tasks) {
            return new SystemTaskRegistry(tasks);
        }

        @Bean
        public MetadataDAO mockMetadataDAO() {
            return mock(MetadataDAO.class);
        }

        @Bean
        public Map<TaskType, TaskMapper> taskMapperMap(Collection<TaskMapper> taskMappers) {
            return taskMappers.stream()
                    .collect(Collectors.toMap(TaskMapper::getTaskType, Function.identity()));
        }

        @Bean
        public ParametersUtils parametersUtils(ObjectMapper mapper) {
            return new ParametersUtils(mapper);
        }
    }

    private DeciderService deciderService;

    private ExternalPayloadStorageUtils externalPayloadStorageUtils;
    private static Registry registry;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private SystemTaskRegistry systemTaskRegistry;

    @Autowired
    @Qualifier("taskMapperMap")
    private Map<TaskType, TaskMapper> taskMappers;

    @Autowired private ParametersUtils parametersUtils;

    @Autowired private MetadataDAO metadataDAO;

    @Rule public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void init() {
        registry = new DefaultRegistry();
        Spectator.globalRegistry().add(registry);
    }

    @Before
    public void setup() {
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("TestDeciderService");
        workflowDef.setVersion(1);
        TaskDef taskDef = new TaskDef();
        when(metadataDAO.getTaskDef(any())).thenReturn(taskDef);
        when(metadataDAO.getLatestWorkflowDef(any())).thenReturn(Optional.of(workflowDef));

        deciderService =
                new DeciderService(
                        parametersUtils,
                        metadataDAO,
                        externalPayloadStorageUtils,
                        systemTaskRegistry,
                        taskMappers,
                        Duration.ofMinutes(60));
    }

    @Test
    public void testGetTaskInputV2() {
        WorkflowModel workflow = createDefaultWorkflow();

        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("workflowInputParam", "${workflow.input.requestId}");
        inputParams.put("taskOutputParam", "${task2.output.location}");
        inputParams.put("taskOutputParam2", "${task2.output.locationBad}");
        inputParams.put("taskOutputParam3", "${task3.output.location}");
        inputParams.put("constParam", "Some String value");
        inputParams.put("nullValue", null);
        inputParams.put("task2Status", "${task2.status}");
        inputParams.put("channelMap", "${workflow.input.channelMapping}");
        Map<String, Object> taskInput =
                parametersUtils.getTaskInput(inputParams, workflow, null, null);

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("workflowInputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam2"));
        assertTrue(taskInput.containsKey("taskOutputParam3"));
        assertNull(taskInput.get("taskOutputParam2"));

        assertNotNull(taskInput.get("channelMap"));
        assertEquals(5, taskInput.get("channelMap"));

        assertEquals("request id 001", taskInput.get("workflowInputParam"));
        assertEquals("http://location", taskInput.get("taskOutputParam"));
        assertNull(taskInput.get("taskOutputParam3"));
        assertNull(taskInput.get("nullValue"));
        assertEquals(
                workflow.getTasks().get(0).getStatus().name(),
                taskInput.get("task2Status")); // task2 and task3 are the tasks respectively
    }

    @Test
    public void testGetTaskInputV2Partial() {
        WorkflowModel workflow = createDefaultWorkflow();
        System.setProperty("EC2_INSTANCE", "i-123abcdef990");
        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("workflowInputParam", "${workflow.input.requestId}");
        inputParams.put("workfowOutputParam", "${workflow.output.name}");
        inputParams.put("taskOutputParam", "${task2.output.location}");
        inputParams.put("taskOutputParam2", "${task2.output.locationBad}");
        inputParams.put("taskOutputParam3", "${task3.output.location}");
        inputParams.put("constParam", "Some String value   &");
        inputParams.put("partial", "${task2.output.location}/something?host=${EC2_INSTANCE}");
        inputParams.put("jsonPathExtracted", "${workflow.output.names[*].year}");
        inputParams.put("secondName", "${workflow.output.names[1].name}");
        inputParams.put(
                "concatenatedName",
                "The Band is: ${workflow.output.names[1].name}-\t${EC2_INSTANCE}");

        TaskDef taskDef = new TaskDef();
        taskDef.getInputTemplate().put("opname", "${workflow.output.name}");
        List<Object> listParams = new LinkedList<>();
        List<Object> listParams2 = new LinkedList<>();
        listParams2.add("${workflow.input.requestId}-10-${EC2_INSTANCE}");
        listParams.add(listParams2);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "${workflow.output.names[0].name}");
        map.put("hasAwards", "${workflow.input.hasAwards}");
        listParams.add(map);
        taskDef.getInputTemplate().put("listValues", listParams);

        Map<String, Object> taskInput =
                parametersUtils.getTaskInput(inputParams, workflow, taskDef, null);

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("workflowInputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam2"));
        assertTrue(taskInput.containsKey("taskOutputParam3"));
        assertNull(taskInput.get("taskOutputParam2"));
        assertNotNull(taskInput.get("jsonPathExtracted"));
        assertTrue(taskInput.get("jsonPathExtracted") instanceof List);
        assertNotNull(taskInput.get("secondName"));
        assertTrue(taskInput.get("secondName") instanceof String);
        assertEquals("The Doors", taskInput.get("secondName"));
        assertEquals("The Band is: The Doors-\ti-123abcdef990", taskInput.get("concatenatedName"));

        assertEquals("request id 001", taskInput.get("workflowInputParam"));
        assertEquals("http://location", taskInput.get("taskOutputParam"));
        assertNull(taskInput.get("taskOutputParam3"));
        assertNotNull(taskInput.get("partial"));
        assertEquals("http://location/something?host=i-123abcdef990", taskInput.get("partial"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetTaskInput() {
        Map<String, Object> ip = new HashMap<>();
        ip.put("workflowInputParam", "${workflow.input.requestId}");
        ip.put("taskOutputParam", "${task2.output.location}");
        List<Map<String, Object>> json = new LinkedList<>();
        Map<String, Object> m1 = new HashMap<>();
        m1.put("name", "person name");
        m1.put("city", "New York");
        m1.put("phone", 2120001234);
        m1.put("status", "${task2.output.isPersonActive}");

        Map<String, Object> m2 = new HashMap<>();
        m2.put("employer", "City Of New York");
        m2.put("color", "purple");
        m2.put("requestId", "${workflow.input.requestId}");

        json.add(m1);
        json.add(m2);
        ip.put("complexJson", json);

        WorkflowDef def = new WorkflowDef();
        def.setName("testGetTaskInput");
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", "request id 001");
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("task2");
        task.getOutputData().put("location", "http://location");
        task.getOutputData().put("isPersonActive", true);
        workflow.getTasks().add(task);
        Map<String, Object> taskInput = parametersUtils.getTaskInput(ip, workflow, null, null);

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("workflowInputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam"));
        assertEquals("request id 001", taskInput.get("workflowInputParam"));
        assertEquals("http://location", taskInput.get("taskOutputParam"));
        assertNotNull(taskInput.get("complexJson"));
        assertTrue(taskInput.get("complexJson") instanceof List);

        List<Map<String, Object>> resolvedInput =
                (List<Map<String, Object>>) taskInput.get("complexJson");
        assertEquals(2, resolvedInput.size());
    }

    @Test
    public void testGetTaskInputV1() {
        Map<String, Object> ip = new HashMap<>();
        ip.put("workflowInputParam", "workflow.input.requestId");
        ip.put("taskOutputParam", "task2.output.location");

        WorkflowDef def = new WorkflowDef();
        def.setSchemaVersion(1);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);

        workflow.getInput().put("requestId", "request id 001");
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("task2");
        task.getOutputData().put("location", "http://location");
        task.getOutputData().put("isPersonActive", true);
        workflow.getTasks().add(task);
        Map<String, Object> taskInput = parametersUtils.getTaskInput(ip, workflow, null, null);

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("workflowInputParam"));
        assertTrue(taskInput.containsKey("taskOutputParam"));
        assertEquals("request id 001", taskInput.get("workflowInputParam"));
        assertEquals("http://location", taskInput.get("taskOutputParam"));
    }

    @Test
    public void testGetTaskInputV2WithInputTemplate() {
        TaskDef def = new TaskDef();
        Map<String, Object> inputTemplate = new HashMap<>();
        inputTemplate.put("url", "https://some_url:7004");
        inputTemplate.put("default_url", "https://default_url:7004");
        inputTemplate.put("someKey", "someValue");

        def.getInputTemplate().putAll(inputTemplate);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("some_new_url", "https://some_new_url:7004");
        workflowInput.put("workflow_input_url", "https://workflow_input_url:7004");
        workflowInput.put("some_other_key", "some_other_value");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("testGetTaskInputV2WithInputTemplate");
        workflowDef.setVersion(1);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setInput(workflowInput);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("url", "${workflow.input.some_new_url}");
        workflowTask
                .getInputParameters()
                .put("workflow_input_url", "${workflow.input.workflow_input_url}");
        workflowTask.getInputParameters().put("someKey", "${workflow.input.someKey}");
        workflowTask.getInputParameters().put("someOtherKey", "${workflow.input.some_other_key}");
        workflowTask
                .getInputParameters()
                .put("someNowhereToBeFoundKey", "${workflow.input.some_ne_key}");

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(), workflow, null, def);
        assertTrue(taskInput.containsKey("url"));
        assertTrue(taskInput.containsKey("default_url"));
        assertEquals(taskInput.get("url"), "https://some_new_url:7004");
        assertEquals(taskInput.get("default_url"), "https://default_url:7004");
        assertEquals(taskInput.get("workflow_input_url"), "https://workflow_input_url:7004");
        assertEquals("some_other_value", taskInput.get("someOtherKey"));
        assertEquals("someValue", taskInput.get("someKey"));
        assertNull(taskInput.get("someNowhereToBeFoundKey"));
    }

    @Test
    public void testGetNextTask() {

        WorkflowDef def = createNestedWorkflow();
        WorkflowTask firstTask = def.getTasks().get(0);
        assertNotNull(firstTask);
        assertEquals("fork1", firstTask.getTaskReferenceName());
        WorkflowTask nextAfterFirst = def.getNextTask(firstTask.getTaskReferenceName());
        assertNotNull(nextAfterFirst);
        assertEquals("join1", nextAfterFirst.getTaskReferenceName());

        WorkflowTask fork2 = def.getTaskByRefName("fork2");
        assertNotNull(fork2);
        assertEquals("fork2", fork2.getTaskReferenceName());

        WorkflowTask taskAfterFork2 = def.getNextTask("fork2");
        assertNotNull(taskAfterFork2);
        assertEquals("join2", taskAfterFork2.getTaskReferenceName());

        WorkflowTask t2 = def.getTaskByRefName("t2");
        assertNotNull(t2);
        assertEquals("t2", t2.getTaskReferenceName());

        WorkflowTask taskAfterT2 = def.getNextTask("t2");
        assertNotNull(taskAfterT2);
        assertEquals("t4", taskAfterT2.getTaskReferenceName());

        WorkflowTask taskAfterT3 = def.getNextTask("t3");
        assertNotNull(taskAfterT3);
        assertEquals(DECISION.name(), taskAfterT3.getType());
        assertEquals("d1", taskAfterT3.getTaskReferenceName());

        WorkflowTask taskAfterT4 = def.getNextTask("t4");
        assertNotNull(taskAfterT4);
        assertEquals("join2", taskAfterT4.getTaskReferenceName());

        WorkflowTask taskAfterT6 = def.getNextTask("t6");
        assertNotNull(taskAfterT6);
        assertEquals("t9", taskAfterT6.getTaskReferenceName());

        WorkflowTask taskAfterJoin2 = def.getNextTask("join2");
        assertNotNull(taskAfterJoin2);
        assertEquals("join1", taskAfterJoin2.getTaskReferenceName());

        WorkflowTask taskAfterJoin1 = def.getNextTask("join1");
        assertNotNull(taskAfterJoin1);
        assertEquals("t5", taskAfterJoin1.getTaskReferenceName());

        WorkflowTask taskAfterSubWF = def.getNextTask("sw1");
        assertNotNull(taskAfterSubWF);
        assertEquals("join1", taskAfterSubWF.getTaskReferenceName());

        WorkflowTask taskAfterT9 = def.getNextTask("t9");
        assertNotNull(taskAfterT9);
        assertEquals("join2", taskAfterT9.getTaskReferenceName());
    }

    @Test
    public void testCaseStatement() {

        WorkflowDef def = createConditionalWF();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCreateTime(0L);
        workflow.setWorkflowId("a");
        workflow.setCorrelationId("b");
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        DeciderOutcome outcome = deciderService.decide(workflow);
        List<TaskModel> scheduledTasks = outcome.tasksToBeScheduled;
        assertNotNull(scheduledTasks);
        assertEquals(2, scheduledTasks.size());
        assertEquals(TaskModel.Status.IN_PROGRESS, scheduledTasks.get(0).getStatus());
        assertEquals(TaskModel.Status.SCHEDULED, scheduledTasks.get(1).getStatus());
    }

    @Test
    public void testGetTaskByRef() {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel t1 = new TaskModel();
        t1.setReferenceTaskName("ref");
        t1.setSeq(0);
        t1.setStatus(TaskModel.Status.TIMED_OUT);

        TaskModel t2 = new TaskModel();
        t2.setReferenceTaskName("ref");
        t2.setSeq(1);
        t2.setStatus(TaskModel.Status.FAILED);

        TaskModel t3 = new TaskModel();
        t3.setReferenceTaskName("ref");
        t3.setSeq(2);
        t3.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().add(t1);
        workflow.getTasks().add(t2);
        workflow.getTasks().add(t3);

        TaskModel task = workflow.getTaskByRefName("ref");
        assertNotNull(task);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(t3.getSeq(), task.getSeq());
    }

    @Test
    public void testTaskTimeout() {
        Counter counter =
                registry.counter("task_timeout", "class", "WorkflowMonitor", "taskType", "test");
        long counterCount = counter.count();

        TaskDef taskType = new TaskDef();
        taskType.setName("test");
        taskType.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskType.setTimeoutSeconds(1);

        TaskModel task = new TaskModel();
        task.setTaskType(taskType.getName());
        task.setStartTime(System.currentTimeMillis() - 2_000); // 2 seconds ago!
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        deciderService.checkTaskTimeout(taskType, task);

        // Task should be marked as timed out
        assertEquals(TaskModel.Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        taskType.setTimeoutPolicy(TimeoutPolicy.ALERT_ONLY);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);
        deciderService.checkTaskTimeout(taskType, task);

        // Nothing will happen
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        boolean exception = false;
        taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);

        try {
            deciderService.checkTaskTimeout(taskType, task);
        } catch (TerminateWorkflowException tw) {
            exception = true;
        }
        assertTrue(exception);
        assertEquals(TaskModel.Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);
        deciderService.checkTaskTimeout(null, task); // this will be a no-op

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertEquals(counterCount, counter.count());
    }

    @Test
    public void testCheckTaskPollTimeout() {
        Counter counter =
                registry.counter("task_timeout", "class", "WorkflowMonitor", "taskType", "test");
        long counterCount = counter.count();

        TaskDef taskType = new TaskDef();
        taskType.setName("test");
        taskType.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskType.setPollTimeoutSeconds(1);

        TaskModel task = new TaskModel();
        task.setTaskType(taskType.getName());
        task.setScheduledTime(System.currentTimeMillis() - 2_000);
        task.setStatus(TaskModel.Status.SCHEDULED);
        deciderService.checkTaskPollTimeout(taskType, task);

        assertEquals(++counterCount, counter.count());
        assertEquals(TaskModel.Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());

        task.setScheduledTime(System.currentTimeMillis());
        task.setReasonForIncompletion(null);
        task.setStatus(TaskModel.Status.SCHEDULED);
        deciderService.checkTaskPollTimeout(taskType, task);

        assertEquals(counterCount, counter.count());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertNull(task.getReasonForIncompletion());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testConcurrentTaskInputCalc() throws InterruptedException {
        TaskDef def = new TaskDef();

        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("path", "${workflow.input.inputLocation}");
        inputMap.put("type", "${workflow.input.sourceType}");
        inputMap.put("channelMapping", "${workflow.input.channelMapping}");

        List<Map<String, Object>> input = new LinkedList<>();
        input.add(inputMap);

        Map<String, Object> body = new HashMap<>();
        body.put("input", input);

        def.getInputTemplate().putAll(body);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        final int[] result = new int[10];
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final int x = i;
            executorService.submit(
                    () -> {
                        try {
                            Map<String, Object> workflowInput = new HashMap<>();
                            workflowInput.put("outputLocation", "baggins://outputlocation/" + x);
                            workflowInput.put("inputLocation", "baggins://inputlocation/" + x);
                            workflowInput.put("sourceType", "MuxedSource");
                            workflowInput.put("channelMapping", x);

                            WorkflowDef workflowDef = new WorkflowDef();
                            workflowDef.setName("testConcurrentTaskInputCalc");
                            workflowDef.setVersion(1);

                            WorkflowModel workflow = new WorkflowModel();
                            workflow.setWorkflowDefinition(workflowDef);
                            workflow.setInput(workflowInput);

                            Map<String, Object> taskInput =
                                    parametersUtils.getTaskInputV2(
                                            new HashMap<>(), workflow, null, def);

                            Object reqInputObj = taskInput.get("input");
                            assertNotNull(reqInputObj);
                            assertTrue(reqInputObj instanceof List);
                            List<Map<String, Object>> reqInput =
                                    (List<Map<String, Object>>) reqInputObj;

                            Object cmObj = reqInput.get(0).get("channelMapping");
                            assertNotNull(cmObj);
                            if (!(cmObj instanceof Number)) {
                                result[x] = -1;
                            } else {
                                Number channelMapping = (Number) cmObj;
                                result[x] = channelMapping.intValue();
                            }

                            latch.countDown();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
        latch.await(1, TimeUnit.MINUTES);
        if (latch.getCount() > 0) {
            fail(
                    "Executions did not complete in a minute.  Something wrong with the build server?");
        }
        executorService.shutdownNow();
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTaskRetry() {
        WorkflowModel workflow = createDefaultWorkflow();

        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("workflowInputParam", "${workflow.input.requestId}");
        inputParams.put("taskOutputParam", "${task2.output.location}");
        inputParams.put("constParam", "Some String value");
        inputParams.put("nullValue", null);
        inputParams.put("task2Status", "${task2.status}");
        inputParams.put("null", null);
        inputParams.put("task_id", "${CPEWF_TASK_ID}");

        Map<String, Object> env = new HashMap<>();
        env.put("env_task_id", "${CPEWF_TASK_ID}");
        inputParams.put("env", env);

        Map<String, Object> taskInput =
                parametersUtils.getTaskInput(inputParams, workflow, null, "t1");
        TaskModel task = new TaskModel();
        task.getInputData().putAll(taskInput);
        task.setStatus(TaskModel.Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("task_id", "${CPEWF_TASK_ID}");
        workflowTask.getInputParameters().put("env", env);

        Optional<TaskModel> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals("t1", task.getInputData().get("task_id"));
        assertEquals(
                "t1", ((Map<String, Object>) task.getInputData().get("env")).get("env_task_id"));

        assertNotSame(task.getTaskId(), task2.get().getTaskId());
        assertEquals(task2.get().getTaskId(), task2.get().getInputData().get("task_id"));
        assertEquals(
                task2.get().getTaskId(),
                ((Map<String, Object>) task2.get().getInputData().get("env")).get("env_task_id"));

        TaskModel task3 = new TaskModel();
        task3.getInputData().putAll(taskInput);
        task3.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        task3.setTaskId("t1");
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(new WorkflowDef()));
        exception.expect(TerminateWorkflowException.class);
        deciderService.retry(taskDef, workflowTask, task3, workflow);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWorkflowTaskRetry() {
        WorkflowModel workflow = createDefaultWorkflow();

        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("workflowInputParam", "${workflow.input.requestId}");
        inputParams.put("taskOutputParam", "${task2.output.location}");
        inputParams.put("constParam", "Some String value");
        inputParams.put("nullValue", null);
        inputParams.put("task2Status", "${task2.status}");
        inputParams.put("null", null);
        inputParams.put("task_id", "${CPEWF_TASK_ID}");

        Map<String, Object> env = new HashMap<>();
        env.put("env_task_id", "${CPEWF_TASK_ID}");
        inputParams.put("env", env);

        Map<String, Object> taskInput =
                parametersUtils.getTaskInput(inputParams, workflow, null, "t1");

        // Create a first failed task
        TaskModel task = new TaskModel();
        task.getInputData().putAll(taskInput);
        task.setStatus(TaskModel.Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        assertEquals(3, taskDef.getRetryCount());

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("task_id", "${CPEWF_TASK_ID}");
        workflowTask.getInputParameters().put("env", env);
        workflowTask.setRetryCount(1);

        // Retry the failed task and assert that a new one has been created
        Optional<TaskModel> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals("t1", task.getInputData().get("task_id"));
        assertEquals(
                "t1", ((Map<String, Object>) task.getInputData().get("env")).get("env_task_id"));

        assertNotSame(task.getTaskId(), task2.get().getTaskId());
        assertEquals(task2.get().getTaskId(), task2.get().getInputData().get("task_id"));
        assertEquals(
                task2.get().getTaskId(),
                ((Map<String, Object>) task2.get().getInputData().get("env")).get("env_task_id"));

        // Set the retried task to FAILED, retry it again and assert that the workflow failed
        task2.get().setStatus(TaskModel.Status.FAILED);
        exception.expect(TerminateWorkflowException.class);
        final Optional<TaskModel> task3 =
                deciderService.retry(taskDef, workflowTask, task2.get(), workflow);

        assertFalse(task3.isPresent());
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
    }

    @Test
    public void testLinearBackoff() {
        WorkflowModel workflow = createDefaultWorkflow();

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        taskDef.setRetryDelaySeconds(60);
        taskDef.setRetryLogic(TaskDef.RetryLogic.LINEAR_BACKOFF);
        taskDef.setBackoffScaleFactor(2);
        WorkflowTask workflowTask = new WorkflowTask();

        Optional<TaskModel> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals(120, task2.get().getCallbackAfterSeconds()); // 60*2*1

        Optional<TaskModel> task3 =
                deciderService.retry(taskDef, workflowTask, task2.get(), workflow);
        assertEquals(240, task3.get().getCallbackAfterSeconds()); // 60*2*2

        Optional<TaskModel> task4 =
                deciderService.retry(taskDef, workflowTask, task3.get(), workflow);
        // // 60*2*3
        assertEquals(360, task4.get().getCallbackAfterSeconds()); // 60*2*3

        taskDef.setRetryCount(Integer.MAX_VALUE);
        task4.get().setRetryCount(Integer.MAX_VALUE - 100);
        Optional<TaskModel> task5 =
                deciderService.retry(taskDef, workflowTask, task4.get(), workflow);
        assertEquals(Integer.MAX_VALUE, task5.get().getCallbackAfterSeconds());
    }

    @Test
    public void testExponentialBackoff() {
        WorkflowModel workflow = createDefaultWorkflow();

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        taskDef.setRetryDelaySeconds(60);
        taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        WorkflowTask workflowTask = new WorkflowTask();

        Optional<TaskModel> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals(60, task2.get().getCallbackAfterSeconds());

        Optional<TaskModel> task3 =
                deciderService.retry(taskDef, workflowTask, task2.get(), workflow);
        assertEquals(120, task3.get().getCallbackAfterSeconds());

        Optional<TaskModel> task4 =
                deciderService.retry(taskDef, workflowTask, task3.get(), workflow);
        assertEquals(240, task4.get().getCallbackAfterSeconds());

        taskDef.setRetryCount(Integer.MAX_VALUE);
        task4.get().setRetryCount(Integer.MAX_VALUE - 100);
        Optional<TaskModel> task5 =
                deciderService.retry(taskDef, workflowTask, task4.get(), workflow);
        assertEquals(Integer.MAX_VALUE, task5.get().getCallbackAfterSeconds());
    }

    @Test
    public void testFork() throws IOException {
        InputStream stream = TestDeciderService.class.getResourceAsStream("/test.json");
        WorkflowModel workflow = objectMapper.readValue(stream, WorkflowModel.class);

        DeciderOutcome outcome = deciderService.decide(workflow);
        assertFalse(outcome.isComplete);
        assertEquals(5, outcome.tasksToBeScheduled.size());
        assertEquals(1, outcome.tasksToBeUpdated.size());
    }

    @Test
    public void testDecideSuccessfulWorkflow() {
        WorkflowDef workflowDef = createLinearWorkflow();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel task1 = new TaskModel();
        task1.setTaskType("junit_task_l1");
        task1.setReferenceTaskName("s1");
        task1.setSeq(1);
        task1.setRetried(false);
        task1.setExecuted(false);
        task1.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().add(task1);

        DeciderOutcome deciderOutcome = deciderService.decide(workflow);
        assertNotNull(deciderOutcome);

        assertFalse(workflow.getTaskByRefName("s1").isRetried());
        assertEquals(1, deciderOutcome.tasksToBeUpdated.size());
        assertEquals("s1", deciderOutcome.tasksToBeUpdated.get(0).getReferenceTaskName());
        assertEquals(1, deciderOutcome.tasksToBeScheduled.size());
        assertEquals("s2", deciderOutcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertFalse(deciderOutcome.isComplete);

        TaskModel task2 = new TaskModel();
        task2.setTaskType("junit_task_l2");
        task2.setReferenceTaskName("s2");
        task2.setSeq(2);
        task2.setRetried(false);
        task2.setExecuted(false);
        task2.setStatus(TaskModel.Status.COMPLETED);
        workflow.getTasks().add(task2);

        deciderOutcome = deciderService.decide(workflow);
        assertNotNull(deciderOutcome);
        assertTrue(workflow.getTaskByRefName("s2").isExecuted());
        assertFalse(workflow.getTaskByRefName("s2").isRetried());
        assertEquals(1, deciderOutcome.tasksToBeUpdated.size());
        assertEquals("s2", deciderOutcome.tasksToBeUpdated.get(0).getReferenceTaskName());
        assertEquals(0, deciderOutcome.tasksToBeScheduled.size());
        assertTrue(deciderOutcome.isComplete);
    }

    @Test
    public void testDecideWithLoopTask() {
        WorkflowDef workflowDef = createLinearWorkflow();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel task1 = new TaskModel();
        task1.setTaskType("junit_task_l1");
        task1.setReferenceTaskName("s1");
        task1.setSeq(1);
        task1.setIteration(1);
        task1.setRetried(false);
        task1.setExecuted(false);
        task1.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().add(task1);

        DeciderOutcome deciderOutcome = deciderService.decide(workflow);
        assertNotNull(deciderOutcome);

        assertFalse(workflow.getTaskByRefName("s1").isRetried());
        assertEquals(1, deciderOutcome.tasksToBeUpdated.size());
        assertEquals("s1", deciderOutcome.tasksToBeUpdated.get(0).getReferenceTaskName());
        assertEquals(1, deciderOutcome.tasksToBeScheduled.size());
        assertEquals("s2__1", deciderOutcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertFalse(deciderOutcome.isComplete);
    }

    @Test
    public void testDecideFailedTask() {
        WorkflowDef workflowDef = createLinearWorkflow();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        TaskModel task = new TaskModel();
        task.setTaskType("junit_task_l1");
        task.setReferenceTaskName("s1");
        task.setSeq(1);
        task.setRetried(false);
        task.setExecuted(false);
        task.setStatus(TaskModel.Status.FAILED);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("s1");
        workflowTask.setName("junit_task_l1");
        workflowTask.setTaskDefinition(new TaskDef("junit_task_l1"));
        task.setWorkflowTask(workflowTask);

        workflow.getTasks().add(task);

        DeciderOutcome deciderOutcome = deciderService.decide(workflow);
        assertNotNull(deciderOutcome);
        assertFalse(workflow.getTaskByRefName("s1").isExecuted());
        assertTrue(workflow.getTaskByRefName("s1").isRetried());
        assertEquals(1, deciderOutcome.tasksToBeUpdated.size());
        assertEquals("s1", deciderOutcome.tasksToBeUpdated.get(0).getReferenceTaskName());
        assertEquals(1, deciderOutcome.tasksToBeScheduled.size());
        assertEquals("s1", deciderOutcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertFalse(deciderOutcome.isComplete);
    }

    @Test
    public void testGetTasksToBeScheduled() {
        WorkflowDef workflowDef = createLinearWorkflow();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("s1");
        workflowTask1.setTaskReferenceName("s1");
        workflowTask1.setType(SIMPLE.name());
        workflowTask1.setTaskDefinition(new TaskDef("s1"));

        List<TaskModel> tasksToBeScheduled =
                deciderService.getTasksToBeScheduled(workflow, workflowTask1, 0, null);
        assertNotNull(tasksToBeScheduled);
        assertEquals(1, tasksToBeScheduled.size());
        assertEquals("s1", tasksToBeScheduled.get(0).getReferenceTaskName());

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("s2");
        workflowTask2.setTaskReferenceName("s2");
        workflowTask2.setType(SIMPLE.name());
        workflowTask2.setTaskDefinition(new TaskDef("s2"));
        tasksToBeScheduled = deciderService.getTasksToBeScheduled(workflow, workflowTask2, 0, null);
        assertNotNull(tasksToBeScheduled);
        assertEquals(1, tasksToBeScheduled.size());
        assertEquals("s2", tasksToBeScheduled.get(0).getReferenceTaskName());
    }

    @Test
    public void testIsResponseTimedOut() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_rt");
        taskDef.setResponseTimeoutSeconds(10);

        TaskModel task = new TaskModel();
        task.setTaskDefName("test_rt");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setTaskId("aa");
        task.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        task.setUpdateTime(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(11));

        assertTrue(deciderService.isResponseTimedOut(taskDef, task));

        // verify that sub workflow tasks are not response timed out
        task.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);
        assertFalse(deciderService.isResponseTimedOut(taskDef, task));

        task.setTaskType("asyncCompleteSystemTask");
        assertFalse(deciderService.isResponseTimedOut(taskDef, task));
    }

    @Test
    public void testFilterNextLoopOverTasks() {

        WorkflowModel workflow = new WorkflowModel();

        TaskModel task1 = new TaskModel();
        task1.setReferenceTaskName("task1");
        task1.setStatus(TaskModel.Status.COMPLETED);
        task1.setTaskId("task1");
        task1.setIteration(1);

        TaskModel task2 = new TaskModel();
        task2.setReferenceTaskName("task2");
        task2.setStatus(TaskModel.Status.SCHEDULED);
        task2.setTaskId("task2");

        TaskModel task3 = new TaskModel();
        task3.setReferenceTaskName("task3__1");
        task3.setStatus(TaskModel.Status.IN_PROGRESS);
        task3.setTaskId("task3__1");

        TaskModel task4 = new TaskModel();
        task4.setReferenceTaskName("task4");
        task4.setStatus(TaskModel.Status.SCHEDULED);
        task4.setTaskId("task4");

        TaskModel task5 = new TaskModel();
        task5.setReferenceTaskName("task5");
        task5.setStatus(TaskModel.Status.COMPLETED);
        task5.setTaskId("task5");

        workflow.getTasks().addAll(Arrays.asList(task1, task2, task3, task4, task5));
        List<TaskModel> tasks =
                deciderService.filterNextLoopOverTasks(
                        Arrays.asList(task2, task3, task4), task1, workflow);
        assertEquals(2, tasks.size());
        tasks.forEach(
                task -> {
                    assertTrue(
                            task.getReferenceTaskName()
                                    .endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(1)));
                    assertEquals(1, task.getIteration());
                });
    }

    @Test
    public void testUpdateWorkflowOutput() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().isEmpty());
        TaskModel task = new TaskModel();
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("taskKey", "taskValue");
        task.setOutputData(taskOutput);
        workflow.getTasks().add(task);
        WorkflowDef workflowDef = new WorkflowDef();
        when(metadataDAO.getWorkflowDef(anyString(), anyInt()))
                .thenReturn(Optional.of(workflowDef));
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertEquals("taskValue", workflow.getOutput().get("taskKey"));
    }

    // when workflow definition has outputParameters defined
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testUpdateWorkflowOutput_WhenDefinitionHasOutputParameters() {
        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setOutputParameters(
                new HashMap() {
                    {
                        put("workflowKey", "workflowValue");
                    }
                });
        workflow.setWorkflowDefinition(workflowDef);
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("test_task");
        task.setOutputData(
                new HashMap() {
                    {
                        put("taskKey", "taskValue");
                    }
                });
        workflow.getTasks().add(task);
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertEquals("workflowValue", workflow.getOutput().get("workflowKey"));
    }

    @Test
    public void testUpdateWorkflowOutput_WhenWorkflowHasTerminateTask() {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_TERMINATE);
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(
                new HashMap<String, Object>() {
                    {
                        put("taskKey", "taskValue");
                    }
                });
        workflow.getTasks().add(task);
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertEquals("taskValue", workflow.getOutput().get("taskKey"));
        verify(externalPayloadStorageUtils, never()).downloadPayload(anyString());

        // when terminate task has output in external payload storage
        String externalOutputPayloadStoragePath = "/task/output/terminate.json";
        workflow.getTasks().get(0).setOutputData(null);
        workflow.getTasks()
                .get(0)
                .setExternalOutputPayloadStoragePath(externalOutputPayloadStoragePath);
        when(externalPayloadStorageUtils.downloadPayload(externalOutputPayloadStoragePath))
                .thenReturn(
                        new HashMap() {
                            {
                                put("taskKey", "taskValue");
                            }
                        });
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertEquals("taskValue", workflow.getOutput().get("taskKey"));
        verify(externalPayloadStorageUtils, times(1)).downloadPayload(anyString());
    }

    @Test
    public void testCheckWorkflowTimeout() {
        Counter counter =
                registry.counter(
                        "workflow_failure",
                        "class",
                        "WorkflowMonitor",
                        "workflowName",
                        "test",
                        "status",
                        "TIMED_OUT",
                        "ownerApp",
                        "junit");
        long counterCount = counter.count();
        assertEquals(0, counter.count());

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        WorkflowModel workflow = new WorkflowModel();
        workflow.setOwnerApp("junit");
        workflow.setCreateTime(System.currentTimeMillis() - 10_000);
        workflow.setWorkflowId("workflow_id");

        // no-op
        workflow.setWorkflowDefinition(null);
        deciderService.checkWorkflowTimeout(workflow);

        // no-op
        workflow.setWorkflowDefinition(workflowDef);
        deciderService.checkWorkflowTimeout(workflow);

        // alert
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflowDef.setTimeoutSeconds(2);
        workflow.setWorkflowDefinition(workflowDef);
        deciderService.checkWorkflowTimeout(workflow);
        assertEquals(++counterCount, counter.count());

        // time out
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflow.setWorkflowDefinition(workflowDef);
        try {
            deciderService.checkWorkflowTimeout(workflow);
        } catch (TerminateWorkflowException twe) {
            assertTrue(twe.getMessage().contains("Workflow timed out"));
        }

        // for a retried workflow
        workflow.setLastRetriedTime(System.currentTimeMillis() - 5_000);
        try {
            deciderService.checkWorkflowTimeout(workflow);
        } catch (TerminateWorkflowException twe) {
            assertTrue(twe.getMessage().contains("Workflow timed out"));
        }
    }

    @Test
    public void testCheckForWorkflowCompletion() {
        WorkflowDef conditionalWorkflowDef = createConditionalWF();
        WorkflowTask terminateWT = new WorkflowTask();
        terminateWT.setType(TaskType.TERMINATE.name());
        terminateWT.setTaskReferenceName("terminate");
        terminateWT.setName("terminate");
        terminateWT.getInputParameters().put("terminationStatus", "COMPLETED");
        conditionalWorkflowDef.getTasks().add(terminateWT);

        // when workflow has no tasks
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(conditionalWorkflowDef);

        // then workflow completion check returns false
        assertFalse(deciderService.checkForWorkflowCompletion(workflow));

        // when only part of the tasks are completed
        TaskModel decTask = new TaskModel();
        decTask.setTaskType(DECISION.name());
        decTask.setReferenceTaskName("conditional2");
        decTask.setStatus(TaskModel.Status.COMPLETED);

        TaskModel task1 = new TaskModel();
        decTask.setTaskType(SIMPLE.name());
        task1.setReferenceTaskName("t1");
        task1.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().addAll(Arrays.asList(decTask, task1));

        // then workflow completion check returns false
        assertFalse(deciderService.checkForWorkflowCompletion(workflow));

        // when the terminate task is COMPLETED
        TaskModel task2 = new TaskModel();
        decTask.setTaskType(SIMPLE.name());
        task2.setReferenceTaskName("t2");
        task2.setStatus(TaskModel.Status.SCHEDULED);

        TaskModel terminateTask = new TaskModel();
        decTask.setTaskType(TaskType.TERMINATE.name());
        terminateTask.setReferenceTaskName("terminate");
        terminateTask.setStatus(TaskModel.Status.COMPLETED);

        workflow.getTasks().addAll(Arrays.asList(task2, terminateTask));

        // then the workflow completion check returns true
        assertTrue(deciderService.checkForWorkflowCompletion(workflow));
    }

    private WorkflowDef createConditionalWF() {

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        Map<String, Object> inputParams1 = new HashMap<>();
        inputParams1.put("p1", "workflow.input.param1");
        inputParams1.put("p2", "workflow.input.param2");
        workflowTask1.setInputParameters(inputParams1);
        workflowTask1.setTaskReferenceName("t1");
        workflowTask1.setTaskDefinition(new TaskDef("junit_task_1"));

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        Map<String, Object> inputParams2 = new HashMap<>();
        inputParams2.put("tp1", "workflow.input.param1");
        workflowTask2.setInputParameters(inputParams2);
        workflowTask2.setTaskReferenceName("t2");
        workflowTask2.setTaskDefinition(new TaskDef("junit_task_2"));

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_3");
        Map<String, Object> inputParams3 = new HashMap<>();
        inputParams2.put("tp3", "workflow.input.param2");
        workflowTask3.setInputParameters(inputParams3);
        workflowTask3.setTaskReferenceName("t3");
        workflowTask3.setTaskDefinition(new TaskDef("junit_task_3"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("Conditional Workflow");
        workflowDef.setDescription("Conditional Workflow");
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask decisionTask2 = new WorkflowTask();
        decisionTask2.setType(DECISION.name());
        decisionTask2.setCaseValueParam("case");
        decisionTask2.setName("conditional2");
        decisionTask2.setTaskReferenceName("conditional2");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("one", Arrays.asList(workflowTask1, workflowTask3));
        dc.put("two", Collections.singletonList(workflowTask2));
        decisionTask2.setDecisionCases(dc);
        decisionTask2.getInputParameters().put("case", "workflow.input.param2");

        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(DECISION.name());
        decisionTask.setCaseValueParam("case");
        decisionTask.setName("conditional");
        decisionTask.setTaskReferenceName("conditional");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("nested", Collections.singletonList(decisionTask2));
        decisionCases.put("three", Collections.singletonList(workflowTask3));
        decisionTask.setDecisionCases(decisionCases);
        decisionTask.getInputParameters().put("case", "workflow.input.param1");
        decisionTask.getDefaultCase().add(workflowTask2);
        workflowDef.getTasks().add(decisionTask);

        WorkflowTask notifyTask = new WorkflowTask();
        notifyTask.setName("junit_task_4");
        notifyTask.setTaskReferenceName("junit_task_4");
        notifyTask.setTaskDefinition(new TaskDef("junit_task_4"));

        WorkflowTask finalDecisionTask = new WorkflowTask();
        finalDecisionTask.setName("finalcondition");
        finalDecisionTask.setTaskReferenceName("tf");
        finalDecisionTask.setType(DECISION.name());
        finalDecisionTask.setCaseValueParam("finalCase");
        Map<String, Object> fi = new HashMap<>();
        fi.put("finalCase", "workflow.input.finalCase");
        finalDecisionTask.setInputParameters(fi);
        finalDecisionTask.getDecisionCases().put("notify", Collections.singletonList(notifyTask));

        workflowDef.getTasks().add(finalDecisionTask);
        return workflowDef;
    }

    private WorkflowDef createLinearWorkflow() {

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("p1", "workflow.input.param1");
        inputParams.put("p2", "workflow.input.param2");

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_l1");
        workflowTask1.setInputParameters(inputParams);
        workflowTask1.setTaskReferenceName("s1");
        workflowTask1.setTaskDefinition(new TaskDef("junit_task_l1"));

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_l2");
        workflowTask2.setInputParameters(inputParams);
        workflowTask2.setTaskReferenceName("s2");
        workflowTask2.setTaskDefinition(new TaskDef("junit_task_l2"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));
        workflowDef.setName("Linear Workflow");
        workflowDef.getTasks().addAll(Arrays.asList(workflowTask1, workflowTask2));

        return workflowDef;
    }

    private WorkflowModel createDefaultWorkflow() {

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("TestDeciderService");
        workflowDef.setVersion(1);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        workflow.getInput().put("requestId", "request id 001");
        workflow.getInput().put("hasAwards", true);
        workflow.getInput().put("channelMapping", 5);
        Map<String, Object> name = new HashMap<>();
        name.put("name", "The Who");
        name.put("year", 1970);
        Map<String, Object> name2 = new HashMap<>();
        name2.put("name", "The Doors");
        name2.put("year", 1975);

        List<Object> names = new LinkedList<>();
        names.add(name);
        names.add(name2);

        workflow.getOutput().put("name", name);
        workflow.getOutput().put("names", names);
        workflow.getOutput().put("awards", 200);

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("task2");
        task.getOutputData().put("location", "http://location");
        task.setStatus(TaskModel.Status.COMPLETED);

        TaskModel task2 = new TaskModel();
        task2.setReferenceTaskName("task3");
        task2.getOutputData().put("refId", "abcddef_1234_7890_aaffcc");
        task2.setStatus(TaskModel.Status.SCHEDULED);

        workflow.getTasks().add(task);
        workflow.getTasks().add(task2);

        return workflow;
    }

    private WorkflowDef createNestedWorkflow() {

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("Nested Workflow");
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("p1", "workflow.input.param1");
        inputParams.put("p2", "workflow.input.param2");

        List<WorkflowTask> tasks = new ArrayList<>(10);

        for (int i = 0; i < 10; i++) {
            WorkflowTask workflowTask = new WorkflowTask();
            workflowTask.setName("junit_task_" + i);
            workflowTask.setInputParameters(inputParams);
            workflowTask.setTaskReferenceName("t" + i);
            workflowTask.setTaskDefinition(new TaskDef("junit_task_" + i));
            tasks.add(workflowTask);
        }

        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(DECISION.name());
        decisionTask.setName("Decision");
        decisionTask.setTaskReferenceName("d1");
        decisionTask.setDefaultCase(Collections.singletonList(tasks.get(8)));
        decisionTask.setCaseValueParam("case");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("a", Arrays.asList(tasks.get(6), tasks.get(9)));
        decisionCases.put("b", Collections.singletonList(tasks.get(7)));
        decisionTask.setDecisionCases(decisionCases);

        WorkflowDef subWorkflowDef = createLinearWorkflow();
        WorkflowTask subWorkflow = new WorkflowTask();
        subWorkflow.setType(SUB_WORKFLOW.name());
        subWorkflow.setName("sw1");
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowDef.getName());
        subWorkflow.setSubWorkflowParam(subWorkflowParams);
        subWorkflow.setTaskReferenceName("sw1");

        WorkflowTask forkTask2 = new WorkflowTask();
        forkTask2.setType(FORK_JOIN.name());
        forkTask2.setName("second fork");
        forkTask2.setTaskReferenceName("fork2");
        forkTask2.getForkTasks().add(Arrays.asList(tasks.get(2), tasks.get(4)));
        forkTask2.getForkTasks().add(Arrays.asList(tasks.get(3), decisionTask));

        WorkflowTask joinTask2 = new WorkflowTask();
        joinTask2.setName("join2");
        joinTask2.setType(JOIN.name());
        joinTask2.setTaskReferenceName("join2");
        joinTask2.setJoinOn(Arrays.asList("t4", "d1"));

        WorkflowTask forkTask1 = new WorkflowTask();
        forkTask1.setType(FORK_JOIN.name());
        forkTask1.setName("fork1");
        forkTask1.setTaskReferenceName("fork1");
        forkTask1.getForkTasks().add(Collections.singletonList(tasks.get(1)));
        forkTask1.getForkTasks().add(Arrays.asList(forkTask2, joinTask2));
        forkTask1.getForkTasks().add(Collections.singletonList(subWorkflow));

        WorkflowTask joinTask1 = new WorkflowTask();
        joinTask1.setName("join1");
        joinTask1.setType(JOIN.name());
        joinTask1.setTaskReferenceName("join1");
        joinTask1.setJoinOn(Arrays.asList("t1", "fork2"));

        workflowDef.getTasks().add(forkTask1);
        workflowDef.getTasks().add(joinTask1);
        workflowDef.getTasks().add(tasks.get(5));

        return workflowDef;
    }
}
