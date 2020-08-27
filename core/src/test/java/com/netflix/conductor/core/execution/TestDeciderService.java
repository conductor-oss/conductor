/*
 * Copyright 2019 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.common.utils.TaskUtils;
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
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author Viren
 *
 */
@SuppressWarnings("Duplicates")
public class TestDeciderService {

    private DeciderService deciderService;

    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;

    private static Registry registry;

    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void init() {
        registry = new DefaultRegistry();
        Spectator.globalRegistry().add(registry);
    }

    @Before
    public void setup() {
        metadataDAO = mock(MetadataDAO.class);
        externalPayloadStorageUtils = mock(ExternalPayloadStorageUtils.class);
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        Configuration config = mock(Configuration.class);

        TaskDef taskDef = new TaskDef();

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("TestDeciderService");
        workflowDef.setVersion(1);

        when(metadataDAO.getTaskDef(any())).thenReturn(taskDef);
        when(metadataDAO.getLatestWorkflowDef(any())).thenReturn(Optional.of(workflowDef));
        parametersUtils = new ParametersUtils();
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

        deciderService = new DeciderService(parametersUtils, metadataDAO, externalPayloadStorageUtils, taskMappers, config);
    }

    @Test
    public void testGetTaskInputV2() {
        Workflow workflow = createDefaultWorkflow();

        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> ip = new HashMap<>();
        ip.put("workflowInputParam", "${workflow.input.requestId}");
        ip.put("taskOutputParam", "${task2.output.location}");
        ip.put("taskOutputParam2", "${task2.output.locationBad}");
        ip.put("taskOutputParam3", "${task3.output.location}");
        ip.put("constParam", "Some String value");
        ip.put("nullValue", null);
        ip.put("task2Status", "${task2.status}");
        ip.put("channelMap", "${workflow.input.channelMapping}");
        Map<String, Object> taskInput = parametersUtils.getTaskInput(ip, workflow, null, null);

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
        assertEquals(workflow.getTasks().get(0).getStatus().name(), taskInput.get("task2Status"));    //task2 and task3 are the tasks respectively
    }

    @Test
    public void testGetTaskInputV2Partial() {
        Workflow workflow = createDefaultWorkflow();

        System.setProperty("EC2_INSTANCE", "i-123abcdef990");
        Map<String, Object> wfi = new HashMap<>();
        Map<String, Object> wfmap = new HashMap<>();
        wfmap.put("input", workflow.getInput());
        wfmap.put("output", workflow.getOutput());
        wfi.put("workflow", wfmap);

        workflow.getTasks().stream()
                .map(Task::getReferenceTaskName)
                .forEach(ref -> {
                    Map<String, Object> taskInput = workflow.getTaskByRefName(ref).getInputData();
                    Map<String, Object> taskOutput = workflow.getTaskByRefName(ref).getOutputData();
                    Map<String, Object> io = new HashMap<>();
                    io.put("input", taskInput);
                    io.put("output", taskOutput);
                    wfi.put(ref, io);
                });

        workflow.getWorkflowDefinition().setSchemaVersion(2);

        Map<String, Object> ip = new HashMap<>();
        ip.put("workflowInputParam", "${workflow.input.requestId}");
        ip.put("workfowOutputParam", "${workflow.output.name}");
        ip.put("taskOutputParam", "${task2.output.location}");
        ip.put("taskOutputParam2", "${task2.output.locationBad}");
        ip.put("taskOutputParam3", "${task3.output.location}");
        ip.put("constParam", "Some String value   &");
        ip.put("partial", "${task2.output.location}/something?host=${EC2_INSTANCE}");
        ip.put("jsonPathExtracted", "${workflow.output.names[*].year}");
        ip.put("secondName", "${workflow.output.names[1].name}");
        ip.put("concatenatedName", "The Band is: ${workflow.output.names[1].name}-\t${EC2_INSTANCE}");

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


        Map<String, Object> taskInput = parametersUtils.getTaskInput(ip, workflow, taskDef, null);

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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.getInput().put("requestId", "request id 001");
        Task task = new Task();
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

        List<Map<String, Object>> resolvedInput = (List<Map<String, Object>>) taskInput.get("complexJson");
        assertEquals(2, resolvedInput.size());
    }

    @Test
    public void testGetTaskInputV1() {
        Map<String, Object> ip = new HashMap<>();
        ip.put("workflowInputParam", "workflow.input.requestId");
        ip.put("taskOutputParam", "task2.output.location");

        WorkflowDef def = new WorkflowDef();
        def.setSchemaVersion(1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);

        workflow.getInput().put("requestId", "request id 001");
        Task task = new Task();
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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setInput(workflowInput);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("url", "${workflow.input.some_new_url}");
        workflowTask.getInputParameters().put("workflow_input_url", "${workflow.input.workflow_input_url}");
        workflowTask.getInputParameters().put("someKey", "${workflow.input.someKey}");
        workflowTask.getInputParameters().put("someOtherKey", "${workflow.input.some_other_key}");
        workflowTask.getInputParameters().put("someNowhereToBeFoundKey", "${workflow.input.some_ne_key}");

        Map<String, Object> taskInput = parametersUtils.getTaskInputV2(workflowTask.getInputParameters(), workflow, null, def);
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
        assertEquals(TaskType.DECISION.name(), taskAfterT3.getType());
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

        Workflow wf = new Workflow();
        wf.setWorkflowDefinition(def);
        wf.setCreateTime(0L);
        wf.setWorkflowId("a");
        wf.setCorrelationId("b");
        wf.setStatus(WorkflowStatus.RUNNING);

        DeciderOutcome outcome = deciderService.decide(wf);
        List<Task> scheduledTasks = outcome.tasksToBeScheduled;
        assertNotNull(scheduledTasks);
        assertEquals(2, scheduledTasks.size());
        assertEquals(Status.IN_PROGRESS, scheduledTasks.get(0).getStatus());
        assertEquals(Status.SCHEDULED, scheduledTasks.get(1).getStatus());

    }

    @Test
    public void testGetTaskByRef() {
        Workflow workflow = new Workflow();
        Task t1 = new Task();
        t1.setReferenceTaskName("ref");
        t1.setSeq(0);
        t1.setStatus(Status.TIMED_OUT);

        Task t2 = new Task();
        t2.setReferenceTaskName("ref");
        t2.setSeq(1);
        t2.setStatus(Status.FAILED);

        Task t3 = new Task();
        t3.setReferenceTaskName("ref");
        t3.setSeq(2);
        t3.setStatus(Status.COMPLETED);

        workflow.getTasks().add(t1);
        workflow.getTasks().add(t2);
        workflow.getTasks().add(t3);

        Task task = workflow.getTaskByRefName("ref");
        assertNotNull(task);
        assertEquals(Status.COMPLETED, task.getStatus());
        assertEquals(t3.getSeq(), task.getSeq());

    }

    @Test
    public void testTaskTimeout() {
        Counter counter = registry.counter("task_timeout", "class", "WorkflowMonitor", "taskType", "test");
        long counterCount = counter.count();

        TaskDef taskType = new TaskDef();
        taskType.setName("test");
        taskType.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskType.setTimeoutSeconds(1);

        Task task = new Task();
        task.setTaskType(taskType.getName());
        task.setStartTime(System.currentTimeMillis() - 2_000);        //2 seconds ago!
        task.setStatus(Status.IN_PROGRESS);
        deciderService.checkTaskTimeout(taskType, task);

        //Task should be marked as timed out
        assertEquals(Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        taskType.setTimeoutPolicy(TimeoutPolicy.ALERT_ONLY);
        task.setStatus(Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);
        deciderService.checkTaskTimeout(taskType, task);

        //Nothing will happen
        assertEquals(Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        boolean exception = false;
        taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
        task.setStatus(Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);

        try {
            deciderService.checkTaskTimeout(taskType, task);
        } catch (TerminateWorkflowException tw) {
            exception = true;
        }
        assertTrue(exception);
        assertEquals(Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());
        assertEquals(++counterCount, counter.count());

        taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
        task.setStatus(Status.IN_PROGRESS);
        task.setReasonForIncompletion(null);
        deciderService.checkTaskTimeout(null, task);    //this will be a no-op

        assertEquals(Status.IN_PROGRESS, task.getStatus());
        assertNull(task.getReasonForIncompletion());
        assertEquals(counterCount, counter.count());
    }

    @Test
    public void testCheckTaskPollTimeout() {
        Counter counter = registry.counter("task_timeout", "class", "WorkflowMonitor", "taskType", "test");
        long counterCount = counter.count();

        TaskDef taskType = new TaskDef();
        taskType.setName("test");
        taskType.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskType.setPollTimeoutSeconds(1);

        Task task = new Task();
        task.setTaskType(taskType.getName());
        task.setScheduledTime(System.currentTimeMillis() - 2_000);
        task.setStatus(Status.SCHEDULED);
        deciderService.checkTaskPollTimeout(taskType, task);

        assertEquals(++counterCount, counter.count());
        assertEquals(Status.TIMED_OUT, task.getStatus());
        assertNotNull(task.getReasonForIncompletion());

        task.setScheduledTime(System.currentTimeMillis());
        task.setReasonForIncompletion(null);
        task.setStatus(Status.SCHEDULED);
        deciderService.checkTaskPollTimeout(taskType, task);

        assertEquals(counterCount, counter.count());
        assertEquals(Status.SCHEDULED, task.getStatus());
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

        ExecutorService es = Executors.newFixedThreadPool(10);
        final int[] result = new int[10];
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final int x = i;
            es.submit(() -> {

                try {

                    Map<String, Object> workflowInput = new HashMap<>();
                    workflowInput.put("outputLocation", "baggins://outputlocation/" + x);
                    workflowInput.put("inputLocation", "baggins://inputlocation/" + x);
                    workflowInput.put("sourceType", "MuxedSource");
                    workflowInput.put("channelMapping", x);

                    WorkflowDef workflowDef = new WorkflowDef();
                    workflowDef.setName("testConcurrentTaskInputCalc");
                    workflowDef.setVersion(1);

                    Workflow workflow = new Workflow();
                    workflow.setWorkflowDefinition(workflowDef);
                    workflow.setInput(workflowInput);

                    Map<String, Object> taskInput = parametersUtils.getTaskInputV2(new HashMap<>(), workflow, null, def);

                    Object reqInputObj = taskInput.get("input");
                    assertNotNull(reqInputObj);
                    assertTrue(reqInputObj instanceof List);
                    List<Map<String, Object>> reqInput = (List<Map<String, Object>>) reqInputObj;

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
            fail("Executions did not complete in a minute.  Something wrong with the build server?");
        }
        es.shutdownNow();
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testTaskRetry() {
        Workflow workflow = createDefaultWorkflow();

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

        Map<String, Object> taskInput = parametersUtils.getTaskInput(inputParams, workflow, null, "t1");
        Task task = new Task();
        task.getInputData().putAll(taskInput);
        task.setStatus(Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("task_id", "${CPEWF_TASK_ID}");
        workflowTask.getInputParameters().put("env", env);

        Optional<Task> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals("t1", task.getInputData().get("task_id"));
        assertEquals("t1", ((Map<String, Object>) task.getInputData().get("env")).get("env_task_id"));

        assertNotSame(task.getTaskId(), task2.get().getTaskId());
        assertEquals(task2.get().getTaskId(), task2.get().getInputData().get("task_id"));
        assertEquals(task2.get().getTaskId(), ((Map<String, Object>) task2.get().getInputData().get("env")).get("env_task_id"));

        Task task3 = new Task();
        task3.getInputData().putAll(taskInput);
        task3.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
        task3.setTaskId("t1");
        when(metadataDAO.getWorkflowDef(anyString(), anyInt())).thenReturn(Optional.of(new WorkflowDef()));
        exception.expect(TerminateWorkflowException.class);
        deciderService.retry(taskDef, workflowTask, task3, workflow);
    }

    @Test
    public void testWorkflowTaskRetry() {
        Workflow workflow = createDefaultWorkflow();

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

        Map<String, Object> taskInput = parametersUtils.getTaskInput(inputParams, workflow, null, "t1");

        // Create a first failed task
        Task task = new Task();
        task.getInputData().putAll(taskInput);
        task.setStatus(Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        assertEquals(3, taskDef.getRetryCount());

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.getInputParameters().put("task_id", "${CPEWF_TASK_ID}");
        workflowTask.getInputParameters().put("env", env);
        workflowTask.setRetryCount(1);

        // Retry the failed task and assert that a new one has been created
        Optional<Task> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals("t1", task.getInputData().get("task_id"));
        assertEquals("t1", ((Map<String, Object>) task.getInputData().get("env")).get("env_task_id"));

        assertNotSame(task.getTaskId(), task2.get().getTaskId());
        assertEquals(task2.get().getTaskId(), task2.get().getInputData().get("task_id"));
        assertEquals(task2.get().getTaskId(), ((Map<String, Object>) task2.get().getInputData().get("env")).get("env_task_id"));

        // Set the retried task to FAILED, retry it again and assert that the workflow failed
        task2.get().setStatus(Status.FAILED);
        exception.expect(TerminateWorkflowException.class);
        final Optional<Task> task3 = deciderService.retry(taskDef, workflowTask, task2.get(), workflow);

        assertFalse(task3.isPresent());
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());
    }

    @Test
    public void testExponentialBackoff() {
        Workflow workflow = createDefaultWorkflow();

        Task task = new Task();
        task.setStatus(Status.FAILED);
        task.setTaskId("t1");

        TaskDef taskDef = new TaskDef();
        taskDef.setRetryDelaySeconds(60);
        taskDef.setRetryLogic(TaskDef.RetryLogic.EXPONENTIAL_BACKOFF);
        WorkflowTask workflowTask = new WorkflowTask();

        Optional<Task> task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
        assertEquals(60, task2.get().getCallbackAfterSeconds());

        Optional<Task> task3 = deciderService.retry(taskDef, workflowTask, task2.get(), workflow);
        assertEquals(120, task3.get().getCallbackAfterSeconds());

        Optional<Task> task4 = deciderService.retry(taskDef, workflowTask, task3.get(), workflow);
        assertEquals(240, task4.get().getCallbackAfterSeconds());

        taskDef.setRetryCount(Integer.MAX_VALUE);
        task4.get().setRetryCount(Integer.MAX_VALUE - 100);
        Optional<Task> task5 = deciderService.retry(taskDef, workflowTask, task4.get(), workflow);
        assertEquals(Integer.MAX_VALUE, task5.get().getCallbackAfterSeconds());
    }

    @Test
    public void testFork() throws IOException {
        InputStream stream = TestDeciderService.class.getResourceAsStream("/test.json");
        Workflow workflow = objectMapper.readValue(stream, Workflow.class);

        DeciderOutcome outcome = deciderService.decide(workflow);
        assertFalse(outcome.isComplete);
        assertEquals(5, outcome.tasksToBeScheduled.size());
        assertEquals(1, outcome.tasksToBeUpdated.size());
    }

    @Test
    public void testDecideSuccessfulWorkflow() {
        WorkflowDef workflowDef = createLinearWorkflow();

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowStatus.RUNNING);

        Task task1 = new Task();
        task1.setTaskType("junit_task_l1");
        task1.setReferenceTaskName("s1");
        task1.setSeq(1);
        task1.setRetried(false);
        task1.setExecuted(false);
        task1.setStatus(Status.COMPLETED);

        workflow.getTasks().add(task1);

        DeciderOutcome deciderOutcome = deciderService.decide(workflow);
        assertNotNull(deciderOutcome);

        assertFalse(workflow.getTaskByRefName("s1").isRetried());
        assertEquals(1, deciderOutcome.tasksToBeUpdated.size());
        assertEquals("s1", deciderOutcome.tasksToBeUpdated.get(0).getReferenceTaskName());
        assertEquals(1, deciderOutcome.tasksToBeScheduled.size());
        assertEquals("s2", deciderOutcome.tasksToBeScheduled.get(0).getReferenceTaskName());
        assertFalse(deciderOutcome.isComplete);

        Task task2 = new Task();
        task2.setTaskType("junit_task_l2");
        task2.setReferenceTaskName("s2");
        task2.setSeq(2);
        task2.setRetried(false);
        task2.setExecuted(false);
        task2.setStatus(Status.COMPLETED);
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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowStatus.RUNNING);

        Task task1 = new Task();
        task1.setTaskType("junit_task_l1");
        task1.setReferenceTaskName("s1");
        task1.setSeq(1);
        task1.setIteration(1);
        task1.setRetried(false);
        task1.setExecuted(false);
        task1.setStatus(Status.COMPLETED);

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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowStatus.RUNNING);

        Task task = new Task();
        task.setTaskType("junit_task_l1");
        task.setReferenceTaskName("s1");
        task.setSeq(1);
        task.setRetried(false);
        task.setExecuted(false);
        task.setStatus(Status.FAILED);

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

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);
        workflow.setStatus(WorkflowStatus.RUNNING);

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("s1");
        workflowTask1.setTaskReferenceName("s1");
        workflowTask1.setType(TaskType.SIMPLE.name());
        workflowTask1.setTaskDefinition(new TaskDef("s1"));

        List<Task> tasksToBeScheduled = deciderService.getTasksToBeScheduled(workflow, workflowTask1, 0, null);
        assertNotNull(tasksToBeScheduled);
        assertEquals(1, tasksToBeScheduled.size());
        assertEquals("s1", tasksToBeScheduled.get(0).getReferenceTaskName());

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("s2");
        workflowTask2.setTaskReferenceName("s2");
        workflowTask2.setType(TaskType.SIMPLE.name());
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

        Task task = new Task();
        task.setTaskDefName("test_rt");
        task.setStatus(Status.IN_PROGRESS);
        task.setTaskId("aa");
        task.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        task.setUpdateTime(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(11));

        assertTrue(deciderService.isResponseTimedOut(taskDef, task));

        // verify that sub workflow tasks are not response timed out
        task.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);
        assertFalse(deciderService.isResponseTimedOut(taskDef, task));
    }

    @Test
    public void testFilterNextLoopOverTasks() {

        Workflow workflow = new Workflow();

        Task task1 = new Task();
        task1.setReferenceTaskName("task1");
        task1.setStatus(Status.COMPLETED);
        task1.setTaskId("task1");
        task1.setIteration(1);

        Task task2 = new Task();
        task2.setReferenceTaskName("task2");
        task2.setStatus(Status.SCHEDULED);
        task2.setTaskId("task2");

        Task task3 = new Task();
        task3.setReferenceTaskName("task3__1");
        task3.setStatus(Status.IN_PROGRESS);
        task3.setTaskId("task3__1");

        Task task4 = new Task();
        task4.setReferenceTaskName("task4");
        task4.setStatus(Status.SCHEDULED);
        task4.setTaskId("task4");

        Task task5 = new Task();
        task5.setReferenceTaskName("task5");
        task5.setStatus(Status.COMPLETED);
        task5.setTaskId("task5");

        workflow.getTasks().addAll(Arrays.asList(task1, task2, task3, task4, task5));
        List<Task> tasks = deciderService.filterNextLoopOverTasks(Arrays.asList(task2, task3, task4), task1, workflow);
        assertEquals(2, tasks.size());
        tasks.forEach(task -> {
            assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(1)));
            assertEquals(1, task.getIteration());
        });

    }

    @Test
    public void testPopulateWorkflowAndTaskData() {
        String workflowInputPath = "workflow/input/test.json";
        String taskInputPath = "task/input/test.json";
        String taskOutputPath = "task/output/test.json";
        Map<String, Object> workflowParams = new HashMap<>();
        workflowParams.put("key1", "value1");
        workflowParams.put("key2", 100);
        when(externalPayloadStorageUtils.downloadPayload(workflowInputPath)).thenReturn(workflowParams);
        Map<String, Object> taskInputParams = new HashMap<>();
        taskInputParams.put("key", "taskInput");
        when(externalPayloadStorageUtils.downloadPayload(taskInputPath)).thenReturn(taskInputParams);
        Map<String, Object> taskOutputParams = new HashMap<>();
        taskOutputParams.put("key", "taskOutput");
        when(externalPayloadStorageUtils.downloadPayload(taskOutputPath)).thenReturn(taskOutputParams);
        Task task = new Task();
        task.setExternalInputPayloadStoragePath(taskInputPath);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        Workflow workflow = new Workflow();
        workflow.setExternalInputPayloadStoragePath(workflowInputPath);
        workflow.getTasks().add(task);
        Workflow workflowInstance = deciderService.populateWorkflowAndTaskData(workflow);
        assertNotNull(workflowInstance);
        assertTrue(workflow.getInput().isEmpty());
        assertNotNull(workflowInstance.getInput());
        assertEquals(workflowParams, workflowInstance.getInput());
        assertTrue(workflow.getTasks().get(0).getInputData().isEmpty());
        assertNotNull(workflowInstance.getTasks().get(0).getInputData());
        assertEquals(taskInputParams, workflowInstance.getTasks().get(0).getInputData());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertNotNull(workflowInstance.getTasks().get(0).getOutputData());
        assertEquals(taskOutputParams, workflowInstance.getTasks().get(0).getOutputData());
        assertNull(workflowInstance.getExternalInputPayloadStoragePath());
        assertNull(workflowInstance.getTasks().get(0).getExternalInputPayloadStoragePath());
        assertNull(workflowInstance.getTasks().get(0).getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testUpdateWorkflowOutput() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(new WorkflowDef());
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().isEmpty());
        Task task = new Task();
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("taskKey", "taskValue");
        task.setOutputData(taskOutput);
        workflow.getTasks().add(task);
        WorkflowDef workflowDef = new WorkflowDef();
        when(metadataDAO.getWorkflowDef(anyString(), anyInt())).thenReturn(Optional.of(workflowDef));
        deciderService.updateWorkflowOutput(workflow, null);
        assertNotNull(workflow.getOutput());
        assertEquals("taskValue", workflow.getOutput().get("taskKey"));
    }

    @Test
    public void testCheckWorkflowTimeout() {
        Counter counter = registry.counter("workflow_failure", "class", "WorkflowMonitor", "workflowName", "test",
            "status", "TIMED_OUT", "ownerApp", "junit");
        assertEquals(0, counter.count());

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test");
        Workflow workflow = new Workflow();
        workflow.setOwnerApp("junit");
        workflow.setStartTime(System.currentTimeMillis() - 10_000);
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
        assertEquals(1, counter.count());

        // time out
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflow.setWorkflowDefinition(workflowDef);
        try {
            deciderService.checkWorkflowTimeout(workflow);
        } catch (TerminateWorkflowException twe) {
            assertTrue(twe.getMessage().contains("Workflow 'workflow_id' timed out"));
        }

        // for a retried workflow
        workflow.setLastRetriedTime(System.currentTimeMillis() - 5_000);
        try {
            deciderService.checkWorkflowTimeout(workflow);
        } catch (TerminateWorkflowException twe) {
            assertTrue(twe.getMessage().contains("Workflow 'workflow_id' timed out"));
        }
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
        decisionTask2.setType(TaskType.DECISION.name());
        decisionTask2.setCaseValueParam("case");
        decisionTask2.setName("conditional2");
        decisionTask2.setTaskReferenceName("conditional2");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("one", Arrays.asList(workflowTask1, workflowTask3));
        dc.put("two", Collections.singletonList(workflowTask2));
        decisionTask2.setDecisionCases(dc);
        decisionTask2.getInputParameters().put("case", "workflow.input.param2");


        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
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
        finalDecisionTask.setType(TaskType.DECISION.name());
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

    private Workflow createDefaultWorkflow() {

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("TestDeciderService");
        workflowDef.setVersion(1);

        Workflow workflow = new Workflow();
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

        Task task = new Task();
        task.setReferenceTaskName("task2");
        task.getOutputData().put("location", "http://location");
        task.setStatus(Status.COMPLETED);

        Task task2 = new Task();
        task2.setReferenceTaskName("task3");
        task2.getOutputData().put("refId", "abcddef_1234_7890_aaffcc");
        task2.setStatus(Status.SCHEDULED);

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
        decisionTask.setType(TaskType.DECISION.name());
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
        subWorkflow.setType(TaskType.SUB_WORKFLOW.name());
        subWorkflow.setName("sw1");
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowDef.getName());
        subWorkflow.setSubWorkflowParam(subWorkflowParams);
        subWorkflow.setTaskReferenceName("sw1");

        WorkflowTask forkTask2 = new WorkflowTask();
        forkTask2.setType(TaskType.FORK_JOIN.name());
        forkTask2.setName("second fork");
        forkTask2.setTaskReferenceName("fork2");
        forkTask2.getForkTasks().add(Arrays.asList(tasks.get(2), tasks.get(4)));
        forkTask2.getForkTasks().add(Arrays.asList(tasks.get(3), decisionTask));

        WorkflowTask joinTask2 = new WorkflowTask();
        joinTask2.setName("join2");
        joinTask2.setType(TaskType.JOIN.name());
        joinTask2.setTaskReferenceName("join2");
        joinTask2.setJoinOn(Arrays.asList("t4", "d1"));

        WorkflowTask forkTask1 = new WorkflowTask();
        forkTask1.setType(TaskType.FORK_JOIN.name());
        forkTask1.setName("fork1");
        forkTask1.setTaskReferenceName("fork1");
        forkTask1.getForkTasks().add(Collections.singletonList(tasks.get(1)));
        forkTask1.getForkTasks().add(Arrays.asList(forkTask2, joinTask2));
        forkTask1.getForkTasks().add(Collections.singletonList(subWorkflow));


        WorkflowTask joinTask1 = new WorkflowTask();
        joinTask1.setName("join1");
        joinTask1.setType(TaskType.JOIN.name());
        joinTask1.setTaskReferenceName("join1");
        joinTask1.setJoinOn(Arrays.asList("t1", "fork2"));

        workflowDef.getTasks().add(forkTask1);
        workflowDef.getTasks().add(joinTask1);
        workflowDef.getTasks().add(tasks.get(5));

        return workflowDef;
    }

}
