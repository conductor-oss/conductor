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
package com.netflix.conductor.tests.integration;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED_WITH_ERRORS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.FAILED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.TIMED_OUT;
import static com.netflix.conductor.common.metadata.workflow.TaskType.DECISION;
import static com.netflix.conductor.common.metadata.workflow.TaskType.SUB_WORKFLOW;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.RUNNING;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.TERMINATED;
import static com.netflix.conductor.tests.utils.MockExternalPayloadStorage.INITIAL_WORKFLOW_INPUT_PATH;
import static com.netflix.conductor.tests.utils.MockExternalPayloadStorage.INPUT_PAYLOAD_PATH;
import static com.netflix.conductor.tests.utils.MockExternalPayloadStorage.TASK_OUTPUT_PATH;
import static com.netflix.conductor.tests.utils.MockExternalPayloadStorage.TEMP_FILE_PATH;
import static com.netflix.conductor.tests.utils.MockExternalPayloadStorage.WORKFLOW_OUTPUT_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.WorkflowRepairService;
import com.netflix.conductor.core.execution.WorkflowSweeper;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.Terminate;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.tests.utils.UserTask;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractWorkflowServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWorkflowServiceTest.class);

    private static final String COND_TASK_WF = "ConditionalTaskWF";
    private static final String DECISION_WF = "DecisionWorkflow";

    private static final String FORK_JOIN_NESTED_WF = "FanInOutNestedTest";

    private static final String FORK_JOIN_WF = "FanInOutTest";
    private static final String FORK_JOIN_DECISION_WF = "ForkConditionalTest";

    private static final String DO_WHILE_WF = "DoWhileTest";

    private static final String DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest";

    private static final String DYNAMIC_FORK_JOIN_WF_LEGACY = "DynamicFanInOutTestLegacy";

    private static final int RETRY_COUNT = 1;
    private static final String JUNIT_TEST_WF_NON_RESTARTABLE = "junit_test_wf_non_restartable";
    private static final String WF_WITH_SUB_WF = "WorkflowWithSubWorkflow";
    private static final String WORKFLOW_WITH_OPTIONAL_TASK = "optional_task_wf";

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Inject
    protected ExecutionService workflowExecutionService;

    @Inject
    protected SubWorkflow subworkflow;

    @Inject
    protected UserTask userTask;

    @Inject
    protected MetadataService metadataService;

    @Inject
    protected WorkflowSweeper workflowSweeper;

    @Inject
    protected QueueDAO queueDAO;

    @Inject
    protected WorkflowExecutor workflowExecutor;

    @Inject
    protected WorkflowRepairService workflowRepairService;

    @Inject
    protected MetadataMapperService metadataMapperService;

    private ObjectMapper objectMapper = new JsonMapperProvider().get();

    SubWorkflow dummySubWorkflowSystemTask = new SubWorkflow(objectMapper);

    private static boolean registered;

    private static List<TaskDef> taskDefs;

    private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";

    private static final String LINEAR_WORKFLOW_T1_T2_SW = "junit_test_wf_sw";

    private static final String WORKFLOW_MULTI_LEVEL_SW = "junit_test_multi_level_sw";

    private static final String WORKFLOW_FORK_JOIN_OPTIONAL_SW = "junit_test_fork_join_optional_sw";

    private static final String LONG_RUNNING = "longRunningWf";

    private static final String TEST_WORKFLOW = "junit_test_wf3";

    private static final String CONDITIONAL_SYSTEM_WORKFLOW = "junit_conditional_http_wf";
    private static final String WF_T1_SWF_T2 = "junit_t1_sw_t2_wf";

    @BeforeClass
    public static void setup() {
        registered = false;
    }

    @Before
    public void init() {
        System.setProperty("EC2_REGION", "us-east-1");
        System.setProperty("EC2_AVAILABILITY_ZONE", "us-east-1c");
        if (registered) {
            return;
        }

        WorkflowContext.set(new WorkflowContext("junit_app"));
        for (int i = 0; i < 21; i++) {

            String name = "junit_task_" + i;
            if (notFoundSafeGetTaskDef(name) != null) {
                continue;
            }

            TaskDef task = new TaskDef();
            task.setName(name);
            task.setTimeoutSeconds(120);
            task.setRetryCount(RETRY_COUNT);
            metadataService.registerTaskDef(Collections.singletonList(task));
        }

        for (int i = 0; i < 5; i++) {

            String name = "junit_task_0_RT_" + i;
            if (notFoundSafeGetTaskDef(name) != null) {
                continue;
            }

            TaskDef task = new TaskDef();
            task.setName(name);
            task.setTimeoutSeconds(120);
            task.setRetryCount(0);
            metadataService.registerTaskDef(Collections.singletonList(task));
        }

        TaskDef task = new TaskDef();
        task.setName("short_time_out");
        task.setTimeoutSeconds(5);
        task.setRetryCount(RETRY_COUNT);
        metadataService.registerTaskDef(Collections.singletonList(task));

        WorkflowDef def = new WorkflowDef();
        def.setName(LINEAR_WORKFLOW_T1_T2);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${t2.output.uuid}");
        outputParameters.put("o3", "${t1.output.op}");
        def.setOutputParameters(outputParameters);
        def.setFailureWorkflow("$workflow.input.failureWfName");
        def.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        ip1.put("someNullKey", null);
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "${workflow.input.param1}");
        ip2.put("tp2", "${t1.output.op}");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        wftasks.add(wft1);
        wftasks.add(wft2);
        def.setTasks(wftasks);

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        Map<String, Object> ip3 = new HashMap<>();
        ip3.put("tp1", "${workflow.input.param1}");
        ip3.put("tp2", "${t1.output.op}");
        wft3.setInputParameters(ip3);
        wft3.setTaskReferenceName("t3");

        WorkflowDef def2 = new WorkflowDef();
        def2.setName(TEST_WORKFLOW);
        def2.setDescription(def2.getName());
        def2.setVersion(1);
        def2.setInputParameters(Arrays.asList("param1", "param2"));
        LinkedList<WorkflowTask> wftasks2 = new LinkedList<>();

        wftasks2.add(wft1);
        wftasks2.add(wft2);
        wftasks2.add(wft3);
        def2.setSchemaVersion(2);
        def2.setTasks(wftasks2);

        try {

            WorkflowDef[] wdsf = new WorkflowDef[]{def, def2};
            for (WorkflowDef wd : wdsf) {
                metadataService.updateWorkflowDef(wd);
            }
            createForkJoinWorkflow();
            def.setName(LONG_RUNNING);
            metadataService.updateWorkflowDef(def);
        } catch (Exception e) {
        }

        taskDefs = metadataService.getTaskDefs();
        registered = true;
    }

    private TaskDef notFoundSafeGetTaskDef(String name) {
        try {
            return metadataService.getTaskDef(name);
        } catch (ApplicationException e) {
            if (e.getCode() == ApplicationException.Code.NOT_FOUND) {
                return null;
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testWorkflowWithNoTasks() {

        WorkflowDef empty = new WorkflowDef();
        empty.setName("empty_workflow");
        empty.setSchemaVersion(2);
        metadataService.registerWorkflowDef(empty);

        String id = startOrLoadWorkflowExecution(empty.getName(), 1, "testWorkflowWithNoTasks", new HashMap<>(), null, null);
        assertNotNull(id);
        Workflow workflow = workflowExecutionService.getExecutionStatus(id, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(0, workflow.getTasks().size());
    }

    @Test
    public void testTaskDefTemplate() throws Exception {
        System.setProperty("STACK2", "test_stack");
        TaskDef templatedTask = new TaskDef();
        templatedTask.setName("templated_task");
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("method", "GET");
        httpRequest.put("vipStack", "${STACK2}");
        httpRequest.put("uri", "/get/something");
        Map<String, Object> body = new HashMap<>();
        body.put("inputPaths", Arrays.asList("${workflow.input.path1}", "${workflow.input.path2}"));
        body.put("requestDetails", "${workflow.input.requestDetails}");
        body.put("outputPath", "${workflow.input.outputPath}");
        httpRequest.put("body", body);
        templatedTask.getInputTemplate().put("http_request", httpRequest);
        metadataService.registerTaskDef(Arrays.asList(templatedTask));

        WorkflowDef templateWf = new WorkflowDef();
        templateWf.setName("template_workflow");
        WorkflowTask wft = new WorkflowTask();
        wft.setName(templatedTask.getName());
        wft.setWorkflowTaskType(TaskType.SIMPLE);
        wft.setTaskReferenceName("t0");
        templateWf.getTasks().add(wft);
        templateWf.setSchemaVersion(2);
        metadataService.registerWorkflowDef(templateWf);

        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("key1", "value1");
        requestDetails.put("key2", 42);

        Map<String, Object> input = new HashMap<>();
        input.put("path1", "file://path1");
        input.put("path2", "file://path2");
        input.put("outputPath", "s3://bucket/outputPath");
        input.put("requestDetails", requestDetails);

        String id = startOrLoadWorkflowExecution(templateWf.getName(), 1, "testTaskDefTemplate", input, null, null);
        assertNotNull(id);
        Workflow workflow = workflowExecutionService.getExecutionStatus(id, true);
        assertNotNull(workflow);
        assertTrue(workflow.getReasonForIncompletion(), !workflow.getStatus().isTerminal());
        assertEquals(1, workflow.getTasks().size());
        Task task = workflow.getTasks().get(0);
        Map<String, Object> taskInput = task.getInputData();
        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("http_request"));
        assertTrue(taskInput.get("http_request") instanceof Map);

        //Use the commented sysout to get the string value
        //System.out.println(om.writeValueAsString(om.writeValueAsString(taskInput)));
        String expected = "{\"http_request\":{\"method\":\"GET\",\"vipStack\":\"test_stack\",\"body\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]},\"uri\":\"/get/something\"}}";
        assertEquals(expected, objectMapper.writeValueAsString(taskInput));
    }

    @Test
    public void testKafkaTaskDefTemplateSuccess() throws Exception {

        try {
            registerKafkaWorkflow();
        } catch (ApplicationException e) {

        }

        Map<String, Object> input = getKafkaInput();
        String workflowInstanceId = startOrLoadWorkflowExecution("template_kafka_workflow", 1, "testTaskDefTemplate", input, null, null);

        assertNotNull(workflowInstanceId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);

        assertNotNull(workflow);
        assertTrue(workflow.getReasonForIncompletion(), !workflow.getStatus().isTerminal());
        assertEquals(1, workflow.getTasks().size());

        Task task = workflow.getTasks().get(0);
        Map<String, Object> taskInput = task.getInputData();

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("kafka_request"));
        assertTrue(taskInput.get("kafka_request") instanceof Map);

        String expected = "{\"kafka_request\":{\"topic\":\"test_kafka_topic\",\"bootStrapServers\":\"localhost:9092\",\"value\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]}}}";

        assertEquals(expected, objectMapper.writeValueAsString(taskInput));

        TaskResult taskResult = new TaskResult(task);

        taskResult.setStatus(TaskResult.Status.COMPLETED);


        // Polling for the first task
        Task task1 = workflowExecutionService.poll("KAFKA_PUBLISH", "test");
        assertNotNull(task1);

        assertTrue(workflowExecutionService.ackTaskReceived(task1.getTaskId()));
        assertEquals(workflowInstanceId, task1.getWorkflowInstanceId());

        workflowExecutionService.updateTask(taskResult);

        workflowExecutor.decide(workflowInstanceId);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testKafkaTaskDefTemplateFailure() throws Exception {

        try {
            registerKafkaWorkflow();
        } catch (ApplicationException e) {

        }
        Map<String, Object> input = getKafkaInput();
        String workflowInstanceId = startOrLoadWorkflowExecution("template_kafka_workflow", 1, "testTaskDefTemplate", input, null, null);

        assertNotNull(workflowInstanceId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);

        assertNotNull(workflow);
        assertTrue(workflow.getReasonForIncompletion(), !workflow.getStatus().isTerminal());
        assertEquals(1, workflow.getTasks().size());

        Task task = workflow.getTasks().get(0);
        Map<String, Object> taskInput = task.getInputData();

        assertNotNull(taskInput);
        assertTrue(taskInput.containsKey("kafka_request"));
        assertTrue(taskInput.get("kafka_request") instanceof Map);

        String expected = "{\"kafka_request\":{\"topic\":\"test_kafka_topic\",\"bootStrapServers\":\"localhost:9092\",\"value\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]}}}";

        assertEquals(expected, objectMapper.writeValueAsString(taskInput));

        TaskResult taskResult = new TaskResult(task);
        taskResult.setReasonForIncompletion("NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down");
        taskResult.setStatus(TaskResult.Status.FAILED);
        taskResult.addOutputData("TERMINAL_ERROR", "Integration endpoint down: FOOBAR");
        taskResult.addOutputData("ErrorMessage", "There was a terminal error");


        // Polling for the first task
        Task task1 = workflowExecutionService.poll("KAFKA_PUBLISH", "test");
        assertNotNull(task1);

        assertTrue(workflowExecutionService.ackTaskReceived(task1.getTaskId()));
        assertEquals(workflowInstanceId, task1.getWorkflowInstanceId());

        workflowExecutionService.updateTask(taskResult);

        workflowExecutor.decide(workflowInstanceId);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());
    }

    private Map<String, Object> getKafkaInput() {
        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("key1", "value1");
        requestDetails.put("key2", 42);

        Map<String, Object> input = new HashMap<>();
        input.put("path1", "file://path1");
        input.put("path2", "file://path2");
        input.put("outputPath", "s3://bucket/outputPath");
        input.put("requestDetails", requestDetails);
        return input;
    }

    private void registerKafkaWorkflow() {
        System.setProperty("STACK_KAFKA", "test_kafka_topic");
        TaskDef templatedTask = new TaskDef();
        templatedTask.setName("templated_kafka_task");
        templatedTask.setRetryCount(0);
        Map<String, Object> kafkaRequest = new HashMap<>();
        kafkaRequest.put("topic", "${STACK_KAFKA}");
        kafkaRequest.put("bootStrapServers", "localhost:9092");

        Map<String, Object> value = new HashMap<>();
        value.put("inputPaths", Arrays.asList("${workflow.input.path1}", "${workflow.input.path2}"));
        value.put("requestDetails", "${workflow.input.requestDetails}");
        value.put("outputPath", "${workflow.input.outputPath}");
        kafkaRequest.put("value", value);
        templatedTask.getInputTemplate().put("kafka_request", kafkaRequest);
        metadataService.registerTaskDef(Arrays.asList(templatedTask));

        WorkflowDef templateWf = new WorkflowDef();

        templateWf.setName("template_kafka_workflow");
        WorkflowTask wft = new WorkflowTask();
        wft.setName(templatedTask.getName());
        wft.setWorkflowTaskType(TaskType.KAFKA_PUBLISH);
        wft.setTaskReferenceName("t0");
        templateWf.getTasks().add(wft);
        templateWf.setSchemaVersion(2);
        metadataService.registerWorkflowDef(templateWf);
    }

    @Test
    public void testWorkflowSchemaVersion() {
        WorkflowDef ver2 = new WorkflowDef();
        ver2.setSchemaVersion(2);
        ver2.setName("Test_schema_version2");
        ver2.setVersion(1);

        WorkflowDef ver1 = new WorkflowDef();
        ver1.setName("Test_schema_version1");
        ver1.setVersion(1);

        metadataService.updateWorkflowDef(ver1);
        metadataService.updateWorkflowDef(ver2);

        WorkflowDef found = metadataService.getWorkflowDef(ver2.getName(), 1);
        assertEquals(2, found.getSchemaVersion());

        WorkflowDef found1 = metadataService.getWorkflowDef(ver1.getName(), 1);
        assertEquals(2, found1.getSchemaVersion());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testForkJoin() throws Exception {
        createForkJoinWorkflow();

        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(0);
        metadataService.updateTaskDef(taskDef);

        taskName = "junit_task_2";
        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(0);
        metadataService.updateTaskDef(taskDef);

        taskName = "junit_task_3";
        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(0);
        metadataService.updateTaskDef(taskDef);

        taskName = "junit_task_4";
        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(0);
        metadataService.updateTaskDef(taskDef);

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(FORK_JOIN_WF, 1, "fanouttest", input, null, null);
        assertNotNull(workflowId);
        printTaskStatuses(workflowId, "initiated");

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        Task task1 = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task1);
        assertTrue(workflowExecutionService.ackTaskReceived(task1.getTaskId()));

        Task task2 = workflowExecutionService.poll("junit_task_2", "test");
        assertNotNull(task2);
        assertTrue(workflowExecutionService.ackTaskReceived(task2.getTaskId()));

        Task task3 = workflowExecutionService.poll("junit_task_3", "test");
        assertNull(task3);

        task1.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());
        printTaskStatuses(workflow, "T1 completed");

        task3 = workflowExecutionService.poll("junit_task_3", "test");
        assertNotNull(task3);

        task2.setStatus(COMPLETED);
        task3.setStatus(COMPLETED);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<?> future1 = executorService.submit(() -> {
            try {
                workflowExecutionService.updateTask(task2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        future1.get();

        final Task _t3 = task3;
        Future<?> future2 = executorService.submit(() -> {
            try {
                workflowExecutionService.updateTask(_t3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        future2.get();

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        printTaskStatuses(workflow, "T2 T3 completed");
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 6, workflow.getTasks().size());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());
        assertTrue("Found  " + workflow.getTasks().stream().map(t -> t.getReferenceTaskName() + "." + t.getStatus()).collect(Collectors.toList()), workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t4")));

        Task t4 = workflowExecutionService.poll("junit_task_4", "test");
        assertNotNull(t4);
        t4.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t4);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
        printTaskStatuses(workflow, "All completed");
    }

    @Test
    public void testDoWhileSingleIteration() throws Exception {
        try {
            createDoWhileWorkflowWithIteration(1, false, true);
        } catch (Exception e) {
        }
        TaskDef taskDef = new TaskDef();
        taskDef.setName("http1");
        taskDef.setTimeoutSeconds(2);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef));

        TaskDef taskDef2 = new TaskDef();
        taskDef2.setName("http0");
        taskDef2.setTimeoutSeconds(2);
        taskDef2.setRetryCount(1);
        taskDef2.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef2.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef2));

        TaskDef taskDef1 = new TaskDef();
        taskDef1.setName("http2");
        taskDef1.setTimeoutSeconds(2);
        taskDef1.setRetryCount(1);
        taskDef1.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef1.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef1));

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(DO_WHILE_WF + "_1", 1, "looptest", input, null, null);
        System.out.println("testDoWhile.wfid=" + workflowId);
        printTaskStatuses(workflowId, "initiated");

        Task task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("FORK_JOIN", "test");
        assertNull(task); // fork task is completed

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("JOIN", "test");
        assertNull(task); // Both HTTP task completed.

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
        printTaskStatuses(workflow, "All completed");
    }

    @Test
    public void testDoWhileTwoIteration() throws Exception {
        try {
            createDoWhileWorkflowWithIteration(2, false, true);
        } catch (Exception e) {
        }

        TaskDef taskDef = new TaskDef();
        taskDef.setName("http1");
        taskDef.setTimeoutSeconds(5);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef));

        TaskDef taskDef2 = new TaskDef();
        taskDef2.setName("http0");
        taskDef2.setTimeoutSeconds(5);
        taskDef2.setRetryCount(1);
        taskDef2.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef2.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef2));

        TaskDef taskDef1 = new TaskDef();
        taskDef1.setName("http2");
        taskDef1.setTimeoutSeconds(5);
        taskDef1.setRetryCount(1);
        taskDef1.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef1.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef1));

        TaskDef taskDef3 = new TaskDef();
        taskDef1.setName("http3");
        taskDef1.setTimeoutSeconds(5);
        taskDef1.setRetryCount(1);
        taskDef1.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef1.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef3));

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(DO_WHILE_WF + "_2", 1, "looptest", input, null, null);
        System.out.println("testDoWhile.wfid=" + workflowId);
        printTaskStatuses(workflowId, "initiated");

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());

        Task task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("FORK_JOIN", "test");
        assertNull(task); // fork task is completed

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("JOIN", "test");
        assertNull(task); // Both HTTP task completed.

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("FORK_JOIN", "test");
        assertNull(task); // fork task is completed.

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("JOIN", "test");
        assertNull(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testLoopConditionWithInputParamter() throws Exception {
        try {
            createDoWhileWorkflowWithIteration(2, true, true);
        } catch (Exception e) {
        }

        TaskDef taskDef = new TaskDef();
        taskDef.setName("http1");
        taskDef.setTimeoutSeconds(2);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef));

        TaskDef taskDef2 = new TaskDef();
        taskDef2.setName("http0");
        taskDef2.setTimeoutSeconds(2);
        taskDef2.setRetryCount(1);
        taskDef2.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef2.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef2));

        TaskDef taskDef1 = new TaskDef();
        taskDef1.setName("http2");
        taskDef1.setTimeoutSeconds(2);
        taskDef1.setRetryCount(1);
        taskDef1.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef1.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef1));

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(DO_WHILE_WF + "_3", 1, "looptest", input, null, null);
        System.out.println("testDoWhile.wfid=" + workflowId);
        printTaskStatuses(workflowId, "initiated");

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());

        Task task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("FORK_JOIN", "test");
        assertNull(task); // fork task is completed

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("JOIN", "test");
        assertNull(task); // Both HTTP task completed.

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testLoopConditionWithInputParamterWithDef() throws Exception {
    	testLoopConditionWithInputParamter(true);
    }

    @Test
    public void testLoopConditionWithInputParamterNoDef() throws Exception {
    	testLoopConditionWithInputParamter(false);
    }

    private void testLoopConditionWithInputParamter(boolean useDef) throws Exception {
        try {
            createDoWhileWorkflowWithIteration(2, true, useDef);
        } catch (Exception e) {
        }

        TaskDef taskDef = new TaskDef();
        taskDef.setName("http1");
        taskDef.setTimeoutSeconds(2);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef));

        TaskDef taskDef2 = new TaskDef();
        taskDef2.setName("http0");
        taskDef2.setTimeoutSeconds(2);
        taskDef2.setRetryCount(1);
        taskDef2.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef2.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef2));

        TaskDef taskDef1 = new TaskDef();
        taskDef1.setName("http2");
        taskDef1.setTimeoutSeconds(2);
        taskDef1.setRetryCount(1);
        taskDef1.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef1.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Arrays.asList(taskDef1));

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(DO_WHILE_WF + "_3", 1, "looptest", input, null, null);
        System.out.println("testDoWhile.wfid=" + workflowId);
        printTaskStatuses(workflowId, "initiated");

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());

        Task task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("FORK_JOIN", "test");
        assertNull(task); // fork task is completed

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("HTTP", "test");
        assertNotNull(task);
        assertTrue(task.getReferenceTaskName().endsWith(TaskUtils.getLoopOverTaskRefNameSuffix(task.getIteration())));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("JOIN", "test");
        assertNull(task); // Both HTTP task completed.

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testForkJoinNestedSchemaVersion1() {
        createForkJoinNestedWorkflow(1);


        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");        //This should execute t16 and t19
        String wfid = startOrLoadWorkflowExecution("forkJoinNested", FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input, null, null);
        System.out.println("testForkJoinNested.wfid=" + wfid);

        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t11")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t12")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t13")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork1")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork2")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t1")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t2")));


        Task t1 = workflowExecutionService.poll("junit_task_11", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_12", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        Task t3 = workflowExecutionService.poll("junit_task_13", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t3.getTaskId()));

        assertNotNull(t1);
        assertNotNull(t2);
        assertNotNull(t3);

        t1.setStatus(COMPLETED);
        t2.setStatus(COMPLETED);
        t3.setStatus(COMPLETED);

        workflowExecutionService.updateTask(t1);
        workflowExecutionService.updateTask(t2);
        workflowExecutionService.updateTask(t3);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        wf = workflowExecutionService.getExecutionStatus(wfid, true);

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t14")));

        String[] tasks = new String[]{"junit_task_14", "junit_task_16"};
        for (String tt : tasks) {
            Task polled = workflowExecutionService.poll(tt, "test");
            assertNotNull("poll resulted empty for task: " + tt, polled);
            polled.setStatus(COMPLETED);
            workflowExecutionService.updateTask(polled);
        }

        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t19")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));        //Not there yet
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t20")));        //Not there yet

        Task task19 = workflowExecutionService.poll("junit_task_19", "test");
        assertNotNull(task19);
        task19.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task19);

        Task task20 = workflowExecutionService.poll("junit_task_20", "test");
        assertNotNull(task20);
        task20.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task20);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        Set<String> pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("join1")));

        pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));

        Task task15 = workflowExecutionService.poll("junit_task_15", "test");
        assertNotNull(task15);
        task15.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task15);

        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());

    }

    @Test
    public void testForkJoinNestedSchemaVersion2() {
        createForkJoinNestedWorkflow(2);

        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");        //This should execute t16 and t19
        String wfid = startOrLoadWorkflowExecution("forkJoinNested", FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input, null, null);
        System.out.println("testForkJoinNested.wfid=" + wfid);

        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t11")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t12")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t13")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork1")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork2")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t1")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t2")));


        Task t1 = workflowExecutionService.poll("junit_task_11", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_12", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        Task t3 = workflowExecutionService.poll("junit_task_13", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t3.getTaskId()));

        assertNotNull(t1);
        assertNotNull(t2);
        assertNotNull(t3);

        t1.setStatus(COMPLETED);
        t2.setStatus(COMPLETED);
        t3.setStatus(COMPLETED);

        workflowExecutionService.updateTask(t1);
        workflowExecutionService.updateTask(t2);
        workflowExecutionService.updateTask(t3);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        wf = workflowExecutionService.getExecutionStatus(wfid, true);

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t14")));

        String[] tasks = new String[]{"junit_task_14", "junit_task_16"};
        for (String tt : tasks) {
            Task polled = workflowExecutionService.poll(tt, "test");
            assertNotNull("poll resulted empty for task: " + tt, polled);
            polled.setStatus(COMPLETED);
            workflowExecutionService.updateTask(polled);
        }

        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t19")));
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));        //Not there yet
        assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t20")));        //Not there yet

        Task task19 = workflowExecutionService.poll("junit_task_19", "test");
        assertNotNull(task19);
        task19.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task19);

        Task task20 = workflowExecutionService.poll("junit_task_20", "test");
        assertNotNull(task20);
        task20.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task20);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        Set<String> pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("join1")));

        pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));

        Task task15 = workflowExecutionService.poll("junit_task_15", "test");
        assertNotNull(task15);
        task15.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task15);

        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
    }

    @Test
    public void testForkJoinNestedWithSubWorkflow() {

        createForkJoinNestedWorkflowWithSubworkflow(1);

        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");        //This should execute t16 and t19
        String wfid = startOrLoadWorkflowExecution(FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input, null, null);
        System.out.println("testForkJoinNested.wfid=" + wfid);

        Workflow workflow = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t11")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t12")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t13")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("sw1")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork1")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork2")));
        assertFalse(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertFalse(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t1")));
        assertFalse(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t2")));


        Task t1 = workflowExecutionService.poll("junit_task_11", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_12", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        Task t3 = workflowExecutionService.poll("junit_task_13", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t3.getTaskId()));

        assertNotNull(t1);
        assertNotNull(t2);
        assertNotNull(t3);

        t1.setStatus(COMPLETED);
        t2.setStatus(COMPLETED);
        t3.setStatus(COMPLETED);

        workflowExecutionService.updateTask(t1);
        workflowExecutionService.updateTask(t2);
        workflowExecutionService.updateTask(t3);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(wfid, true);

        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t14")));

        String[] tasks = new String[]{"junit_task_1", "junit_task_2", "junit_task_14", "junit_task_16"};
        for (String tt : tasks) {
            Task polled = workflowExecutionService.poll(tt, "test");
            assertNotNull("poll resulted empty for task: " + tt, polled);
            polled.setStatus(COMPLETED);
            workflowExecutionService.updateTask(polled);
        }

        // Execute again to complete the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        assertTrue(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t19")));
        assertFalse(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));        //Not there yet
        assertFalse(workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t20")));        //Not there yet

        Task task19 = workflowExecutionService.poll("junit_task_19", "test");
        assertNotNull(task19);
        task19.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task19);

        Task task20 = workflowExecutionService.poll("junit_task_20", "test");
        assertNotNull(task20);
        task20.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task20);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        workflow = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        Set<String> pendingTasks = workflow.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("join1")));

        pendingTasks = workflow.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
        assertTrue("Found only this: " + pendingTasks, workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));
        Task task15 = workflowExecutionService.poll("junit_task_15", "test");
        assertNotNull(task15);
        task15.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task15);

        workflow = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());

    }

    @Test
    public void testForkJoinFailure() {

        try {
            createForkJoinWorkflow();
        } catch (Exception e) {
        }

        String taskName = "junit_task_2";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        int retryCount = taskDef.getRetryCount();
        taskDef.setRetryCount(0);
        metadataService.updateTaskDef(taskDef);


        Map<String, Object> input = new HashMap<String, Object>();
        String wfid = startOrLoadWorkflowExecution(FORK_JOIN_WF, 1, "fanouttest", input, null, null);
        System.out.println("testForkJoinFailure.wfid=" + wfid);

        Task t1 = workflowExecutionService.poll("junit_task_2", "test");
        assertNotNull(t1);
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_1", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        Task t3 = workflowExecutionService.poll("junit_task_3", "test");
        assertNull(t3);

        assertNotNull(t1);
        assertNotNull(t2);
        t1.setStatus(FAILED);
        t2.setStatus(COMPLETED);

        workflowExecutionService.updateTask(t2);
        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals("Found " + wf.getTasks(), RUNNING, wf.getStatus());

        t3 = workflowExecutionService.poll("junit_task_3", "test");
        assertNotNull(t3);


        workflowExecutionService.updateTask(t1);
        wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals("Found " + wf.getTasks(), WorkflowStatus.FAILED, wf.getStatus());


        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(retryCount);
        metadataService.updateTaskDef(taskDef);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDynamicForkJoinLegacy() {
        try {
            createDynamicForkJoinWorkflowDefsLegacy(1);
        } catch (Exception e) {
        }

        Map<String, Object> input = new HashMap<String, Object>();
        String wfid = startOrLoadWorkflowExecution(DYNAMIC_FORK_JOIN_WF_LEGACY, 1, "dynfanouttest1", input, null, null);
        System.out.println("testDynamicForkJoinLegacy.wfid=" + wfid);

        Task t1 = workflowExecutionService.poll("junit_task_1", "test");
        //assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));

        DynamicForkJoinTaskList dynamicForkJoinTasks = new DynamicForkJoinTaskList();

        input = new HashMap<String, Object>();
        input.put("k1", "v1");
        dynamicForkJoinTasks.add("junit_task_2", null, "xdt1", input);

        HashMap<String, Object> input2 = new HashMap<String, Object>();
        input2.put("k2", "v2");
        dynamicForkJoinTasks.add("junit_task_3", null, "xdt2", input2);

        t1.getOutputData().put("dynamicTasks", dynamicForkJoinTasks);
        t1.setStatus(COMPLETED);

        workflowExecutionService.updateTask(t1);

        Task t2 = workflowExecutionService.poll("junit_task_2", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));
        assertEquals("xdt1", t2.getReferenceTaskName());
        assertTrue(t2.getInputData().containsKey("k1"));
        assertEquals("v1", t2.getInputData().get("k1"));
        Map<String, Object> output = new HashMap<String, Object>();
        output.put("ok1", "ov1");
        t2.setOutputData(output);
        t2.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t2);

        Task t3 = workflowExecutionService.poll("junit_task_3", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t3.getTaskId()));
        assertEquals("xdt2", t3.getReferenceTaskName());
        assertTrue(t3.getInputData().containsKey("k2"));
        assertEquals("v2", t3.getInputData().get("k2"));

        output = new HashMap<>();
        output.put("ok1", "ov1");
        t3.setOutputData(output);
        t3.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t3);

        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());

        // Check the output
        Task joinTask = wf.getTaskByRefName("dynamicfanouttask_join");
        assertEquals("Found:" + joinTask.getOutputData(), 2, joinTask.getOutputData().keySet().size());
        Set<String> joinTaskOutput = joinTask.getOutputData().keySet();
        System.out.println("joinTaskOutput=" + joinTaskOutput);
        for (String key : joinTask.getOutputData().keySet()) {
            assertTrue(key.equals("xdt1") || key.equals("xdt2"));
            assertEquals("ov1", ((Map<String, Object>) joinTask.getOutputData().get(key)).get("ok1"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDynamicForkJoin() {

        createDynamicForkJoinWorkflowDefs();

        String taskName = "junit_task_2";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        int retryCount = taskDef.getRetryCount();
        taskDef.setRetryCount(2);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setRetryLogic(RetryLogic.FIXED);
        metadataService.updateTaskDef(taskDef);

        Map<String, Object> workflowInput = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(DYNAMIC_FORK_JOIN_WF, 1, "dynfanouttest1", workflowInput, null, null);
        System.out.println("testDynamicForkJoin.wfid=" + workflowId);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        Task task1 = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task1);
        assertTrue(workflowExecutionService.ackTaskReceived(task1.getTaskId()));
        assertEquals("dt1", task1.getReferenceTaskName());

        Map<String, Object> inputParams2 = new HashMap<>();
        inputParams2.put("k1", "v1");
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        workflowTask2.setTaskReferenceName("xdt1");

        Map<String, Object> inputParams3 = new HashMap<>();
        inputParams3.put("k2", "v2");
        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_3");
        workflowTask3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", inputParams2);
        dynamicTasksInput.put("xdt2", inputParams3);
        task1.getOutputData().put("dynamicTasks", Arrays.asList(workflowTask2, workflowTask3));
        task1.getOutputData().put("dynamicTasksInput", dynamicTasksInput);
        task1.setStatus(COMPLETED);

        workflowExecutionService.updateTask(task1);
        workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 5, workflow.getTasks().size());

        Task task2 = workflowExecutionService.poll("junit_task_2", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(task2.getTaskId()));
        assertEquals("xdt1", task2.getReferenceTaskName());
        assertTrue(task2.getInputData().containsKey("k1"));
        assertEquals("v1", task2.getInputData().get("k1"));
        Map<String, Object> output = new HashMap<>();
        output.put("ok1", "ov1");
        task2.setOutputData(output);
        task2.setStatus(FAILED);
        workflowExecutionService.updateTask(task2);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_2")).count());
        assertTrue(workflow.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_2")).allMatch(t -> t.getWorkflowTask() != null));
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 6, workflow.getTasks().size());

        task2 = workflowExecutionService.poll("junit_task_2", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(task2.getTaskId()));
        assertEquals("xdt1", task2.getReferenceTaskName());
        assertTrue(task2.getInputData().containsKey("k1"));
        assertEquals("v1", task2.getInputData().get("k1"));
        task2.setOutputData(output);
        task2.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task2);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 6, workflow.getTasks().size());

        Task task3 = workflowExecutionService.poll("junit_task_3", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(task3.getTaskId()));
        assertEquals("xdt2", task3.getReferenceTaskName());
        assertTrue(task3.getInputData().containsKey("k2"));
        assertEquals("v2", task3.getInputData().get("k2"));
        output = new HashMap<>();
        output.put("ok1", "ov1");
        task3.setOutputData(output);
        task3.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task3);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 7, workflow.getTasks().size());

        Task task4 = workflowExecutionService.poll("junit_task_4", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(task4.getTaskId()));
        assertEquals("task4", task4.getReferenceTaskName());
        task4.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task4);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals("Found " + workflow.getTasks().stream().map(Task::getTaskType).collect(Collectors.toList()), 7, workflow.getTasks().size());

        // Check the output
        Task joinTask = workflow.getTaskByRefName("dynamicfanouttask_join");
        assertEquals("Found:" + joinTask.getOutputData(), 2, joinTask.getOutputData().keySet().size());
        Set<String> joinTaskOutput = joinTask.getOutputData().keySet();
        System.out.println("joinTaskOutput=" + joinTaskOutput);
        for (String key : joinTask.getOutputData().keySet()) {
            assertTrue(key.equals("xdt1") || key.equals("xdt2"));
            assertEquals("ov1", ((Map<String, Object>) joinTask.getOutputData().get(key)).get("ok1"));
        }

        // reset the task def
        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(retryCount);
        taskDef.setRetryDelaySeconds(1);
        metadataService.updateTaskDef(taskDef);
    }

    @Test
    public void testForkJoinDecisionWorkflow() {
        createForkJoinDecisionWorkflow();

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1");
        input.put("param2", "p2");
        input.put("case", "c");
        String workflowId = startOrLoadWorkflowExecution(FORK_JOIN_DECISION_WF, 1, "forkjoin_conditional", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());

        // poll task 10
        Task task = workflowExecutionService.poll("junit_task_10", "task10.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());

        // poll task 1
        task = workflowExecutionService.poll("junit_task_1", "task1.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());

        // poll task 2
        task = workflowExecutionService.poll("junit_task_2", "task2.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());

        // poll task 20
        task = workflowExecutionService.poll("junit_task_20", "task20.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(7, workflow.getTasks().size());
    }

    private void createForkJoinWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(FORK_JOIN_WF);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        // fork task
        WorkflowTask fanoutTask = new WorkflowTask();
        fanoutTask.setType(TaskType.FORK_JOIN.name());
        fanoutTask.setTaskReferenceName("fanouttask");

        Map<String, Object> inputParams1 = new HashMap<>();
        inputParams1.put("p1", "workflow.input.param1");
        inputParams1.put("p2", "workflow.input.param2");

        // left fork
        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        workflowTask1.setInputParameters(inputParams1);
        workflowTask1.setTaskReferenceName("t1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_3");
        workflowTask3.setInputParameters(inputParams1);
        workflowTask3.setTaskReferenceName("t3");

        // right fork
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        Map<String, Object> inputParams2 = new HashMap<>();
        inputParams2.put("tp1", "workflow.input.param1");
        workflowTask2.setInputParameters(inputParams2);
        workflowTask2.setTaskReferenceName("t2");

        fanoutTask.getForkTasks().add(Arrays.asList(workflowTask1, workflowTask3));
        fanoutTask.getForkTasks().add(Collections.singletonList(workflowTask2));
        workflowDef.getTasks().add(fanoutTask);

        // join task
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType(TaskType.JOIN.name());
        joinTask.setTaskReferenceName("fanouttask_join");
        joinTask.setJoinOn(Arrays.asList("t3", "t2"));

        workflowDef.getTasks().add(joinTask);

        // simple task
        WorkflowTask workflowTask4 = new WorkflowTask();
        workflowTask4.setName("junit_task_4");
        workflowTask4.setInputParameters(inputParams2);
        workflowTask4.setTaskReferenceName("t4");

        workflowDef.getTasks().add(workflowTask4);
        metadataService.updateWorkflowDef(workflowDef);
    }

    private void createForkJoinDecisionWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(FORK_JOIN_DECISION_WF);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("p1", "${workflow.input.param1}");
        inputParams.put("p2", "${workflow.input.param2}");

        // left decision
        WorkflowTask leftCaseTask1 = new WorkflowTask();
        leftCaseTask1.setName("junit_task_1");
        leftCaseTask1.setInputParameters(inputParams);
        leftCaseTask1.setTaskReferenceName("t1");

        WorkflowTask leftCaseTask2 = new WorkflowTask();
        leftCaseTask2.setName("junit_task_2");
        leftCaseTask2.setInputParameters(inputParams);
        leftCaseTask2.setTaskReferenceName("t2");

        // default decision
        WorkflowTask defaultCaseTask5 = new WorkflowTask();
        defaultCaseTask5.setName("junit_task_5");
        defaultCaseTask5.setInputParameters(inputParams);
        defaultCaseTask5.setTaskReferenceName("t5");

        // left fork
        // decision task
        Map<String, Object> decisionInput = new HashMap<>();
        decisionInput.put("case", "${workflow.input.case}");

        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setCaseValueParam("case");
        decisionTask.setName("decisionTask");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setInputParameters(decisionInput);
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("c", Arrays.asList(leftCaseTask1, leftCaseTask2));
        decisionTask.setDefaultCase(Collections.singletonList(defaultCaseTask5));
        decisionTask.setDecisionCases(decisionCases);

        WorkflowTask workflowTask20 = new WorkflowTask();
        workflowTask20.setName("junit_task_20");
        workflowTask20.setInputParameters(inputParams);
        workflowTask20.setTaskReferenceName("t20");

        // right fork
        WorkflowTask rightForkTask10 = new WorkflowTask();
        rightForkTask10.setName("junit_task_10");
        rightForkTask10.setInputParameters(inputParams);
        rightForkTask10.setTaskReferenceName("t10");

        // fork task
        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setName("forkTask");
        forkTask.setType(TaskType.FORK_JOIN.name());
        forkTask.setTaskReferenceName("forkTask");
        forkTask.getForkTasks().add(Arrays.asList(decisionTask, workflowTask20));
        forkTask.getForkTasks().add(Collections.singletonList(rightForkTask10));

        // join task
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("joinTask");
        joinTask.setType(TaskType.JOIN.name());
        joinTask.setTaskReferenceName("joinTask");
        joinTask.setJoinOn(Arrays.asList("t20", "t10"));

        workflowDef.getTasks().add(forkTask);
        workflowDef.getTasks().add(joinTask);

        metadataService.updateWorkflowDef(workflowDef);
    }

    @Test
    public void testDecisionWorkflow() {
        createDecisionWorkflow();

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1");
        input.put("param2", "p2");
        input.put("case", "c");
        String workflowId = startOrLoadWorkflowExecution(DECISION_WF, 1, "decision", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        // poll task 1
        Task task = workflowExecutionService.poll("junit_task_1", "task1.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());

        // poll task 2
        task = workflowExecutionService.poll("junit_task_2", "task2.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        // poll task 20
        task = workflowExecutionService.poll("junit_task_20", "task20.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // update to COMPLETED
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    private void createDecisionWorkflow() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(DECISION_WF);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("p1", "${workflow.input.param1}");
        inputParams.put("p2", "${workflow.input.param2}");

        // left decision
        WorkflowTask leftCaseTask1 = new WorkflowTask();
        leftCaseTask1.setName("junit_task_1");
        leftCaseTask1.setInputParameters(inputParams);
        leftCaseTask1.setTaskReferenceName("t1");

        WorkflowTask leftCaseTask2 = new WorkflowTask();
        leftCaseTask2.setName("junit_task_2");
        leftCaseTask2.setInputParameters(inputParams);
        leftCaseTask2.setTaskReferenceName("t2");

        // default decision
        WorkflowTask defaultCaseTask5 = new WorkflowTask();
        defaultCaseTask5.setName("junit_task_5");
        defaultCaseTask5.setInputParameters(inputParams);
        defaultCaseTask5.setTaskReferenceName("t5");

        // decision task
        Map<String, Object> decisionInput = new HashMap<>();
        decisionInput.put("case", "${workflow.input.case}");
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setCaseValueParam("case");
        decisionTask.setName("decisionTask");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setInputParameters(decisionInput);
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("c", Arrays.asList(leftCaseTask1, leftCaseTask2));
        decisionTask.setDefaultCase(Collections.singletonList(defaultCaseTask5));
        decisionTask.setDecisionCases(decisionCases);

        WorkflowTask workflowTask20 = new WorkflowTask();
        workflowTask20.setName("junit_task_20");
        workflowTask20.setInputParameters(inputParams);
        workflowTask20.setTaskReferenceName("t20");

        workflowDef.getTasks().add(decisionTask);
        workflowDef.getTasks().add(workflowTask20);

        metadataService.updateWorkflowDef(workflowDef);
    }

    private void createDoWhileWorkflowWithIteration(int iteration, boolean isInputParameter, boolean useTaskDef) {
        WorkflowDef workflowDef = new WorkflowDef();
        if (isInputParameter) {
            workflowDef.setName(DO_WHILE_WF + "_3");
        } else {
            workflowDef.setName(DO_WHILE_WF + "_" + iteration);
        }
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask loopTask = new WorkflowTask();
        loopTask.setType(TaskType.DO_WHILE.name());
        loopTask.setTaskReferenceName("loopTask");
        loopTask.setName("loopTask");
        loopTask.setWorkflowTaskType(TaskType.DO_WHILE);
        Map<String, Object> input = new HashMap<>();
        input.put("value", "${workflow.input.loop}");
        loopTask.setInputParameters(input);

        if(useTaskDef) {
	        TaskDef taskDef = new TaskDef();
	        taskDef.setName("loopTask");
	        taskDef.setTimeoutSeconds(200);
	        taskDef.setRetryCount(1);
	        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
	        taskDef.setRetryDelaySeconds(10);
	        metadataService.registerTaskDef(Arrays.asList(taskDef));
        }

        Map<String, Object> inputParams1 = new HashMap<>();
        inputParams1.put("p1", "workflow.input.param1");
        inputParams1.put("p2", "workflow.input.param2");

        WorkflowTask http1 = new WorkflowTask();
        http1.setName("http1");
        http1.setInputParameters(inputParams1);
        http1.setTaskReferenceName("http1");
        http1.setWorkflowTaskType(TaskType.HTTP);

        WorkflowTask http2 = new WorkflowTask();
        http2.setName("http2");
        http2.setInputParameters(inputParams1);
        http2.setTaskReferenceName("http2");
        http2.setWorkflowTaskType(TaskType.HTTP);

        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setInputParameters(inputParams1);
        fork.setTaskReferenceName("fork");
        fork.setWorkflowTaskType(TaskType.FORK_JOIN);
        fork.setForkTasks(Arrays.asList(Arrays.asList(http1),Arrays.asList(http2)));

        WorkflowTask join = new WorkflowTask();
        join.setName("join");
        join.setInputParameters(inputParams1);
        join.setTaskReferenceName("join");
        join.setWorkflowTaskType(TaskType.JOIN);

        WorkflowTask http0 = new WorkflowTask();
        http0.setName("http0");
        http0.setInputParameters(inputParams1);
        http0.setTaskReferenceName("http0");
        http0.setWorkflowTaskType(TaskType.HTTP);

        loopTask.getLoopOver().add(http0);
        loopTask.getLoopOver().add(fork);
        loopTask.getLoopOver().add(join);
        if (isInputParameter) {
            loopTask.setLoopCondition("if ($.loopTask['iteration'] < $.value) { true; } else { false; }");
        } else {
            loopTask.setLoopCondition("if ($.loopTask['iteration'] < " + iteration + " ) { true;} else {false;} ");
        }

        workflowDef.getTasks().add(loopTask);

        if (iteration == 2 && isInputParameter == false) {
        	if(useTaskDef) {
	            TaskDef taskDef2 = new TaskDef();
	            taskDef2.setName("loopTask2");
	            taskDef2.setTimeoutSeconds(200);
	            taskDef2.setRetryCount(3);
	            taskDef2.setTimeoutPolicy(TimeoutPolicy.RETRY);
	            taskDef2.setRetryDelaySeconds(10);
	            metadataService.registerTaskDef(Arrays.asList(taskDef2));
        	}
            WorkflowTask loopTask2 = new WorkflowTask();
            loopTask2.setType(TaskType.DO_WHILE.name());
            loopTask2.setTaskReferenceName("loopTask2");
            loopTask2.setName("loopTask2");
            loopTask2.setWorkflowTaskType(TaskType.DO_WHILE);
            loopTask2.setInputParameters(input);
            WorkflowTask http3 = new WorkflowTask();
            http3.setName("http3");
            http3.setInputParameters(inputParams1);
            http3.setTaskReferenceName("http3");
            http3.setWorkflowTaskType(TaskType.HTTP);
            loopTask2.setLoopCondition("if ($.loopTask2['iteration'] < 1) { true; } else { false; }");
            loopTask2.getLoopOver().add(http3);
            workflowDef.getTasks().add(loopTask2);
        }
        metadataService.registerWorkflowDef(workflowDef);
    }


    private void createForkJoinWorkflowWithZeroRetry() {
        WorkflowDef def = new WorkflowDef();
        def.setName(FORK_JOIN_WF + "_2");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask fanout = new WorkflowTask();
        fanout.setType(TaskType.FORK_JOIN.name());
        fanout.setTaskReferenceName("fanouttask");

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_0_RT_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_0_RT_3");
        wft3.setInputParameters(ip1);
        wft3.setTaskReferenceName("t3");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_0_RT_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "workflow.input.param1");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        WorkflowTask wft4 = new WorkflowTask();
        wft4.setName("junit_task_0_RT_4");
        wft4.setInputParameters(ip2);
        wft4.setTaskReferenceName("t4");

        fanout.getForkTasks().add(Arrays.asList(wft1, wft3));
        fanout.getForkTasks().add(Arrays.asList(wft2));

        def.getTasks().add(fanout);

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("fanouttask_join");
        join.setJoinOn(Arrays.asList("t3", "t2"));

        def.getTasks().add(join);
        def.getTasks().add(wft4);
        metadataService.updateWorkflowDef(def);
    }

    private void createForkJoinNestedWorkflow(int schemaVersion) {
        WorkflowDef def = new WorkflowDef();
        def.setName(FORK_JOIN_NESTED_WF);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setSchemaVersion(schemaVersion);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask[] tasks = new WorkflowTask[21];

        Map<String, Object> ip1 = new HashMap<>();
        if (schemaVersion <= 1) {
            ip1.put("p1", "workflow.input.param1");
            ip1.put("p2", "workflow.input.param2");
            ip1.put("case", "workflow.input.case");
        } else {
            ip1.put("p1", "${workflow.input.param1}");
            ip1.put("p2", "${workflow.input.param2}");
            ip1.put("case", "${workflow.input.case}");
        }

        for (int i = 10; i < 21; i++) {
            WorkflowTask wft = new WorkflowTask();
            wft.setName("junit_task_" + i);
            wft.setInputParameters(ip1);
            wft.setTaskReferenceName("t" + i);
            tasks[i] = wft;
        }

        WorkflowTask d1 = new WorkflowTask();
        d1.setType(TaskType.DECISION.name());
        d1.setName("Decision");
        d1.setTaskReferenceName("d1");
        d1.setInputParameters(ip1);
        d1.setDefaultCase(Arrays.asList(tasks[18], tasks[20]));
        d1.setCaseValueParam("case");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("a", Arrays.asList(tasks[16], tasks[19], tasks[20]));
        decisionCases.put("b", Arrays.asList(tasks[17], tasks[20]));
        d1.setDecisionCases(decisionCases);

        WorkflowTask fork2 = new WorkflowTask();
        fork2.setType(TaskType.FORK_JOIN.name());
        fork2.setName("fork2");
        fork2.setTaskReferenceName("fork2");
        fork2.getForkTasks().add(Arrays.asList(tasks[12], tasks[14]));
        fork2.getForkTasks().add(Arrays.asList(tasks[13], d1));

        WorkflowTask join2 = new WorkflowTask();
        join2.setType(TaskType.JOIN.name());
        join2.setTaskReferenceName("join2");
        join2.setJoinOn(Arrays.asList("t14", "t20"));

        WorkflowTask fork1 = new WorkflowTask();
        fork1.setType(TaskType.FORK_JOIN.name());
        fork1.setTaskReferenceName("fork1");
        fork1.getForkTasks().add(Arrays.asList(tasks[11]));
        fork1.getForkTasks().add(Arrays.asList(fork2, join2));

        WorkflowTask join1 = new WorkflowTask();
        join1.setType(TaskType.JOIN.name());
        join1.setTaskReferenceName("join1");
        join1.setJoinOn(Arrays.asList("t11", "join2"));

        def.getTasks().add(fork1);
        def.getTasks().add(join1);
        def.getTasks().add(tasks[15]);

        metadataService.updateWorkflowDef(def);
    }

    private void createForkJoinNestedWorkflowWithSubworkflow(int schemaVersion) {
        WorkflowDef def = new WorkflowDef();
        def.setName(FORK_JOIN_NESTED_WF);
        def.setDescription(def.getName());
        def.setSchemaVersion(1);
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        ip1.put("case", "workflow.input.case");

        WorkflowTask[] tasks = new WorkflowTask[21];

        for (int i = 10; i < 21; i++) {
            WorkflowTask wft = new WorkflowTask();
            wft.setName("junit_task_" + i);
            wft.setInputParameters(ip1);
            wft.setTaskReferenceName("t" + i);
            tasks[i] = wft;
        }

        WorkflowTask d1 = new WorkflowTask();
        d1.setType(TaskType.DECISION.name());
        d1.setName("Decision");
        d1.setTaskReferenceName("d1");
        d1.setInputParameters(ip1);
        d1.setDefaultCase(Arrays.asList(tasks[18], tasks[20]));
        d1.setCaseValueParam("case");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("a", Arrays.asList(tasks[16], tasks[19], tasks[20]));
        decisionCases.put("b", Arrays.asList(tasks[17], tasks[20]));
        d1.setDecisionCases(decisionCases);

        WorkflowTask subWorkflow = new WorkflowTask();
        subWorkflow.setType(SUB_WORKFLOW.name());
        SubWorkflowParams sw = new SubWorkflowParams();
        sw.setName(LINEAR_WORKFLOW_T1_T2);
        subWorkflow.setSubWorkflowParam(sw);
        subWorkflow.setTaskReferenceName("sw1");

        WorkflowTask fork2 = new WorkflowTask();
        fork2.setType(TaskType.FORK_JOIN.name());
        fork2.setName("fork2");
        fork2.setTaskReferenceName("fork2");
        fork2.getForkTasks().add(Arrays.asList(tasks[12], tasks[14]));
        fork2.getForkTasks().add(Arrays.asList(tasks[13], d1));

        WorkflowTask join2 = new WorkflowTask();
        join2.setType(TaskType.JOIN.name());
        join2.setTaskReferenceName("join2");
        join2.setJoinOn(Arrays.asList("t14", "t20"));

        WorkflowTask fork1 = new WorkflowTask();
        fork1.setType(TaskType.FORK_JOIN.name());
        fork1.setTaskReferenceName("fork1");
        fork1.getForkTasks().add(Arrays.asList(tasks[11]));
        fork1.getForkTasks().add(Arrays.asList(fork2, join2));
        fork1.getForkTasks().add(Arrays.asList(subWorkflow));


        WorkflowTask join1 = new WorkflowTask();
        join1.setType(TaskType.JOIN.name());
        join1.setTaskReferenceName("join1");
        join1.setJoinOn(Arrays.asList("t11", "join2", "sw1"));

        def.getTasks().add(fork1);
        def.getTasks().add(join1);
        def.getTasks().add(tasks[15]);

        metadataService.updateWorkflowDef(def);
    }

    private void createDynamicForkJoinWorkflowDefs() {

        WorkflowDef def = new WorkflowDef();
        def.setName(DYNAMIC_FORK_JOIN_WF);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setSchemaVersion(2);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        workflowTask1.setInputParameters(ip1);
        workflowTask1.setTaskReferenceName("dt1");

        WorkflowTask fanout = new WorkflowTask();
        fanout.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        fanout.setTaskReferenceName("dynamicfanouttask");
        fanout.setDynamicForkTasksParam("dynamicTasks");
        fanout.setDynamicForkTasksInputParamName("dynamicTasksInput");
        fanout.getInputParameters().put("dynamicTasks", "${dt1.output.dynamicTasks}");
        fanout.getInputParameters().put("dynamicTasksInput", "${dt1.output.dynamicTasksInput}");

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamicfanouttask_join");

        WorkflowTask workflowTask4 = new WorkflowTask();
        workflowTask4.setName("junit_task_4");
        workflowTask4.setTaskReferenceName("task4");

        def.getTasks().add(workflowTask1);
        def.getTasks().add(fanout);
        def.getTasks().add(join);
        def.getTasks().add(workflowTask4);

        metadataMapperService.populateTaskDefinitions(def);

        metadataService.updateWorkflowDef(def);
    }

    @SuppressWarnings("deprecation")
    private void createDynamicForkJoinWorkflowDefsLegacy(int schemaVersion) {
        WorkflowDef def = new WorkflowDef();
        def.setName(DYNAMIC_FORK_JOIN_WF_LEGACY);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setSchemaVersion(schemaVersion);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        if (schemaVersion <= 1) {
            ip1.put("p1", "workflow.input.param1");
            ip1.put("p2", "workflow.input.param2");
        } else {
            ip1.put("p1", "${workflow.input.param1}");
            ip1.put("p2", "${workflow.input.param2}");
        }

        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("dt1");

        WorkflowTask fanout = new WorkflowTask();
        fanout.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        fanout.setTaskReferenceName("dynamicfanouttask");
        fanout.setDynamicForkJoinTasksParam("dynamicTasks");
        if (schemaVersion <= 1) {
            fanout.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
            fanout.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");
        } else {
            fanout.getInputParameters().put("dynamicTasks", "${dt1.output.dynamicTasks}");
            fanout.getInputParameters().put("dynamicTasksInput", "${dt1.output.dynamicTasksInput}");
        }
        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamicfanouttask_join");

        def.getTasks().add(wft1);
        def.getTasks().add(fanout);
        def.getTasks().add(join);

        metadataMapperService.populateTaskDefinitions(def);

        metadataService.updateWorkflowDef(def);

    }

    private void createConditionalWF(int schemaVersion) {
        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();

        if (schemaVersion <= 1) {
            ip1.put("p1", "workflow.input.param1");
            ip1.put("p2", "workflow.input.param2");
        } else {
            ip1.put("p1", "${workflow.input.param1}");
            ip1.put("p2", "${workflow.input.param2}");
        }

        workflowTask1.setInputParameters(ip1);
        workflowTask1.setTaskReferenceName("t1");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        if (schemaVersion <= 1) {
            ip2.put("tp1", "workflow.input.param1");
        } else {
            ip2.put("tp1", "${workflow.input.param1}");
        }
        workflowTask2.setInputParameters(ip2);
        workflowTask2.setTaskReferenceName("t2");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_3");
        Map<String, Object> ip3 = new HashMap<>();
        ip3.put("tp3", "workflow.input.param2");
        workflowTask3.setInputParameters(ip3);
        workflowTask3.setTaskReferenceName("t3");

        WorkflowTask workflowTask10 = new WorkflowTask();
        workflowTask10.setName("junit_task_10");
        Map<String, Object> ip10 = new HashMap<>();
        ip10.put("tp10", "workflow.input.param2");
        workflowTask10.setInputParameters(ip10);
        workflowTask10.setTaskReferenceName("t10");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(COND_TASK_WF);
        workflowDef.setDescription(COND_TASK_WF);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask nestedCondition = new WorkflowTask();
        nestedCondition.setType(TaskType.DECISION.name());
        nestedCondition.setCaseValueParam("case");
        nestedCondition.setName("nestedCondition");
        nestedCondition.setTaskReferenceName("nestedCondition");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("one", Collections.singletonList(workflowTask1));
        dc.put("two", Collections.singletonList(workflowTask2));
        nestedCondition.setDecisionCases(dc);

        WorkflowTask condition = new WorkflowTask();
        Map<String, Object> finalCaseInput = new HashMap<>();

        if (schemaVersion <= 1) {
            condition.getInputParameters().put("case", "workflow.input.param1");
            nestedCondition.getInputParameters().put("case", "workflow.input.param2");
            finalCaseInput.put("finalCase", "workflow.input.finalCase");
        } else {
            condition.getInputParameters().put("case", "${workflow.input.param1}");
            nestedCondition.getInputParameters().put("case", "${workflow.input.param2}");
            finalCaseInput.put("finalCase", "${workflow.input.finalCase}");
        }

        condition.setType(TaskType.DECISION.name());
        condition.setCaseValueParam("case");
        condition.setName("conditional");
        condition.setTaskReferenceName("conditional");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("nested", Collections.singletonList(nestedCondition));
        decisionCases.put("three", Collections.singletonList(workflowTask3));
        condition.setDecisionCases(decisionCases);
        condition.getDefaultCase().add(workflowTask10);
        workflowDef.getTasks().add(condition);

        WorkflowTask notifyTask = new WorkflowTask();
        notifyTask.setName("junit_task_4");
        notifyTask.setTaskReferenceName("junit_task_4");

        WorkflowTask finalTask = new WorkflowTask();
        finalTask.setName("finalcondition");
        finalTask.setTaskReferenceName("finalCase");
        finalTask.setType(TaskType.DECISION.name());
        finalTask.setCaseValueParam("finalCase");
        finalTask.setInputParameters(finalCaseInput);
        finalTask.getDecisionCases().put("notify", Collections.singletonList(notifyTask));

        workflowDef.setSchemaVersion(schemaVersion);
        workflowDef.getTasks().add(finalTask);
        metadataService.updateWorkflowDef(workflowDef);
    }

    @Test
    public void testForkJoinWithOptionalSubworkflows() {
        createForkJoinWorkflowWithOptionalSubworkflowForks();

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(WORKFLOW_FORK_JOIN_OPTIONAL_SW, 1, "", workflowInput, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals("found " + workflow.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 4, workflow.getTasks().size());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId1 = workflow.getTaskByRefName("st1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId1, 1);
        String subWorkflowTaskId2 = workflow.getTaskByRefName("st2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId2, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        String subWorkflowId1 = workflow.getTasks().get(1).getSubWorkflowId();
        Workflow subWorkflow1 = workflowExecutionService.getExecutionStatus(subWorkflowId1, true);
        assertNotNull(subWorkflow1);
        assertEquals(RUNNING, subWorkflow1.getStatus());
        assertEquals(1, subWorkflow1.getTasks().size());

        String subWorkflowId2 = workflow.getTasks().get(2).getSubWorkflowId();
        Workflow subWorkflow2 = workflowExecutionService.getExecutionStatus(subWorkflowId2, true);
        assertNotNull(subWorkflow2);
        assertEquals(RUNNING, subWorkflow2.getStatus());
        assertEquals(1, subWorkflow2.getTasks().size());

        // fail sub-workflow 1
        Task task = new Task();
        while (!subWorkflowId1.equals(task.getWorkflowInstanceId())) {
            task = workflowExecutionService.poll("simple_task_in_sub_wf", "junit.worker");
        }
        assertNotNull(task);
        assertEquals("simple_task_in_sub_wf", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(subWorkflowId1, task.getWorkflowInstanceId());

        TaskResult taskResult = new TaskResult(task);
        taskResult.setReasonForIncompletion("fail task 1");
        taskResult.setStatus(TaskResult.Status.FAILED);
        workflowExecutionService.updateTask(taskResult);

        subWorkflow1 = workflowExecutionService.getExecutionStatus(subWorkflowId1, true);
        assertNotNull(subWorkflow1);
        assertEquals(WorkflowStatus.FAILED, subWorkflow1.getStatus());

        subWorkflow2 = workflowExecutionService.getExecutionStatus(subWorkflowId2, true);
        assertNotNull(subWorkflow2);
        assertEquals(RUNNING, subWorkflow2.getStatus());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId1, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(COMPLETED_WITH_ERRORS, workflow.getTasks().get(1).getStatus());
        assertEquals(IN_PROGRESS, workflow.getTasks().get(2).getStatus());

        // fail sub workflow 2
        task = new Task();
        while (!subWorkflowId2.equals(task.getWorkflowInstanceId())) {
            task = workflowExecutionService.poll("simple_task_in_sub_wf", "junit.worker");
        }
        assertNotNull(task);
        assertEquals("simple_task_in_sub_wf", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(subWorkflowId2, task.getWorkflowInstanceId());

        taskResult = new TaskResult(task);
        taskResult.setReasonForIncompletion("fail task 2");
        taskResult.setStatus(TaskResult.Status.FAILED);
        workflowExecutionService.updateTask(taskResult);

        subWorkflow1 = workflowExecutionService.getExecutionStatus(subWorkflowId1, true);
        assertNotNull(subWorkflow1);
        assertEquals(WorkflowStatus.FAILED, subWorkflow1.getStatus());

        subWorkflow2 = workflowExecutionService.getExecutionStatus(subWorkflowId2, true);
        assertNotNull(subWorkflow2);
        assertEquals(WorkflowStatus.FAILED, subWorkflow2.getStatus());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId2, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(COMPLETED_WITH_ERRORS, workflow.getTasks().get(1).getStatus());
        assertEquals(COMPLETED_WITH_ERRORS, workflow.getTasks().get(2).getStatus());
    }


    @Test
    public void testDefDAO() {
        List<TaskDef> taskDefs = metadataService.getTaskDefs();
        assertNotNull(taskDefs);
        assertFalse(taskDefs.isEmpty());
    }

    @Test
    public void testSimpleWorkflowFailureWithTerminalError() throws Exception {
        clearWorkflows();

        TaskDef taskDef = notFoundSafeGetTaskDef("junit_task_1");
        taskDef.setRetryCount(1);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(workflowDef);
        Map<String, Object> outputParameters = workflowDef.getOutputParameters();
        outputParameters.put("validationErrors", "${t1.output.ErrorMessage}");
        metadataService.updateWorkflowDef(workflowDef);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        String workflowInstanceId = startOrLoadWorkflowExecution("simpleWorkflowFailureWithTerminalError", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(workflowInstanceId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        boolean failed = false;
        try {
            workflowExecutor.rewind(workflowInstanceId, false);
        } catch (ApplicationException ae) {
            failed = true;
        }
        assertTrue(failed);

        // Polling for the first task should return the same task as before
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowInstanceId, task.getWorkflowInstanceId());

        TaskResult taskResult = new TaskResult(task);
        taskResult.setReasonForIncompletion("NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down");
        taskResult.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        taskResult.addOutputData("TERMINAL_ERROR", "Integration endpoint down: FOOBAR");
        taskResult.addOutputData("ErrorMessage", "There was a terminal error");

        workflowExecutionService.updateTask(taskResult);
        workflowExecutor.decide(workflowInstanceId);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        TaskDef junit_task_1 = notFoundSafeGetTaskDef("junit_task_1");
        Task t1 = workflow.getTaskByRefName("t1");
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals("NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down", workflow.getReasonForIncompletion());
        assertEquals(1, junit_task_1.getRetryCount()); //Configured retries at the task definition level
        assertEquals(0, t1.getRetryCount()); //Actual retries done on the task
        assertTrue(workflow.getOutput().containsKey("o1"));
        assertEquals("p1 value", workflow.getOutput().get("o1"));
        assertEquals(workflow.getOutput().get("validationErrors").toString(), "There was a terminal error");

        outputParameters.remove("validationErrors");
        metadataService.updateWorkflowDef(workflowDef);
    }

    @Test
    public void testSimpleWorkflow() throws Exception {

        clearWorkflows();

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowInstanceId = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        logger.info("testSimpleWorkflow.wfid= {}", workflowInstanceId);
        assertNotNull(workflowInstanceId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        boolean failed = false;
        try {
            workflowExecutor.rewind(workflowInstanceId, false);
        } catch (ApplicationException ae) {
            failed = true;
        }
        assertTrue(failed);

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowInstanceId, task.getWorkflowInstanceId());

        workflowExecutor.decide(workflowInstanceId);

        String task1Op = "task1.Done";
        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 1);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        task = tasks.get(0);
        assertEquals(workflowInstanceId, task.getWorkflowInstanceId());
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, false);
        assertNotNull(workflow);
        assertNotNull(workflow.getOutput());

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull("Found=" + task.getInputData(), task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());

        assertTrue("Found " + workflow.getOutput().toString(), workflow.getOutput().containsKey("o3"));
        assertEquals("task1.Done", workflow.getOutput().get("o3"));
    }

    @Test
    public void testSimpleWorkflowNullInputOutputs() throws Exception {
        clearWorkflows();

        WorkflowDef workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        // Assert null keys are preserved in task definition's input parameters.
        assertTrue(workflowDefinition.getTasks().get(0).getInputParameters().containsKey("someNullKey"));

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", null);
        String workflowInstanceId = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        logger.info("testSimpleWorkflow.wfid= {}", workflowInstanceId);
        assertNotNull(workflowInstanceId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        // Assert null values are passed through workflow input.
        assertNull(workflow.getInput().get("param2"));
        // Assert null values are carried from task definition to task execution.
        assertNull(workflow.getTasks().get(0).getInputData().get("someNullKey"));

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowInstanceId, task.getWorkflowInstanceId());

        task.setStatus(COMPLETED);
        task.getOutputData().put("someKey", null);
        Map<String, Object> someOtherKey = new HashMap<>();
        someOtherKey.put("a", 1);
        someOtherKey.put("A", null);
        task.getOutputData().put("someOtherKey", someOtherKey);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);

        task = workflow.getTasks().get(0);
        // Assert null keys are preserved in task outputs.
        assertTrue(task.getOutputData().containsKey("someKey"));
        assertNull(task.getOutputData().get("someKey"));
        someOtherKey = (Map<String, Object>) task.getOutputData().get("someOtherKey");
        assertTrue(someOtherKey.containsKey("A"));
        assertNull(someOtherKey.get("A"));
    }

    @Test
    public void testTerminateMultiLevelWorkflow() {
        createWorkflowWthMultiLevelSubWorkflows();

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "p1 value");
        workflowInput.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(WORKFLOW_MULTI_LEVEL_SW, 1, "", workflowInput, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskIdLevel1 = workflow.getTaskByRefName("junit_sw_level_1_task").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskIdLevel1, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        String level1SubWorkflowId = workflow.getTasks().get(0).getSubWorkflowId();
        Workflow level1SubWorkflow = workflowExecutionService.getExecutionStatus(level1SubWorkflowId, true);
        assertNotNull(level1SubWorkflow);
        assertEquals(RUNNING, level1SubWorkflow.getStatus());
        assertEquals(1, level1SubWorkflow.getTasks().size());

        String subWorkflowTaskIdLevel2 = level1SubWorkflow.getTaskByRefName("junit_sw_level_2_task").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskIdLevel2, 1);

        level1SubWorkflow = workflowExecutionService.getExecutionStatus(level1SubWorkflowId, true);

        String level2SubWorkflowId = level1SubWorkflow.getTasks().get(0).getSubWorkflowId();
        Workflow level2SubWorkflow = workflowExecutionService.getExecutionStatus(level2SubWorkflowId, true);
        assertNotNull(level2SubWorkflow);
        assertEquals(RUNNING, level2SubWorkflow.getStatus());
        assertEquals(1, level2SubWorkflow.getTasks().size());

        String subWorkflowTaskIdLevel3 = level2SubWorkflow.getTaskByRefName("junit_sw_level_3_task").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskIdLevel3, 1);

        level2SubWorkflow = workflowExecutionService.getExecutionStatus(level2SubWorkflowId, true);

        String level3SubWorkflowId = level2SubWorkflow.getTasks().get(0).getSubWorkflowId();
        Workflow level3SubWorkflow = workflowExecutionService.getExecutionStatus(level3SubWorkflowId, true);
        assertNotNull(level3SubWorkflow);
        assertEquals(RUNNING, level3SubWorkflow.getStatus());
        assertEquals(1, level3SubWorkflow.getTasks().size());
        assertEquals("junit_task_3", level3SubWorkflow.getTasks().get(0).getTaskType());

        // terminate the top-level parent workflow
        workflowExecutor.terminateWorkflow(workflow.getWorkflowId(), "terminate_test");

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(TERMINATED, workflow.getStatus());

        level1SubWorkflow = workflowExecutionService.getExecutionStatus(level1SubWorkflowId, true);
        assertEquals(TERMINATED, level1SubWorkflow.getStatus());

        level2SubWorkflow = workflowExecutionService.getExecutionStatus(level2SubWorkflowId, true);
        assertEquals(TERMINATED, level2SubWorkflow.getStatus());

        level3SubWorkflow = workflowExecutionService.getExecutionStatus(level3SubWorkflowId, true);
        assertEquals(TERMINATED, level3SubWorkflow.getStatus());
    }

    @Test
    public void testSimpleWorkflowWithResponseTimeout() throws Exception {

        createWFWithResponseTimeout();

        String correlationId = "unit_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        String inputParam1 = "p1 value";
        workflowInput.put("param1", inputParam1);
        workflowInput.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution("RTOWF", 1, correlationId, workflowInput, null, null);
        logger.debug("testSimpleWorkflowWithResponseTimeout.wfid=" + workflowId);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.
        assertEquals(1, queueDAO.getSize("task_rt"));

        // Polling for the first task should return the first task
        Task task = workflowExecutionService.poll("task_rt", "task1.junit.worker.testTimeout");
        assertNotNull(task);
        assertEquals("task_rt", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // As the task_rt is out of the queue, the next poll should not get it
        Task nullTask = workflowExecutionService.poll("task_rt", "task1.junit.worker.testTimeout");
        assertNull(nullTask);

        Thread.sleep(10000);
        workflowExecutor.decide(workflowId);
        assertEquals(1, queueDAO.getSize("task_rt"));

        // The first task would be timed_out and a new task will be scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue(workflow.getTasks().stream().allMatch(t -> t.getReferenceTaskName().equals("task_rt_t1")));
        assertEquals(TIMED_OUT, workflow.getTasks().get(0).getStatus());
        assertEquals(SCHEDULED, workflow.getTasks().get(1).getStatus());

        // Polling now should get the same task back because it should have been put back in the queue
        Task taskAgain = workflowExecutionService.poll("task_rt", "task1.junit.worker");
        assertNotNull(taskAgain);

        // update task with callback after seconds greater than the response timeout
        taskAgain.setStatus(IN_PROGRESS);
        taskAgain.setCallbackAfterSeconds(2);
        workflowExecutionService.updateTask(taskAgain);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(SCHEDULED, workflow.getTasks().get(1).getStatus());

        // wait for callback after seconds which is longer than response timeout seconds and then call decide
        Thread.sleep(2010);
        // Ensure unacks are processed.
        queueDAO.processUnacks(taskAgain.getTaskDefName());
        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        // Poll for task again
        taskAgain = workflowExecutionService.poll("task_rt", "task1.junit.worker");
        assertNotNull(taskAgain);

        taskAgain.getOutputData().put("op", "task1.Done");
        taskAgain.setStatus(COMPLETED);
        workflowExecutionService.updateTask(taskAgain);

        // poll for next task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker.testTimeout");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testWorkflowRerunWithSubWorkflows() throws Exception {
        // Execute a workflow with sub-workflow
        String workflowId = this.runWorkflowWithSubworkflow();
        // Check it completed
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        // Now lets pickup the first task in the sub workflow and rerun it from there
        String subWorkflowId = null;
        for (Task task : workflow.getTasks()) {
            if (task.getTaskType().equalsIgnoreCase(SubWorkflow.NAME)) {
                subWorkflowId = task.getSubWorkflowId();
            }
        }
        assertNotNull(subWorkflowId);
        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        Task subWorkflowTask1 = null;
        for (Task task : subWorkflow.getTasks()) {
            if (task.getTaskDefName().equalsIgnoreCase("junit_task_1")) {
                subWorkflowTask1 = task;
            }
        }
        assertNotNull(subWorkflowTask1);

        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();

        Map<String, Object> newInput = new HashMap<>();
        newInput.put("p1", "1");
        newInput.put("p2", "2");
        rerunWorkflowRequest.setTaskInput(newInput);

        String correlationId = "unit_test_sw_new";
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "New p1 value");
        input.put("param2", "New p2 value");
        rerunWorkflowRequest.setCorrelationId(correlationId);
        rerunWorkflowRequest.setWorkflowInput(input);

        rerunWorkflowRequest.setReRunFromWorkflowId(workflowId);
        rerunWorkflowRequest.setReRunFromTaskId(subWorkflowTask1.getTaskId());
        // Rerun
        workflowExecutor.rerun(rerunWorkflowRequest);

        // The main WF and the sub WF should be in RUNNING state
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(correlationId, workflow.getCorrelationId());
        assertEquals("New p1 value", workflow.getInput().get("param1"));
        assertEquals("New p2 value", workflow.getInput().get("param2"));

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(RUNNING, subWorkflow.getStatus());
        // Since we are re running from the sub workflow task, there
        // should be only 1 task that is SCHEDULED
        assertEquals(1, subWorkflow.getTasks().size());
        assertEquals(SCHEDULED, subWorkflow.getTasks().get(0).getStatus());

        // Now execute the task
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(task.getInputData().get("p1").toString(), "1");
        assertEquals(task.getInputData().get("p2").toString(), "2");
        task.getOutputData().put("op", "junit_task_1.done");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(RUNNING, subWorkflow.getStatus());
        assertEquals(2, subWorkflow.getTasks().size());

        // Poll for second task of the sub workflow and execute it
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        task.getOutputData().put("op", "junit_task_2.done");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // Now the sub workflow and the main workflow must have finished
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertEquals(2, subWorkflow.getTasks().size());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflow.getParentWorkflowTaskId(), 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
    }

    @Test
    public void testSimpleWorkflowWithTaskSpecificDomain() throws Exception {

        long startTimeTimestamp = System.currentTimeMillis();

        clearWorkflows();
        createWorkflowWithSubWorkflow();

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2_SW, 1);

        String correlationId = "unit_test_sw";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("junit_task_3", "domain1");
        taskToDomain.put("junit_task_2", "domain1");

        // Poll before so that a polling for this task is "active"
        Task task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker", "domain1");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_2", "task1.junit.worker", "domain1");
        assertNull(task);

        String workflowId = startOrLoadWorkflowExecution("simpleWorkflowWithTaskSpecificDomain", LINEAR_WORKFLOW_T1_T2_SW, 1, correlationId, input, null, taskToDomain);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutor.getWorkflow(workflowId, false);
        assertNotNull(workflow);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        // Check Size
        Map<String, Integer> sizes = workflowExecutionService.getTaskQueueSizes(Arrays.asList("domain1:junit_task_3", "junit_task_3"));
        assertEquals(sizes.get("domain1:junit_task_3").intValue(), 1);
        assertEquals(sizes.get("junit_task_3").intValue(), 0);

        // Polling for the first task
        task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker", "domain1");
        assertNotNull(task);
        assertEquals("junit_task_3", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 10);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        task = tasks.get(0);
        assertEquals(workflowId, task.getWorkflowInstanceId());

        String task1Op = "task1.Done";
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, false);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertNotNull(workflow.getTaskToDomain());
        assertEquals(workflow.getTaskToDomain().size(), 2);

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker", "domain1");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        assertTrue("Found " + workflow.getOutput().toString(), workflow.getOutput().containsKey("o3"));
        assertEquals("task1.Done", workflow.getOutput().get("o3"));

        Predicate<PollData> pollDataWithinTestTimes = pollData -> pollData.getLastPollTime() != 0 && pollData.getLastPollTime() > startTimeTimestamp;

        List<PollData> pollData = workflowExecutionService.getPollData("junit_task_3").stream()
                .filter(pollDataWithinTestTimes)
                .collect(Collectors.toList());
        assertEquals(2, pollData.size());
        for (PollData pd : pollData) {
            assertEquals(pd.getQueueName(), "junit_task_3");
            assertEquals(pd.getWorkerId(), "task1.junit.worker");
            assertTrue(pd.getLastPollTime() != 0);
            if (pd.getDomain() != null) {
                assertEquals(pd.getDomain(), "domain1");
            }
        }

        List<PollData> pdList = workflowExecutionService.getAllPollData().stream()
                .filter(pollDataWithinTestTimes)
                .collect(Collectors.toList());
        int count = 0;
        for (PollData pd : pdList) {
            if (pd.getQueueName().equals("junit_task_3")) {
                count++;
            }
        }
        assertEquals(2, count);
    }

    @Test
    public void testSimpleWorkflowWithAllTaskInOneDomain() throws Exception {

        clearWorkflows();
        createWorkflowWithSubWorkflow();

        WorkflowDef def = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2_SW, 1);

        String correlationId = "unit_test_sw";
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        Map<String, String> taskToDomain = new HashMap<String, String>();
        taskToDomain.put("*", "domain11,, domain12");

        // Poll before so that a polling for this task is "active"
        Task task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker", "domain11");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_2", "task1.junit.worker", "domain12");
        assertNull(task);

        String workflowId = startOrLoadWorkflowExecution("simpleWorkflowWithTasksInOneDomain", LINEAR_WORKFLOW_T1_T2_SW, 1, correlationId, input, null, taskToDomain);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutor.getWorkflow(workflowId, false);
        assertNotNull(workflow);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        // Check Size
        Map<String, Integer> sizes = workflowExecutionService.getTaskQueueSizes(Arrays.asList("domain11:junit_task_3", "junit_task_3"));
        assertEquals(sizes.get("domain11:junit_task_3").intValue(), 1);
        assertEquals(sizes.get("junit_task_3").intValue(), 0);

        // Polling for the first task
        task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_3", "task1.junit.worker", "domain11");
        assertNotNull(task);
        assertEquals("junit_task_3", task.getTaskType());
        assertEquals("domain11", task.getDomain());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 1);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        task = tasks.get(0);

        String task1Op = "task1.Done";
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker", "domain12");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, false);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertNotNull(workflow.getTaskToDomain());
        assertEquals(workflow.getTaskToDomain().size(), 1);

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);


        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker", "domain11");
        assertNull(task);
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker", "domain12");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertEquals("domain12", task.getDomain());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        assertTrue("Found " + workflow.getOutput().toString(), workflow.getOutput().containsKey("o3"));
        assertEquals("task1.Done", workflow.getOutput().get("o3"));
    }

    @After
    public void clearWorkflows() throws Exception {
        List<String> workflowsWithVersion = metadataService.getWorkflowDefs().stream()
                .map(def -> def.getName() + ":" + def.getVersion())
                .collect(Collectors.toList());
        for (String workflowWithVersion : workflowsWithVersion) {
            String workflowName = StringUtils.substringBefore(workflowWithVersion, ":");
            int version = Integer.parseInt(StringUtils.substringAfter(workflowWithVersion, ":"));
            List<String> running = workflowExecutionService.getRunningWorkflows(workflowName, version);
            for (String wfid : running) {
                Workflow workflow = workflowExecutor.getWorkflow(wfid, false);
                if (!workflow.getStatus().isTerminal()) {
                    workflowExecutor.terminateWorkflow(wfid, "cleanup");
                }
            }
        }
        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);

        new FileOutputStream(this.getClass().getResource(TEMP_FILE_PATH).getPath()).close();
    }

    @Test
    public void testLongRunning() throws Exception {

        clearWorkflows();

        metadataService.getWorkflowDef(LONG_RUNNING, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        String workflowId = startOrLoadWorkflowExecution(LONG_RUNNING, 1, correlationId, input, null, null);
        System.out.println("testLongRunning.wfid=" + workflowId);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Collections.singletonList("junit_task_1")).get("junit_task_1"));

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Output = "task1.In.Progress";
        task.getOutputData().put("op", task1Output);
        task.setStatus(IN_PROGRESS);
        task.setCallbackAfterSeconds(5);
        workflowExecutionService.updateTask(task);
        String taskId = task.getTaskId();

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Collections.singletonList("junit_task_1")).get("junit_task_1"));

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        // Polling for next task should not return anything
        Task task2 = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNull(task2);

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNull(task);

        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        // Polling for the first task should return the same task as before
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(task.getTaskId(), taskId);

        task1Output = "task1.Done";
        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 1);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task = tasks.get(0);
        task.getOutputData().put("op", task1Output);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Output, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
    }

    @Test
    public void testResetWorkflowInProgressTasks() {
        WorkflowDef workflowDef = metadataService.getWorkflowDef(LONG_RUNNING, 1);
        assertNotNull(workflowDef);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(LONG_RUNNING, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Collections.singletonList("junit_task_1")).get("junit_task_1"));

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // Verify the task
        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");
        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        // Update the task with callbackAfterSeconds
        String task1Output = "task1.In.Progress";
        task.getOutputData().put("op", task1Output);
        task.setStatus(IN_PROGRESS);
        task.setCallbackAfterSeconds(3600);
        workflowExecutionService.updateTask(task);
        String taskId = task.getTaskId();

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Collections.singletonList("junit_task_1")).get("junit_task_1"));

        // Check the workflow
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(SCHEDULED, workflow.getTasks().get(0).getStatus());

        // Polling for next task should not return anything
        Task task2 = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNull(task2);

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNull(task);

        // Reset the callbackAfterSeconds
        workflowExecutor.resetCallbacksForWorkflow(workflowId);

        // Now Polling for the first task should return the same task as before
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(task.getTaskId(), taskId);
        assertEquals(task.getCallbackAfterSeconds(), 0);

        // update task with COMPLETED status
        task1Output = "task1.Done";
        task.getOutputData().put("op", task1Output);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // poll for next task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Output, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

         // update task with COMPLETED status
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
    }

    @Test
    public void testConcurrentWorkflowExecutions() {

        int count = 3;

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_concurrrent";
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String[] wfids = new String[count];

        for (int i = 0; i < count; i++) {
            String wfid = startOrLoadWorkflowExecution("concurrentWorkflowExecutions", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
            System.out.println("testConcurrentWorkflowExecutions.wfid=" + wfid);
            assertNotNull(wfid);

            List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2, 1);
            assertNotNull(ids);
            assertTrue("found no ids: " + ids, ids.size() > 0);        //if there are concurrent tests running, this would be more than 1
            boolean foundId = false;
            for (String id : ids) {
                if (id.equals(wfid)) {
                    foundId = true;
                }
            }
            assertTrue(foundId);
            wfids[i] = wfid;
        }


        String task1Op = "";
        for (int i = 0; i < count; i++) {

            Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
            assertNotNull(task);
            assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
            String param1 = (String) task.getInputData().get("p1");
            String param2 = (String) task.getInputData().get("p2");

            assertNotNull(param1);
            assertNotNull(param2);
            assertEquals("p1 value", param1);
            assertEquals("p2 value", param2);

            task1Op = "task1.output->" + param1 + "." + param2;
            task.getOutputData().put("op", task1Op);
            task.setStatus(COMPLETED);
            workflowExecutionService.updateTask(task);
        }

        for (int i = 0; i < count; i++) {
            Task task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
            assertNotNull(task);
            assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
            String task2Input = (String) task.getInputData().get("tp2");
            assertNotNull(task2Input);
            assertEquals(task1Op, task2Input);

            task2Input = (String) task.getInputData().get("tp1");
            assertNotNull(task2Input);
            assertEquals(inputParam1, task2Input);

            task.setStatus(COMPLETED);
            workflowExecutionService.updateTask(task);
        }

        List<Workflow> wfs = workflowExecutionService.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
        wfs.forEach(wf -> {
            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
        });
    }

    @Test
    public void testCaseStatementsSchemaVersion1() {
        createConditionalWF(1);
        runConditionalWorkflowTest();
    }


    @Test
    public void testCaseStatementsSchemaVersion2() {
        createConditionalWF(2);
        runConditionalWorkflowTest();
    }

    private void runConditionalWorkflowTest() {
        String correlationId = "testCaseStatements: " + System.currentTimeMillis();
        Map<String, Object> input = new HashMap<>();

        //default case
        input.put("param1", "xxx");
        input.put("param2", "two");
        String workflowId = startOrLoadWorkflowExecution(COND_TASK_WF, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        Task task = workflowExecutionService.poll("junit_task_10", "junit");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());

        //nested - one
        input.put("param1", "nested");
        input.put("param2", "one");
        workflowId = startOrLoadWorkflowExecution(COND_TASK_WF + 2, COND_TASK_WF, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("junit_task_1", workflow.getTasks().get(2).getTaskDefName());

        //nested - two
        input.put("param1", "nested");
        input.put("param2", "two");
        workflowId = startOrLoadWorkflowExecution(COND_TASK_WF + 3, COND_TASK_WF, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals("junit_task_2", workflow.getTasks().get(2).getTaskDefName());

        //three
        input.put("param1", "three");
        input.put("param2", "two");
        input.put("finalCase", "notify");
        workflowId = startOrLoadWorkflowExecution(COND_TASK_WF + 4, COND_TASK_WF, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("junit_task_3", workflow.getTasks().get(1).getTaskDefName());

        task = workflowExecutionService.poll("junit_task_3", "junit");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals("junit_task_4", workflow.getTasks().get(3).getTaskDefName());

        task = workflowExecutionService.poll("junit_task_4", "junit");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    private Task getTask(String taskType) {
        Task task;
        int count = 2;
        do {
            task = workflowExecutionService.poll(taskType, "junit");
            if (task == null) {
                count--;
            }
            if (count < 0) {
                break;
            }

        } while (task == null);
        if (task != null) {
            workflowExecutionService.ackTaskReceived(task.getTaskId());
        }
        return task;
    }

    @Test
    public void testRetries() {

        String taskName = "junit_task_2";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(2);
        taskDef.setRetryDelaySeconds(1);
        metadataService.updateTaskDef(taskDef);

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        System.out.println("testRetries.wfid=" + wfid);
        assertNotNull(wfid);

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(ids);
        assertTrue("found no ids: " + ids, ids.size() > 0);        //if there are concurrent tests running, this would be more than 1
        boolean foundId = false;
        for (String id : ids) {
            if (id.equals(wfid)) {
                foundId = true;
            }
        }
        assertTrue(foundId);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        //fail the task twice and then succeed
        verify(inputParam1, wfid, task1Op, true);
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        verify(inputParam1, wfid, task1Op, false);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        assertEquals(3, es.getTasks().size());        //task 1, and 2 of the task 2

        assertEquals("junit_task_1", es.getTasks().get(0).getTaskType());
        assertEquals("junit_task_2", es.getTasks().get(1).getTaskType());
        assertEquals("junit_task_2", es.getTasks().get(2).getTaskType());
        assertEquals(COMPLETED, es.getTasks().get(0).getStatus());
        assertEquals(FAILED, es.getTasks().get(1).getStatus());
        assertEquals(COMPLETED, es.getTasks().get(2).getStatus());
        assertEquals(es.getTasks().get(1).getTaskId(), es.getTasks().get(2).getRetriedTaskId());


    }

    @Test
    public void testSuccess() {

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(ids);
        assertTrue("found no ids: " + ids, ids.size() > 0);        //if there are concurrent tests running, this would be more than 1
        boolean foundId = false;
        for (String id : ids) {
            if (id.equals(wfid)) {
                foundId = true;
            }
        }
        assertTrue(foundId);

		/*
		 * @correlationId
		List<Workflow> byCorrelationId = ess.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
		assertNotNull(byCorrelationId);
		assertTrue(!byCorrelationId.isEmpty());
		assertEquals(1, byCorrelationId.size());
		*/

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // The first task would be marked as scheduled
        assertEquals(1, es.getTasks().size());
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());

        // decideNow should be idempotent if re-run on the same state!
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        assertEquals(1, es.getTasks().size());
        Task t = es.getTasks().get(0);
        assertEquals(SCHEDULED, t.getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        assertNotNull(task);
        assertEquals(t.getTaskId(), task.getTaskId());
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        t = es.getTasks().get(0);
        assertEquals(IN_PROGRESS, t.getStatus());
        String taskId = t.getTaskId();

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getTaskId().equals(taskId)) {
                assertEquals(COMPLETED, wfTask.getStatus());
            } else {
                assertEquals(SCHEDULED, wfTask.getStatus());
            }
        });

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertNotNull(task);
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        // Check the tasks, at this time there should be 2 task
        assertEquals(es.getTasks().size(), 2);
        es.getTasks().forEach(wfTask -> {
            assertEquals(wfTask.getStatus(), COMPLETED);
        });

        System.out.println("Total tasks=" + es.getTasks().size());
        assertTrue(es.getTasks().size() < 10);


    }

    @Test
    public void testDeciderUpdate() {

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        Workflow workflow = workflowExecutor.getWorkflow(wfid, false);
        long updated1 = workflow.getUpdateTime();
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        workflowExecutor.decide(wfid);
        workflow = workflowExecutor.getWorkflow(wfid, false);
        long updated2 = workflow.getUpdateTime();
        assertEquals(updated1, updated2);

        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        workflowExecutor.terminateWorkflow(wfid, "done");
        workflow = workflowExecutor.getWorkflow(wfid, false);
        updated2 = workflow.getUpdateTime();
        assertTrue("updated1[" + updated1 + "] >? updated2[" + updated2 + "]", updated2 > updated1);

    }

    @Test
    @Ignore
    //Ignore for now, will improve this in the future
    public void testFailurePoints() {

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // The first task would be marked as scheduled
        assertEquals(1, es.getTasks().size());
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        String taskId = task.getTaskId();

        String task1Op = "task1.output";
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        try {
            workflowExecutionService.updateTask(task);
        } catch (Exception e) {
            workflowExecutionService.updateTask(task);
        }

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getTaskId().equals(taskId)) {
                assertEquals(COMPLETED, wfTask.getStatus());
            } else {
                assertEquals(SCHEDULED, wfTask.getStatus());
            }
        });

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertNotNull(task);
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        // Check the tasks, at this time there should be 2 task
        assertEquals(es.getTasks().size(), 2);
        es.getTasks().forEach(wfTask -> {
            assertEquals(wfTask.getStatus(), COMPLETED);
        });

        System.out.println("Total tasks=" + es.getTasks().size());
        assertTrue(es.getTasks().size() < 10);


    }

    @Test
    public void testDeciderMix() throws Exception {

        ExecutorService executors = Executors.newFixedThreadPool(3);

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(ids);
        assertTrue("found no ids: " + ids, ids.size() > 0);        //if there are concurrent tests running, this would be more than 1
        boolean foundId = false;
        for (String id : ids) {
            if (id.equals(wfid)) {
                foundId = true;
            }
        }
        assertTrue(foundId);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // The first task would be marked as scheduled
        assertEquals(1, es.getTasks().size());
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());

        List<Future<Void>> futures = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(executors.submit(() -> {
                workflowExecutor.decide(wfid);
                return null;
            }));
        }
        for (Future<Void> future : futures) {
            future.get();
        }
        futures.clear();

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // The first task would be marked as scheduled
        assertEquals(1, es.getTasks().size());
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());


        // decideNow should be idempotent if re-run on the same state!
        workflowExecutor.decide(wfid);
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        assertEquals(1, es.getTasks().size());
        Task t = es.getTasks().get(0);
        assertEquals(SCHEDULED, t.getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        assertNotNull(task);
        assertEquals(t.getTaskId(), task.getTaskId());
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        t = es.getTasks().get(0);
        assertEquals(IN_PROGRESS, t.getStatus());
        String taskId = t.getTaskId();

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getTaskId().equals(taskId)) {
                assertEquals(COMPLETED, wfTask.getStatus());
            } else {
                assertEquals(SCHEDULED, wfTask.getStatus());
            }
        });

        //Run sweep 10 times!
        for (int i = 0; i < 10; i++) {
            futures.add(executors.submit(() -> {
                long s = System.currentTimeMillis();
                workflowExecutor.decide(wfid);
                System.out.println("Took " + (System.currentTimeMillis() - s) + " ms to run decider");
                return null;
            }));
        }
        for (Future<Void> future : futures) {
            future.get();
        }
        futures.clear();

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertEquals(RUNNING, es.getStatus());
        assertEquals(2, es.getTasks().size());

        System.out.println("Workflow tasks=" + es.getTasks());

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertNotNull(task);
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        // Check the tasks, at this time there should be 2 task
        assertEquals(es.getTasks().size(), 2);
        es.getTasks().forEach(wfTask -> {
            assertEquals(wfTask.getStatus(), COMPLETED);
        });

        System.out.println("Total tasks=" + es.getTasks().size());
        assertTrue(es.getTasks().size() < 10);
    }

    @Test
    public void testFailures() {
        metadataService.getWorkflowDef(FORK_JOIN_WF, 1);

        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(found.getFailureWorkflow());
        assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        input.put("failureWfName", "FanInOutTest");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        Task task = getTask("junit_task_1");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.FAILED, es.getStatus());

        taskDef.setRetryCount(RETRY_COUNT);
        metadataService.updateTaskDef(taskDef);

    }

    @Test
    public void testRetryWithForkJoin() throws Exception {
        String workflowId = this.runAFailedForkJoinWF();
        workflowExecutor.retry(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getStatus(), RUNNING);

        printTaskStatuses(workflow, "After retry called");

        Task t2 = workflowExecutionService.poll("junit_task_0_RT_2", "test");
        assertNotNull(t2);
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        Task t3 = workflowExecutionService.poll("junit_task_0_RT_3", "test");
        assertNotNull(t3);
        assertTrue(workflowExecutionService.ackTaskReceived(t3.getTaskId()));

        t2.setStatus(COMPLETED);
        t3.setStatus(COMPLETED);

        ExecutorService es = Executors.newFixedThreadPool(2);
        Future<?> future1 = es.submit(() -> {
            try {
                workflowExecutionService.updateTask(t2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        final Task _t3 = t3;
        Future<?> future2 = es.submit(() -> {
            try {
                workflowExecutionService.updateTask(_t3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        future1.get();
        future2.get();

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        printTaskStatuses(workflow, "T2, T3 complete");
        workflowExecutor.decide(workflowId);

        Task t4 = workflowExecutionService.poll("junit_task_0_RT_4", "test");
        assertNotNull(t4);
        t4.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t4);

        printTaskStatuses(workflowId, "After complete");
    }

    @Test
    public void testRetryWithDoWhile() throws Exception {
        String workflowId = this.runAFailedDoWhileWF();
        workflowExecutor.retry(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getStatus(), RUNNING);

        printTaskStatuses(workflow, "After retry called");

        Task t2 = workflowExecutionService.poll("junit_task_0_RT_2", "test");
        assertNotNull(t2);
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));

        t2.setStatus(COMPLETED);

        ExecutorService es = Executors.newFixedThreadPool(2);
        Future<?> future1 = es.submit(() -> {
            try {
                workflowExecutionService.updateTask(t2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        future1.get();

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        printTaskStatuses(workflow, "T2, T3 complete");
        workflowExecutor.decide(workflowId);

        printTaskStatuses(workflowId, "After complete");
    }

    @Test
    public void testRetry() {
        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        int retryCount = taskDef.getRetryCount();
        taskDef.setRetryCount(1);
        int retryDelay = taskDef.getRetryDelaySeconds();
        taskDef.setRetryDelaySeconds(0);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(workflowDef.getFailureWorkflow());
        assertFalse(StringUtils.isBlank(workflowDef.getFailureWorkflow()));

        String correlationId = "retry_test_" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution("retry", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(workflowId);
        printTaskStatuses(workflowId, "initial");

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        Task task = getTask("junit_task_1");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        task = getTask("junit_task_1");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        printTaskStatuses(workflowId, "before retry");

        workflowExecutor.retry(workflowId);

        printTaskStatuses(workflowId, "after retry");
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());

        task = getTask("junit_task_1");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        task = getTask("junit_task_2");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertEquals(3, workflow.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_1")).count());

        taskDef.setRetryCount(retryCount);
        taskDef.setRetryDelaySeconds(retryDelay);
        metadataService.updateTaskDef(taskDef);

        printTaskStatuses(workflowId, "final");
    }

    @Test
    public void testNonRestartartableWorkflows() {
        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        found.setName(JUNIT_TEST_WF_NON_RESTARTABLE);
        found.setRestartable(false);
        metadataService.updateWorkflowDef(found);

        assertNotNull(found);
        assertNotNull(found.getFailureWorkflow());
        assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(JUNIT_TEST_WF_NON_RESTARTABLE, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        Task task = getTask("junit_task_1");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        workflowExecutor.rewind(workflow.getWorkflowId(), false);

        // Polling for the first task should return the same task as before
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 1);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        task = tasks.get(0);
        assertEquals(workflowId, task.getWorkflowInstanceId());

        String task1Op = "task1.Done";
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, false);
        assertNotNull(workflow);
        assertNotNull(workflow.getOutput());

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull("Found=" + task.getInputData(), task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        tasks = workflow.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        assertTrue("Found " + workflow.getOutput().toString(), workflow.getOutput().containsKey("o3"));
        assertEquals("task1.Done", workflow.getOutput().get("o3"));

        expectedException.expect(ApplicationException.class);
        expectedException.expectMessage(String.format("%s is non-restartable", workflow));
        workflowExecutor.rewind(workflow.getWorkflowId(), false);
    }


    @Test
    public void testRestart() {
        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(workflowDef.getFailureWorkflow());
        assertFalse(StringUtils.isBlank(workflowDef.getFailureWorkflow()));

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        Task task = getTask("junit_task_1");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        workflowExecutor.rewind(workflow.getWorkflowId(), false);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_1");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_2");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());

        // Add a new version of the definition with an additional task
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("junit_task_20");
        workflowTask.setTaskReferenceName("task_added");
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);

        workflowDef.getTasks().add(workflowTask);
        workflowDef.setVersion(2);
        metadataService.updateWorkflowDef(workflowDef);

        // restart with the latest definition
        workflowExecutor.rewind(workflow.getWorkflowId(), true);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_1");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_2");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_20");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        assertEquals("task_added", task.getReferenceTaskName());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());

        // cleanup
        metadataService.unregisterWorkflowDef(workflowDef.getName(), 2);
    }


    @Test
    public void testTaskTimeout() throws Exception {

        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1);
        taskDef.setRetryDelaySeconds(0);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(found.getFailureWorkflow());
        assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        input.put("failureWfName", "FanInOutTest");
        String workflowId = startOrLoadWorkflowExecution("timeout", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        //Ensure that we have a workflow queued up for evaluation here...
        long size = queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE);
        assertEquals(1, size);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals("found: " + workflow.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 1, workflow.getTasks().size());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        //Ensure that we have a workflow queued up for evaluation here...
        size = queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE);
        assertEquals(1, size);

        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        workflowSweeper.sweep(Collections.singletonList(workflowId), workflowExecutor, workflowRepairService);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("found: " + workflow.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 2, workflow.getTasks().size());

        Task task1 = workflow.getTasks().get(0);
        assertEquals(TIMED_OUT, task1.getStatus());
        Task task2 = workflow.getTasks().get(1);
        assertEquals(SCHEDULED, task2.getStatus());

        task = workflowExecutionService.poll(task2.getTaskDefName(), "task1.junit.worker");
        assertNotNull(task);
        assertEquals(workflowId, task.getWorkflowInstanceId());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        workflowExecutor.decide(workflowId);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());

        assertEquals(TIMED_OUT, workflow.getTasks().get(0).getStatus());
        assertEquals(TIMED_OUT, workflow.getTasks().get(1).getStatus());
        assertEquals(WorkflowStatus.TIMED_OUT, workflow.getStatus());

        assertEquals(1, queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE));

        taskDef.setTimeoutSeconds(0);
        taskDef.setRetryCount(RETRY_COUNT);
        metadataService.updateTaskDef(taskDef);
    }

    @Test
    public void testWorkflowTimeouts() throws Exception {
        WorkflowDef workflowDef = metadataService.getWorkflowDef(TEST_WORKFLOW, 1);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTimeoutSeconds(5);
        metadataService.updateWorkflowDef(workflowDef);

        String correlationId = "test_workflow_timeout" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(TEST_WORKFLOW, 1, correlationId, input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        Uninterruptibles.sleepUninterruptibly(6, TimeUnit.SECONDS);
        workflowSweeper.sweep(Collections.singletonList(workflowId), workflowExecutor, workflowRepairService);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.TIMED_OUT, workflow.getStatus());

        workflowDef.setTimeoutSeconds(0);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        metadataService.updateWorkflowDef(workflowDef);
    }

    @Test
    public void testReruns() {

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // Check the tasks, at this time there should be 1 task
        assertEquals(es.getTasks().size(), 1);
        Task t = es.getTasks().get(0);
        assertEquals(SCHEDULED, t.getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(t.getTaskId(), task.getTaskId());

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getTaskId().equals(t.getTaskId())) {
                assertEquals(wfTask.getStatus(), COMPLETED);
            } else {
                assertEquals(wfTask.getStatus(), SCHEDULED);
            }
        });

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());

        // Now rerun lets rerun the workflow from the second task
        RerunWorkflowRequest request = new RerunWorkflowRequest();
        request.setReRunFromWorkflowId(wfid);
        request.setReRunFromTaskId(es.getTasks().get(1).getTaskId());

        String reRunwfid = workflowExecutor.rerun(request);

        Workflow esRR = workflowExecutionService.getExecutionStatus(reRunwfid, true);
        assertNotNull(esRR);
        assertEquals(esRR.getReasonForIncompletion(), RUNNING, esRR.getStatus());
        // Check the tasks, at this time there should be 2 tasks
        // first one is skipped and the second one is scheduled
        assertEquals(esRR.getTasks().toString(), 2, esRR.getTasks().size());
        assertEquals(COMPLETED, esRR.getTasks().get(0).getStatus());
        Task tRR = esRR.getTasks().get(1);
        assertEquals(esRR.getTasks().toString(), SCHEDULED, tRR.getStatus());
        assertEquals(tRR.getTaskType(), "junit_task_2");

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(reRunwfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());

        //////////////////////
        // Now rerun the entire workflow
        RerunWorkflowRequest request1 = new RerunWorkflowRequest();
        request1.setReRunFromWorkflowId(wfid);

        String reRunwfid1 = workflowExecutor.rerun(request1);

        es = workflowExecutionService.getExecutionStatus(reRunwfid1, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // Check the tasks, at this time there should be 1 task
        assertEquals(es.getTasks().size(), 1);
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
    }

    @Test
    public void testTaskSkipping() {

        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(0);
        metadataService.updateTaskDef(taskDef);


        metadataService.getWorkflowDef(TEST_WORKFLOW, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(TEST_WORKFLOW, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        // Now Skip the second task
        workflowExecutor.skipTaskFromWorkflow(wfid, "t2", null);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // Check the tasks, at this time there should be 3 task
        assertEquals(2, es.getTasks().size());

        assertEquals(SCHEDULED, es.getTasks().stream().filter(task -> "t1".equals(task.getReferenceTaskName())).findFirst().orElse(null).getStatus());
        assertEquals(Status.SKIPPED, es.getTasks().stream().filter(task -> "t2".equals(task.getReferenceTaskName())).findFirst().orElse(null).getStatus());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        assertEquals("t1", task.getReferenceTaskName());

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getReferenceTaskName().equals("t1")) {
                assertEquals(COMPLETED, wfTask.getStatus());
            } else if (wfTask.getReferenceTaskName().equals("t2")) {
                assertEquals(Status.SKIPPED, wfTask.getStatus());
            } else {
                assertEquals(SCHEDULED, wfTask.getStatus());
            }
        });

        task = workflowExecutionService.poll("junit_task_3", "task3.junit.worker");
        assertNotNull(task);
        assertEquals(IN_PROGRESS, task.getStatus());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
    }

    @Test
    public void testPauseResume() {

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);

        String correlationId = "unit_test_1" + System.nanoTime();
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);

        assertNotNull(wfid);

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(ids);
        assertTrue("found no ids: " + ids, ids.size() > 0);        //if there are concurrent tests running, this would be more than 1
        boolean foundId = false;
        for (String id : ids) {
            if (id.equals(wfid)) {
                foundId = true;
            }
        }
        assertTrue(foundId);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        Task t = es.getTasks().get(0);
        assertEquals(SCHEDULED, t.getStatus());

        // PAUSE
        workflowExecutor.pauseWorkflow(wfid);

        // The workflow is paused but the scheduled task should be pollable

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(t.getTaskId(), task.getTaskId());

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);

        String task1Op = "task1.output->" + param1 + "." + param2;
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // This decide should not schedule the next task
        //ds.decideNow(wfid, task);

        // If we get the full workflow here then, last task should be completed and the rest (including PAUSE task) should be scheduled
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        es.getTasks().forEach(wfTask -> {
            if (wfTask.getTaskId().equals(t.getTaskId())) {
                assertEquals(wfTask.getStatus(), COMPLETED);
            }
        });

        // This should return null as workflow is paused
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNull("Found: " + task, task);

        // Even if decide is run again the next task will not be scheduled as the workflow is still paused--
        workflowExecutor.decide(wfid);

        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertTrue(task == null);

        // RESUME
        workflowExecutor.resumeWorkflow(wfid);

        // Now polling should get the second task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));


        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);

        Task byRefName = workflowExecutionService.getPendingTaskForWorkflow("t2", wfid);
        assertNotNull(byRefName);
        assertEquals(task.getTaskId(), byRefName.getTaskId());

        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
    }

    @Test
    public void testSubWorkflow() {

        createSubWorkflow();
        metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 2 value");
        input.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String wfId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(wfId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);

        Task simpleTask = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(simpleTask);
        simpleTask.setStatus(COMPLETED);
        workflowExecutionService.updateTask(simpleTask);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        Task subWorkflowTask = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(subWorkflowTask);
        assertNotNull(subWorkflowTask.getOutputData());
        assertNotNull(subWorkflowTask.getInputData());
        assertNotNull("Output: " + subWorkflowTask.getSubWorkflowId() + ", status: " + subWorkflowTask.getStatus(), subWorkflowTask.getSubWorkflowId());
        assertTrue(subWorkflowTask.getInputData().containsKey("workflowInput"));
        assertEquals(42, ((Map<String, Object>) subWorkflowTask.getInputData().get("workflowInput")).get("param2"));
        String subWorkflowId = subWorkflowTask.getSubWorkflowId();

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(wfId, workflow.getParentWorkflowId());
        assertEquals(RUNNING, workflow.getStatus());

        simpleTask = workflowExecutionService.poll("junit_task_1", "test");
        simpleTask.setStatus(COMPLETED);
        workflowExecutionService.updateTask(simpleTask);

        simpleTask = workflowExecutionService.poll("junit_task_2", "test");
        assertEquals(subWorkflowId, simpleTask.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        simpleTask.getOutputData().put("uuid", uuid);
        simpleTask.setStatus(COMPLETED);
        workflowExecutionService.updateTask(simpleTask);

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(LINEAR_WORKFLOW_T1_T2, workflow.getWorkflowName());
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().containsKey("o1"));
        assertTrue(workflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", workflow.getOutput().get("o1"));
        assertEquals(uuid, workflow.getOutput().get("o2"));

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        subWorkflowTask = workflowExecutionService.poll("junit_task_6", "test");
        assertNotNull(subWorkflowTask);
        subWorkflowTask.setStatus(COMPLETED);
        workflowExecutionService.updateTask(subWorkflowTask);

        workflow = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testSubWorkflowFailure() {

        TaskDef taskDef = notFoundSafeGetTaskDef("junit_task_1");
        assertNotNull(taskDef);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(2);
        metadataService.updateTaskDef(taskDef);


        createSubWorkflow();
        metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 2 value");
        input.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String workflowId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull(task.getSubWorkflowId());
        String subWorkflowId = task.getSubWorkflowId();

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertNotNull(subWorkflow.getTasks());

        assertEquals(workflowId, subWorkflow.getParentWorkflowId());
        assertEquals(RUNNING, subWorkflow.getStatus());

        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.FAILED, subWorkflow.getStatus());
        workflowExecutor.executeSystemTask(subworkflow, subWorkflow.getParentWorkflowTaskId(), 1);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());
        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertEquals(FAILED, task.getStatus());
    }

    @Test
    public void testSubWorkflowFailureInverse() {

        TaskDef taskDef = notFoundSafeGetTaskDef("junit_task_1");
        assertNotNull(taskDef);
        taskDef.setRetryCount(0);
        taskDef.setTimeoutSeconds(2);
        metadataService.updateTaskDef(taskDef);


        createSubWorkflow();

        WorkflowDef found = metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);
        assertNotNull(found);
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 2 value");
        input.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String workflowId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull(task.getSubWorkflowId());
        String subWorkflowId = task.getSubWorkflowId();

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(workflowId, workflow.getParentWorkflowId());
        assertEquals(RUNNING, workflow.getStatus());

        workflowExecutor.terminateWorkflow(workflowId, "fail");
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(WorkflowStatus.TERMINATED, workflow.getStatus());

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(WorkflowStatus.TERMINATED, workflow.getStatus());

    }

    @Test
    public void testSubWorkflowRetry() {
        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        int retryCount = notFoundSafeGetTaskDef(taskName).getRetryCount();
        taskDef.setRetryCount(0);
        metadataService.updateTaskDef(taskDef);

        // create a workflow with sub-workflow
        createSubWorkflow();
        WorkflowDef found = metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);

        WorkflowTask workflowTask = found.getTasks().stream().filter(t -> t.getType().equals(SUB_WORKFLOW.name())).findAny().orElse(null);

        // Set subworkflow task retry count to 1.
        TaskDef subWorkflowTaskDef = new TaskDef();
        subWorkflowTaskDef.setRetryCount(1);
        subWorkflowTaskDef.setName("test_subworkflow_task");
        subWorkflowTaskDef.setOwnerEmail("test@qbc.com");
        workflowTask.setTaskDefinition(subWorkflowTaskDef);

        metadataService.updateWorkflowDef(found);

        // start the workflow
        Map<String, Object> workflowInputParams = new HashMap<>();
        workflowInputParams.put("param1", "param 1");
        workflowInputParams.put("param3", "param 2");
        workflowInputParams.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String workflowId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", workflowInputParams, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        // poll and complete first task
        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(2, workflow.getTasks().size());

        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().orElse(null);
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull("Output: " + task.getOutputData().toString() + ", status: " + task.getStatus(), task.getSubWorkflowId());
        String subWorkflowId = task.getSubWorkflowId();

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(workflowId, workflow.getParentWorkflowId());
        assertEquals(RUNNING, workflow.getStatus());

        // poll and fail the first task in sub-workflow
        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.FAILED, subWorkflow.getStatus());
        subWorkflowTaskId = subWorkflow.getParentWorkflowTaskId();

        workflowExecutor.executeSystemTask(subworkflow, subWorkflowTaskId, 1);

        // Ensure failed Subworkflow task is rescheduled.
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = workflow.getTasks().stream()
                .filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name()))
                .filter(t -> t.getStatus().equals(SCHEDULED))
                .findAny().orElse(null);
        assertNotNull(task);
        subWorkflowTaskId = task.getTaskId();

        workflowExecutor.executeSystemTask(subworkflow, task.getTaskId(), 1);

        // Get the latest workflow and task, and then acquire latest subWorkflowId
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        task = workflow.getTasks().stream()
                .filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name()))
                .filter(t -> t.getStatus().equals(IN_PROGRESS))
                .findAny().orElse(null);
        assertNotNull(task);
        assertNotNull("Retried task in scheduled state shouldn't have a SubworkflowId yet", task.getSubWorkflowId());
        subWorkflowId = task.getSubWorkflowId();

        // poll and fail the first task in sub-workflow
        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        // Retry the failed sub workflow
        workflowExecutor.retry(subWorkflowId);
        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(RUNNING, subWorkflow.getStatus());

        task = workflowExecutionService.poll("junit_task_2", "test");
        assertEquals(subWorkflowId, task.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        task.getOutputData().put("uuid", uuid);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertNotNull(subWorkflow.getOutput());
        assertTrue(subWorkflow.getOutput().containsKey("o1"));
        assertTrue(subWorkflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", subWorkflow.getOutput().get("o1"));
        assertEquals(uuid, subWorkflow.getOutput().get("o2"));

        // Simulating SystemTaskWorkerCoordinator
        workflowExecutor.executeSystemTask(subworkflow, subWorkflow.getParentWorkflowTaskId(), 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = workflowExecutionService.poll("junit_task_6", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());

        // reset retry count
        taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setRetryCount(retryCount);
        metadataService.updateTaskDef(taskDef);

        workflowTask = found.getTasks().stream().filter(t -> t.getType().equals(SUB_WORKFLOW.name())).findAny().orElse(null);
        workflowTask.setTaskDefinition(null);
        metadataService.updateWorkflowDef(found);
    }

    @Test
    public void testWait() {

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_wait");
        workflowDef.setSchemaVersion(2);

        WorkflowTask waitWorkflowTask = new WorkflowTask();
        waitWorkflowTask.setWorkflowTaskType(TaskType.WAIT);
        waitWorkflowTask.setName("wait");
        waitWorkflowTask.setTaskReferenceName("wait0");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("junit_task_1");
        workflowTask.setTaskReferenceName("t1");

        workflowDef.getTasks().add(waitWorkflowTask);
        workflowDef.getTasks().add(workflowTask);
        metadataService.registerWorkflowDef(workflowDef);

        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", new HashMap<>(), null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());
        assertEquals(RUNNING, workflow.getStatus());

        Task waitTask = workflow.getTasks().get(0);
        assertEquals(TaskType.WAIT.name(), waitTask.getTaskType());
        waitTask.setStatus(COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        Task task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testWaitTimeout() throws Exception {

        TaskDef taskDef = new TaskDef();
        taskDef.setName("waitTimeout");
        taskDef.setTimeoutSeconds(2);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Collections.singletonList(taskDef));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_wait_timeout");
        workflowDef.setSchemaVersion(2);

        WorkflowTask waitWorkflowTask = new WorkflowTask();
        waitWorkflowTask.setWorkflowTaskType(TaskType.WAIT);
        waitWorkflowTask.setName("waitTimeout");
        waitWorkflowTask.setTaskReferenceName("wait0");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("junit_task_1");
        workflowTask.setTaskReferenceName("t1");

        workflowDef.getTasks().add(waitWorkflowTask);
        workflowDef.getTasks().add(workflowTask);
        metadataService.registerWorkflowDef(workflowDef);

        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", new HashMap<>(), null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());
        assertEquals(RUNNING, workflow.getStatus());

        // timeout the wait task and ensure it is retried
        Thread.sleep(3000);
        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(TIMED_OUT, workflow.getTasks().get(0).getStatus());
        assertEquals(IN_PROGRESS, workflow.getTasks().get(1).getStatus());

        Task waitTask = workflow.getTasks().get(1);
        assertEquals(TaskType.WAIT.name(), waitTask.getTaskType());
        waitTask.setStatus(COMPLETED);
        workflowExecutor.updateTask(new TaskResult(waitTask));

        Task task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }


    @Test
    public void testLambda() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_lambda_wf");
        workflowDef.setSchemaVersion(2);

        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("input", "${workflow.input}");
        inputParams.put("scriptExpression", "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false} }");
        WorkflowTask lambdaWorkflowTask = new WorkflowTask();
        lambdaWorkflowTask.setWorkflowTaskType(TaskType.LAMBDA);
        lambdaWorkflowTask.setName("lambda");
        lambdaWorkflowTask.setInputParameters(inputParams);
        lambdaWorkflowTask.setTaskReferenceName("lambda0");

        workflowDef.getTasks().add(lambdaWorkflowTask);

        assertNotNull(workflowDef);
        metadataService.registerWorkflowDef(workflowDef);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a", 1);
        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", inputs, null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);

        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());

        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        Task lambda_task = workflow.getTasks().get(0);

        assertEquals(lambda_task.getOutputData().toString(), "{result={testvalue=true}}");
        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testTerminateTaskWithCompletedStatus() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_terminate_task_wf");
        workflowDef.setSchemaVersion(2);

        Map<String, Object> lambdaTaskInputParams = new HashMap<>();
        lambdaTaskInputParams.put("input", "${workflow.input}");
        lambdaTaskInputParams.put("scriptExpression", "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false}}");

        WorkflowTask lambdaWorkflowTask = new WorkflowTask();
        lambdaWorkflowTask.setWorkflowTaskType(TaskType.LAMBDA);
        lambdaWorkflowTask.setName("lambda");
        lambdaWorkflowTask.setInputParameters(lambdaTaskInputParams);
        lambdaWorkflowTask.setTaskReferenceName("lambda0");

        Map<String, Object> terminateTaskInputParams = new HashMap<>();
        terminateTaskInputParams.put(Terminate.getTerminationStatusParameter(), "COMPLETED");
        terminateTaskInputParams.put(Terminate.getTerminationWorkflowOutputParameter(), "${lambda0.output}");

        WorkflowTask terminateWorkflowTask = new WorkflowTask();
        terminateWorkflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        terminateWorkflowTask.setName("terminate");
        terminateWorkflowTask.setInputParameters(terminateTaskInputParams);
        terminateWorkflowTask.setTaskReferenceName("terminate0");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        workflowTask2.setTaskReferenceName("t2");

        workflowDef.getTasks().addAll(Arrays.asList(lambdaWorkflowTask, terminateWorkflowTask, workflowTask2));

        assertNotNull(workflowDef);
        metadataService.registerWorkflowDef(workflowDef);

        Map<String, Object> wfInput = new HashMap<>();
        wfInput.put("a", 1);
        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", wfInput, null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);

        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());

        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(TaskType.TASK_TYPE_LAMBDA, workflow.getTasks().get(0).getTaskType());
        assertEquals(TaskType.TASK_TYPE_TERMINATE, workflow.getTasks().get(1).getTaskType());
        assertEquals(workflow.getTasks().get(1).getOutputData(), workflow.getOutput());

        metadataService.unregisterWorkflowDef("test_terminate_task_wf", 1);
    }

    @Test
    public void testTerminateTaskWithFailedStatus() {
        String failureWorkflowName = "failure_workflow";
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_terminate_task_wf");
        workflowDef.setSchemaVersion(2);

        Map<String, Object> lambdaTaskInputParams = new HashMap<>();
        lambdaTaskInputParams.put("input", "${workflow.input}");
        lambdaTaskInputParams.put("scriptExpression", "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false}}");

        WorkflowTask lambdaWorkflowTask = new WorkflowTask();
        lambdaWorkflowTask.setWorkflowTaskType(TaskType.LAMBDA);
        lambdaWorkflowTask.setName("lambda");
        lambdaWorkflowTask.setInputParameters(lambdaTaskInputParams);
        lambdaWorkflowTask.setTaskReferenceName("lambda0");

        Map<String, Object> terminateTaskInputParams = new HashMap<>();
        terminateTaskInputParams.put(Terminate.getTerminationStatusParameter(), "FAILED");
        terminateTaskInputParams.put(Terminate.getTerminationWorkflowOutputParameter(), "${lambda0.output}");

        WorkflowTask terminateWorkflowTask = new WorkflowTask();
        terminateWorkflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        terminateWorkflowTask.setName("terminate");
        terminateWorkflowTask.setInputParameters(terminateTaskInputParams);
        terminateWorkflowTask.setTaskReferenceName("terminate0");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        workflowTask2.setTaskReferenceName("t2");

        workflowDef.getTasks().addAll(Arrays.asList(lambdaWorkflowTask, terminateWorkflowTask, workflowTask2));

        WorkflowDef failureWorkflowDef = new WorkflowDef();
        failureWorkflowDef.setName(failureWorkflowName);
        failureWorkflowDef.setTasks(Collections.singletonList(lambdaWorkflowTask));

        workflowDef.setFailureWorkflow(failureWorkflowName);

        metadataService.registerWorkflowDef(failureWorkflowDef);
        metadataService.registerWorkflowDef(workflowDef);

        Map<String, Object> wfInput = new HashMap<>();
        wfInput.put("a", 1);
        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", wfInput, null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);

        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());

        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.FAILED, workflow.getStatus());
        assertEquals(TaskType.TASK_TYPE_LAMBDA, workflow.getTasks().get(0).getTaskType());
        assertEquals(TaskType.TASK_TYPE_TERMINATE, workflow.getTasks().get(1).getTaskType());
        assertNotNull(workflow.getOutput());
        assertNotNull(workflow.getOutput().get("conductor.failure_workflow"));

        String failureWorkflowId = (String)workflow.getOutput().get("conductor.failure_workflow");
        Workflow failureWorkflow = workflowExecutionService.getExecutionStatus(failureWorkflowId, true);
        assertNotNull(failureWorkflow);
        assertEquals(failureWorkflowName, failureWorkflow.getWorkflowName());
        assertEquals(workflowId, failureWorkflow.getInput().get("workflowId"));
        assertEquals(WorkflowStatus.COMPLETED, failureWorkflow.getStatus());
        assertEquals(1, failureWorkflow.getTasks().size());
        assertEquals(TaskType.TASK_TYPE_LAMBDA, failureWorkflow.getTasks().get(0).getTaskType());

        metadataService.unregisterWorkflowDef("test_terminate_task_wf", 1);
        metadataService.unregisterWorkflowDef(failureWorkflowName, 1);
    }

    @Test
    public void testEventWorkflow() {

        TaskDef taskDef = new TaskDef();
        taskDef.setName("eventX");
        taskDef.setTimeoutSeconds(1);

        metadataService.registerTaskDef(Collections.singletonList(taskDef));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_event");
        workflowDef.setSchemaVersion(2);

        WorkflowTask eventWorkflowTask = new WorkflowTask();
        eventWorkflowTask.setWorkflowTaskType(TaskType.EVENT);
        eventWorkflowTask.setName("eventX");
        eventWorkflowTask.setTaskReferenceName("wait0");
        eventWorkflowTask.setSink("conductor");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("junit_task_1");
        workflowTask.setTaskReferenceName("t1");

        workflowDef.getTasks().add(eventWorkflowTask);
        workflowDef.getTasks().add(workflowTask);
        metadataService.registerWorkflowDef(workflowDef);

        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", new HashMap<>(), null, null);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);

        Task eventTask = workflow.getTasks().get(0);
        assertEquals(TaskType.EVENT.name(), eventTask.getTaskType());
        assertEquals(COMPLETED, eventTask.getStatus());
        assertFalse(eventTask.getOutputData().isEmpty());
        assertNotNull(eventTask.getOutputData().get("event_produced"));

        Task task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testTaskWithCallbackAfterSecondsInWorkflow() throws InterruptedException {
        WorkflowDef workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(workflowDef);

        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "", new HashMap<>(), null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);
        assertNotNull(workflow);

        Task task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String taskId = task.getTaskId();
        task.setStatus(IN_PROGRESS);
        task.setCallbackAfterSeconds(2L);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());

        // task should not be available
        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNull(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());

        Thread.sleep(2050);
        queueDAO.processUnacks("junit_task_1");
        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        assertEquals(taskId, task.getTaskId());

        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());

        task = workflowExecutionService.poll("junit_task_2", "test");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        taskId = task.getTaskId();
        task.setStatus(IN_PROGRESS);
        task.setCallbackAfterSeconds(5L);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());

        // task should not be available
        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNull(task);

        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

        task = workflowExecutionService.poll("junit_task_2", "test");
        assertNotNull(task);
        assertEquals(taskId, task.getTaskId());

        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(2, workflow.getTasks().size());
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testWorkflowUsingExternalPayloadStorage() {
        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(found);

        Map<String, Object> outputParameters = found.getOutputParameters();
        outputParameters.put("workflow_output", "${t1.output.op}");
        metadataService.updateWorkflowDef(found);

        String workflowInputPath = INITIAL_WORKFLOW_INPUT_PATH;
        String correlationId = "wf_external_storage";
        String workflowId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update first task with COMPLETED
        String taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue("The first task output should not be persisted", workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue("The second task input should not be persisted", workflow.getTasks().get(1).getInputData().isEmpty());
        assertEquals(taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertEquals(INPUT_PAYLOAD_PATH, workflow.getTasks().get(1).getExternalInputPayloadStoragePath());

        // Polling for the second task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(task.getInputData().isEmpty());
        assertNotNull(task.getExternalInputPayloadStoragePath());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update second task with COMPLETED
        task.getOutputData().put("op", "success_task2");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue("The first task output should not be persisted", workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue("The second task input should not be persisted", workflow.getTasks().get(1).getInputData().isEmpty());
        assertEquals(taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertEquals(INPUT_PAYLOAD_PATH, workflow.getTasks().get(1).getExternalInputPayloadStoragePath());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals(WORKFLOW_OUTPUT_PATH, workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testWorkflowWithConditionalSystemTaskUsingExternalPayloadStorage() {
        createConditionalWFWithSystemTask();
        WorkflowDef workflowDef = metadataService.getWorkflowDef(CONDITIONAL_SYSTEM_WORKFLOW, 1);
        assertNotNull(workflowDef);

        String workflowInputPath = INITIAL_WORKFLOW_INPUT_PATH;
        String correlationId = "conditional_http_external_storage";
        String workflowId = workflowExecutor.startWorkflow(CONDITIONAL_SYSTEM_WORKFLOW, 1, correlationId, null, workflowInputPath, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "junit.worker.task_1");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update first task with COMPLETED and using external payload storage for output data
        String taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertEquals(DECISION.name(), workflow.getTasks().get(1).getTaskType());
        assertEquals(UserTask.NAME, workflow.getTasks().get(2).getTaskType());
        assertEquals(0, workflow.getTasks().get(2).getPollCount());

        // simulate the SystemTaskWorkerCoordinator action
        String taskId = workflow.getTaskByRefName("user_task").getTaskId();
        workflowExecutor.executeSystemTask(userTask, taskId, 1);

        task = workflowExecutionService.getTask(taskId);
        assertEquals(COMPLETED, task.getStatus());
        assertEquals(0, workflow.getTasks().get(2).getPollCount());
        assertTrue("The task input should not be persisted", task.getInputData().isEmpty());
        assertEquals(INPUT_PAYLOAD_PATH, task.getExternalInputPayloadStoragePath());
        assertEquals(104, task.getOutputData().get("size"));

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        // Polling for the last task
        task = workflowExecutionService.poll("junit_task_3", "junit.worker.task_3");
        assertNotNull(task);
        assertEquals("junit_task_3", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update final task with COMPLETED
        task.getOutputData().put("op", "success_task3");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals(WORKFLOW_OUTPUT_PATH, workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testWorkflowWithForkJoinUsingExternalPayloadStorage() {
        createForkJoinWorkflow();

        WorkflowDef workflowDef = metadataService.getWorkflowDef(FORK_JOIN_WF, 1);
        assertNotNull(workflowDef);

        String workflowInputPath = "workflow/input";
        String correlationId = "fork_join_external_storage";
        String workflowId = workflowExecutor.startWorkflow(FORK_JOIN_WF, 1, correlationId, null, workflowInputPath, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(4, workflow.getTasks().size());

        // Polling for first task from left fork
        Task task1 = workflowExecutionService.poll("junit_task_1", "junit.worker.task_1");
        assertNotNull(task1);
        assertEquals("junit_task_1", task1.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task1.getTaskId()));
        assertEquals(workflowId, task1.getWorkflowInstanceId());

        // Polling for second task from left fork should not return a task
        Task task3 = workflowExecutionService.poll("junit_task_3", "junit.worker.task_3");
        assertNull(task3);

        // Polling for first task from right fork
        Task task2 = workflowExecutionService.poll("junit_task_2", "junit.worker.task_2");
        assertNotNull(task2);
        assertEquals("junit_task_2", task2.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task2.getTaskId()));
        assertEquals(workflowId, task2.getWorkflowInstanceId());

        // Update first task of left fork to COMPLETED
        task1.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(5, workflow.getTasks().size());

        // Polling for second task from left fork
        task3 = workflowExecutionService.poll("junit_task_3", "junit.worker.task_3");
        assertNotNull(task3);
        assertEquals("junit_task_3", task3.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task3.getTaskId()));
        assertEquals(workflowId, task3.getWorkflowInstanceId());

        // Update both tasks to COMPLETED with output in external storage
        task2.setOutputData(null);
        task2.setExternalOutputPayloadStoragePath(TASK_OUTPUT_PATH);
        task2.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task2);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        task3.setOutputData(null);
        task3.setExternalOutputPayloadStoragePath(TASK_OUTPUT_PATH);
        task3.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task3);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(6, workflow.getTasks().size());
        assertTrue("The JOIN task output should not be persisted", workflow.getTasks().get(3).getOutputData().isEmpty());
        assertEquals(TASK_OUTPUT_PATH, workflow.getTasks().get(3).getExternalOutputPayloadStoragePath());

        // Polling for task after the JOIN task
        Task task4 = workflowExecutionService.poll("junit_task_4", "junit.worker.task_4");
        assertNotNull(task4);
        assertEquals("junit_task_4", task4.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task4.getTaskId()));
        assertEquals(workflowId, task4.getWorkflowInstanceId());

        task4.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task4);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(6, workflow.getTasks().size());
        assertTrue("The task_2 output should not be persisted", workflow.getTasks().get(2).getOutputData().isEmpty());
        assertEquals(TASK_OUTPUT_PATH, workflow.getTasks().get(3).getExternalOutputPayloadStoragePath());
        assertTrue("The JOIN task output should not be persisted", workflow.getTasks().get(3).getOutputData().isEmpty());
        assertEquals(TASK_OUTPUT_PATH, workflow.getTasks().get(3).getExternalOutputPayloadStoragePath());
        assertTrue("The task_3 output should not be persisted", workflow.getTasks().get(4).getOutputData().isEmpty());
        assertEquals(TASK_OUTPUT_PATH, workflow.getTasks().get(3).getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testWorkflowWithSubWorkflowUsingExternalPayloadStorage() {
        createWorkflow_TaskSubworkflowTask();
        WorkflowDef workflowDef = metadataService.getWorkflowDef(WF_T1_SWF_T2, 1);
        assertNotNull(workflowDef);

        String workflowInputPath = INITIAL_WORKFLOW_INPUT_PATH;
        String correlationId = "subwf_external_storage";
        String workflowId = workflowExecutor.startWorkflow(WF_T1_SWF_T2, 1, correlationId, null, workflowInputPath, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "junit.worker.task_1");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update first task with COMPLETED and using external payload storage for output data
        String taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue("The first task output should not be persisted", workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue("The second task input should not be persisted", workflow.getTasks().get(1).getInputData().isEmpty());
        assertEquals(taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertEquals(INPUT_PAYLOAD_PATH, workflow.getTasks().get(1).getExternalInputPayloadStoragePath());
        assertEquals(SUB_WORKFLOW.name(), workflow.getTasks().get(1).getTaskType());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("swt").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        // Polling for the task within the sub_workflow
        task = workflowExecutionService.poll("junit_task_3", "task3.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_3", task.getTaskType());
        assertEquals(IN_PROGRESS, task.getStatus());
        assertEquals("TEST_SAMPLE", task.getInputData().get("p1"));
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        // Get the sub-workflow
        String subWorkflowId = task.getWorkflowInstanceId();
        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(workflowId, subWorkflow.getParentWorkflowId());
        assertTrue("The sub-workflow input should not be persisted", subWorkflow.getInput().isEmpty());
        assertEquals(INPUT_PAYLOAD_PATH, subWorkflow.getExternalInputPayloadStoragePath());
        assertEquals(subWorkflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());

        // update the task within sub-workflow to COMPLETED
        taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        // check the sub workflow
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(1, subWorkflow.getTasks().size());
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertTrue(subWorkflow.getOutput().isEmpty());
        assertNotNull(subWorkflow.getExternalOutputPayloadStoragePath());
        assertEquals(WORKFLOW_OUTPUT_PATH, subWorkflow.getExternalOutputPayloadStoragePath());

        // check the workflow
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());

        // Check if subworkflow task has external payload path copied from subworkflow
        Task subWorkflowTask = workflow.getTasks().stream()
                .filter(wtask -> wtask.getTaskType().equals(SUB_WORKFLOW.name()))
                .collect(Collectors.toList()).get(0);

        assertEquals(subWorkflowTask.getStatus(), COMPLETED);
        assertTrue(subWorkflowTask.getOutputData().isEmpty());
        assertNotNull(subWorkflowTask.getExternalOutputPayloadStoragePath());

        // Polling for the last task
        task = workflowExecutionService.poll("junit_task_2", "junit.worker.task_2");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());
        assertTrue("The task input should not be persisted", task.getInputData().isEmpty());
        assertEquals(INPUT_PAYLOAD_PATH, task.getExternalInputPayloadStoragePath());

        // update last task with COMPLETED and using external payload storage for output data
        taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // check the parent workflow
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals(WORKFLOW_OUTPUT_PATH, workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testExecutionTimes() {

        String taskName = "junit_task_1";
        TaskDef taskDef = notFoundSafeGetTaskDef(taskName);
        taskDef.setTimeoutSeconds(10);
        metadataService.updateTaskDef(taskDef);

        metadataService.registerTaskDef(Collections.singletonList(taskDef));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_execution_times_wf");
        workflowDef.setSchemaVersion(2);

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        workflowTask1.setTaskReferenceName("task1");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_1");
        workflowTask2.setTaskReferenceName("task2");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_1");
        workflowTask3.setTaskReferenceName("task3");

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType(TaskType.FORK_JOIN.name());
        forkTask.setName("forktask1");
        forkTask.setTaskReferenceName("forktask1");

        forkTask.getForkTasks().add(Collections.singletonList(workflowTask2));
        forkTask.getForkTasks().add(Collections.singletonList(workflowTask3));

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType(TaskType.JOIN.name());
        joinTask.setTaskReferenceName("jointask");
        joinTask.setJoinOn(Arrays.asList("task2", "task3"));

        Map<String, Object> decisionInputParameters = new HashMap<>();
        decisionInputParameters.put("case", "a");

        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setName("decision1");
        decisionTask.setTaskReferenceName("decision1");
        decisionTask.setInputParameters(decisionInputParameters);
        decisionTask.setDefaultCase(Collections.singletonList(workflowTask1));
        decisionTask.setCaseValueParam("case");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("a", Arrays.asList(forkTask, joinTask));
        decisionTask.setDecisionCases(decisionCases);

        workflowDef.getTasks().add(decisionTask);

        assertNotNull(workflowDef);

        metadataService.registerWorkflowDef(workflowDef);

        Map workflowInput = Collections.emptyMap();
        //noinspection unchecked
        String workflowId = startOrLoadWorkflowExecution(workflowDef.getName(), workflowDef.getVersion(), "test", workflowInput, null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);

        assertNotNull(workflow);
        assertEquals(5, workflow.getTasks().size());

        Task task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(Status.COMPLETED);
        workflowExecutionService.updateTask(task);

        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());

        workflow.getTasks().forEach(workflowTask -> {
            assertTrue(workflowTask.getScheduledTime() <= workflowTask.getStartTime());
            assertTrue("" + (workflowTask.getStartTime() - workflowTask.getEndTime()), workflowTask.getStartTime() <= workflowTask.getEndTime());
        });

        assertEquals("decision1", workflow.getTasks().get(0).getReferenceTaskName());
        assertEquals("forktask1", workflow.getTasks().get(1).getReferenceTaskName());
        assertEquals("task2", workflow.getTasks().get(2).getReferenceTaskName());
        assertEquals("task3", workflow.getTasks().get(3).getReferenceTaskName());
        assertEquals("jointask", workflow.getTasks().get(4).getReferenceTaskName());

        metadataService.unregisterWorkflowDef(workflowDef.getName(), 1);

    }

    @Test
    public void testRetryWorkflowUsingExternalPayloadStorage() {
        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(found);

        Map<String, Object> outputParameters = found.getOutputParameters();
        outputParameters.put("workflow_output", "${t1.output.op}");
        metadataService.updateWorkflowDef(found);

        String taskName = "junit_task_2";
        TaskDef taskDef = metadataService.getTaskDef(taskName);
        taskDef.setRetryCount(2);
        taskDef.setRetryDelaySeconds(0);
        metadataService.updateTaskDef(taskDef);

        String workflowInputPath = INITIAL_WORKFLOW_INPUT_PATH;
        String correlationId = "wf_external_storage";
        String workflowId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, null, workflowInputPath, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(workflow.getReasonForIncompletion(), WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());

        // Polling for the first task
        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_1", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update first task with COMPLETED
        String taskOutputPath = TASK_OUTPUT_PATH;
        task.setOutputData(null);
        task.setExternalOutputPayloadStoragePath(taskOutputPath);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // Polling for the second task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(task.getInputData().isEmpty());
        assertNotNull(task.getExternalInputPayloadStoragePath());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update second task with FAILED
        task.getOutputData().put("op", "failed_task2");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());

        // Polling again for the second task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(task.getInputData().isEmpty());
        assertNotNull(task.getExternalInputPayloadStoragePath());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // update second task with COMPLETED
        task.getOutputData().put("op", "success_task2");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertTrue("The workflow input should not be persisted", workflow.getInput().isEmpty());
        assertEquals(workflowInputPath, workflow.getExternalInputPayloadStoragePath());
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(3, workflow.getTasks().size());
        assertTrue("The first task output should not be persisted", workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue("The second task input should not be persisted", workflow.getTasks().get(1).getInputData().isEmpty());
        assertTrue("The second task input should not be persisted", workflow.getTasks().get(2).getInputData().isEmpty());
        assertEquals(taskOutputPath, workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertEquals(INPUT_PAYLOAD_PATH, workflow.getTasks().get(1).getExternalInputPayloadStoragePath());
        assertEquals(INPUT_PAYLOAD_PATH, workflow.getTasks().get(2).getExternalInputPayloadStoragePath());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals(WORKFLOW_OUTPUT_PATH, workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testRateLimiting() {
        // Create a dynamic workflow definition with one simple task
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_concurrency_limits");
        workflowDef.setVersion(1);

        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task_with_ratelimits");
        taskDef.setRateLimitFrequencyInSeconds(600);
        taskDef.setRateLimitPerFrequency(1);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("test_task_with_ratelimits");
        workflowTask.setName("test_task_with_ratelimits");
        workflowTask.setType(UserTask.NAME);
        workflowTask.setTaskDefinition(taskDef);
        Map<String, Object> userIP = new HashMap<>();

        workflowDef.setTasks(Arrays.asList(workflowTask));

        String workflowInstanceId1 = workflowExecutor.startWorkflow(workflowDef, new HashMap<>(),
                "",
                "",
                0,
                "",
                "",
                "",
                new HashMap<>());

        assertNotNull(workflowInstanceId1);

        Workflow workflow1 = workflowExecutionService.getExecutionStatus(workflowInstanceId1, true);
        assertNotNull(workflow1);
        assertEquals(RUNNING, workflow1.getStatus());
        assertEquals(1, workflow1.getTasks().size());        //The very first task is the one that should be scheduled.

        UserTask userTask = new UserTask();

        Task task = workflow1.getTasks().get(0);
        workflowExecutor.executeSystemTask(userTask, task.getTaskId(), 30);

        workflow1 = workflowExecutionService.getExecutionStatus(workflowInstanceId1, true);

        String workflowInstanceId2 = workflowExecutor.startWorkflow(workflowDef, new HashMap<>(),
                "",
                "",
                0,
                "",
                "",
                "",
                new HashMap<>());

        assertNotNull(workflowInstanceId2);

        Workflow workflow2 = workflowExecutionService.getExecutionStatus(workflowInstanceId2, true);
        assertNotNull(workflow2);
        assertEquals(RUNNING, workflow2.getStatus());
        assertEquals(1, workflow2.getTasks().size());        //The very first task is the one that should be scheduled.

        // Try to execute second task
        Task task2 = workflow2.getTasks().get(0);
        workflowExecutor.executeSystemTask(userTask, task2.getTaskId(), 30);
        workflow2 = workflowExecutionService.getExecutionStatus(workflowInstanceId2, true);
        task2 = workflow2.getTasks().get(0);
        assertEquals(SCHEDULED, task2.getStatus());
    }

    @Test
    public void testSimpleWorkflowWithOptionalTask() throws Exception {
        createOptionalTaskWorkflow();

        metadataService.getWorkflowDef(WORKFLOW_WITH_OPTIONAL_TASK, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> workflowInput = new HashMap<>();
        String inputParam1 = "p1 value";
        workflowInput.put("param1", inputParam1);
        workflowInput.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution(WORKFLOW_WITH_OPTIONAL_TASK, 1, correlationId, workflowInput, null, null);
        logger.debug("testSimpleWorkflowWithOptionalTask.wfid=" + workflowId);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.
        assertEquals(1, queueDAO.getSize("task_optional"));

        // Polling for the first task should return the first task
        Task task = workflowExecutionService.poll("task_optional", "task1.junit.worker.optional");
        assertNotNull(task);
        assertEquals("task_optional", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowId, task.getWorkflowInstanceId());

        // As the task_optional is out of the queue, the next poll should not get it
        Task nullTask = workflowExecutionService.poll("task_optional", "task1.junit.worker.optional");
        assertNull(nullTask);

        TaskResult taskResult = new TaskResult(task);
        taskResult.setReasonForIncompletion("NETWORK ERROR");
        taskResult.setStatus(TaskResult.Status.FAILED);

        workflowExecutionService.updateTask(taskResult);

        workflowExecutor.decide(workflowId);
        assertEquals(1, queueDAO.getSize("task_optional"));

        // The first task would be failed and a new task will be scheduled
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertTrue(workflow.getTasks().stream().allMatch(t -> t.getReferenceTaskName().equals("task_optional_t1")));
        assertEquals(FAILED, workflow.getTasks().get(0).getStatus());
        assertEquals(SCHEDULED, workflow.getTasks().get(1).getStatus());

        // Polling now should get the same task back because it should have been put back in the queue
        Task taskAgain = workflowExecutionService.poll("task_optional", "task1.junit.worker");
        assertNotNull(taskAgain);

        Thread.sleep(5000);

        // The second task would be timed-out and completed with errors
        workflowExecutor.decide(workflowId);
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        assertEquals(0, queueDAO.getSize("task_optional"));
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        System.out.println(workflow.getTasks());
        System.out.println(workflow.getTasks().get(1));
        System.out.println(workflow.getTasks().get(2));
        assertEquals(3, workflow.getTasks().size());
        assertEquals(COMPLETED_WITH_ERRORS, workflow.getTasks().get(1).getStatus());

        // poll for next task
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker.testTimeout");
        assertNotNull(task);
        assertEquals("junit_task_2", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        task.setStatus(COMPLETED);
        task.setReasonForIncompletion("unit test failure");
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    private void createOptionalTaskWorkflow() {
        TaskDef task = new TaskDef();
        task.setName("task_optional");
        task.setTimeoutSeconds(5);
        task.setRetryCount(RETRY_COUNT);
        task.setTimeoutPolicy(TimeoutPolicy.RETRY);
        task.setRetryDelaySeconds(0);

        metadataService.registerTaskDef(Collections.singletonList(task));

        WorkflowDef def = new WorkflowDef();
        def.setName(WORKFLOW_WITH_OPTIONAL_TASK);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${t2.output.uuid}");
        outputParameters.put("o3", "${t1.output.op}");
        def.setOutputParameters(outputParameters);
        def.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("task_optional");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setOptional(true);
        wft1.setTaskReferenceName("task_optional_t1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "${workflow.input.param1}");
        ip2.put("tp2", "${t1.output.op}");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        wftasks.add(wft1);
        wftasks.add(wft2);
        def.setTasks(wftasks);

        metadataService.updateWorkflowDef(def);
    }

    @Test
    public void testSubWorkflowTaskToDomain() {
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("junit_task_1", "unittest1");
        taskToDomain.put("junit_task_2", "unittest2");
        createSubWorkflow(taskToDomain);
        metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 2 value");

        input.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String workflowId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull("Output: " + task.getOutputData().toString() + ", status: " + task.getStatus(), task.getSubWorkflowId());
        assertNotNull(task.getInputData());
        assertTrue(task.getInputData().containsKey("workflowInput"));
        assertEquals(42, ((Map<String, Object>) task.getInputData().get("workflowInput")).get("param2"));
        String subWorkflowId = task.getSubWorkflowId();

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertNotNull(subWorkflow.getTasks());
        assertEquals(workflowId, subWorkflow.getParentWorkflowId());
        assertEquals(RUNNING, subWorkflow.getStatus());

        task = workflowExecutionService.poll("junit_task_1", "test", "unittest1");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "test", "unittest2");
        assertEquals(subWorkflowId, task.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        task.getOutputData().put("uuid", uuid);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertNotNull(subWorkflow.getOutput());
        assertTrue(subWorkflow.getOutput().containsKey("o1"));
        assertTrue(subWorkflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", subWorkflow.getOutput().get("o1"));
        assertEquals(uuid, subWorkflow.getOutput().get("o2"));
        assertEquals(taskToDomain, subWorkflow.getTaskToDomain());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        task = workflowExecutionService.poll("junit_task_6", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    public void testSubWorkflowTaskToDomainWildcard() {
        Map<String, String> taskToDomain = new HashMap<>();
        taskToDomain.put("*", "unittest");
        createSubWorkflow(taskToDomain);
        metadataService.getWorkflowDef(WF_WITH_SUB_WF, 1);
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param 1 value");
        input.put("param3", "param 2 value");

        input.put("wfName", LINEAR_WORKFLOW_T1_T2);
        String workflowId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("a2").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull("Output: " + task.getOutputData().toString() + ", status: " + task.getStatus(), task.getSubWorkflowId());
        assertNotNull(task.getInputData());
        assertTrue(task.getInputData().containsKey("workflowInput"));
        assertEquals(42, ((Map<String, Object>) task.getInputData().get("workflowInput")).get("param2"));
        String subWorkflowId = task.getSubWorkflowId();

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertNotNull(subWorkflow.getTasks());
        assertEquals(workflowId, subWorkflow.getParentWorkflowId());
        assertEquals(RUNNING, subWorkflow.getStatus());

        task = workflowExecutionService.poll("junit_task_1", "test", "unittest");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "test", "unittest");
        assertEquals(subWorkflowId, task.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        task.getOutputData().put("uuid", uuid);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertNotNull(subWorkflow.getOutput());
        assertTrue(subWorkflow.getOutput().containsKey("o1"));
        assertTrue(subWorkflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", subWorkflow.getOutput().get("o1"));
        assertEquals(uuid, subWorkflow.getOutput().get("o2"));
        assertEquals(taskToDomain, subWorkflow.getTaskToDomain());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        task = workflowExecutionService.poll("junit_task_6", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    /**
     * This test verifies that a Subworkflow with Terminate task calls decide on parent, and helps progress it immediately.
     */
    @Test
    public void testTerminateTaskInASubworkflow() {
        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("test_terminate_task_wf");
        subWorkflowDef.setSchemaVersion(2);
        subWorkflowDef.setVersion(1);

        Map<String, Object> lambdaTaskInputParams = new HashMap<>();
        lambdaTaskInputParams.put("input", "${workflow.input}");
        lambdaTaskInputParams.put("scriptExpression", "if ($.input.a==1){return {testvalue: true}} else{return {testvalue: false}}");

        WorkflowTask lambdaWorkflowTask = new WorkflowTask();
        lambdaWorkflowTask.setWorkflowTaskType(TaskType.LAMBDA);
        lambdaWorkflowTask.setName("lambda");
        lambdaWorkflowTask.setInputParameters(lambdaTaskInputParams);
        lambdaWorkflowTask.setTaskReferenceName("lambda0");

        Map<String, Object> terminateTaskInputParams = new HashMap<>();
        terminateTaskInputParams.put(Terminate.getTerminationStatusParameter(), "COMPLETED");
        terminateTaskInputParams.put(Terminate.getTerminationWorkflowOutputParameter(), "${lambda0.output}");

        WorkflowTask terminateWorkflowTask = new WorkflowTask();
        terminateWorkflowTask.setType(TaskType.TASK_TYPE_TERMINATE);
        terminateWorkflowTask.setName("terminate");
        terminateWorkflowTask.setInputParameters(terminateTaskInputParams);
        terminateWorkflowTask.setTaskReferenceName("terminate0");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        workflowTask2.setTaskReferenceName("t2");

        subWorkflowDef.getTasks().addAll(Arrays.asList(lambdaWorkflowTask, terminateWorkflowTask, workflowTask2));

        assertNotNull(subWorkflowDef);
        metadataService.registerWorkflowDef(subWorkflowDef);

        // Create Parent workflow
        WorkflowDef parentWorkflowDef = new WorkflowDef();
        parentWorkflowDef.setName("test_parent_wf_for_terminate_task_subwf");
        parentWorkflowDef.setSchemaVersion(2);

        WorkflowTask subWorkflowTask = new WorkflowTask();
        subWorkflowTask.setWorkflowTaskType(SUB_WORKFLOW);
        subWorkflowTask.setName("subWF");
        subWorkflowTask.setTaskReferenceName("subWF");
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowDef.getName());
        subWorkflowParams.setVersion(subWorkflowDef.getVersion());
        subWorkflowTask.setSubWorkflowParam(subWorkflowParams);

        parentWorkflowDef.getTasks().addAll(Arrays.asList(subWorkflowTask));

        assertNotNull(parentWorkflowDef);
        metadataService.registerWorkflowDef(parentWorkflowDef);

        Map wfInput = Collections.singletonMap("a", 1);
        String workflowId = startOrLoadWorkflowExecution(parentWorkflowDef.getName(), parentWorkflowDef.getVersion(), "", wfInput, null, null);
        Workflow workflow = workflowExecutor.getWorkflow(workflowId, true);

        assertNotNull(workflow);
        assertEquals(1, workflow.getTasks().size());

        SubWorkflow subWorkflowSystemTask = new SubWorkflow();
        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("subWF").getTaskId();
        workflowExecutor.executeSystemTask(subWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        Task task = workflow.getTaskByRefName("subWF");

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(task.getSubWorkflowId(), true);

        assertNotNull(workflow);
        assertNotNull(task);
        assertEquals(COMPLETED, task.getStatus());
        assertNotNull(subWorkflow);
        assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals("tasks:" + subWorkflow.getTasks(), WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertEquals(TaskType.TASK_TYPE_LAMBDA, subWorkflow.getTasks().get(0).getTaskType());
        assertEquals(TaskType.TASK_TYPE_TERMINATE, subWorkflow.getTasks().get(1).getTaskType());
        assertEquals(subWorkflow.getTasks().get(1).getOutputData(), subWorkflow.getOutput());
        assertEquals(SUB_WORKFLOW.name(), workflow.getTasks().get(0).getTaskType());

        metadataService.unregisterWorkflowDef(parentWorkflowDef.getName(), parentWorkflowDef.getVersion());
        metadataService.unregisterWorkflowDef(subWorkflowDef.getName(), subWorkflowDef.getVersion());
    }

    @Test
    public void testPollWithConcurrentExecutionLimits() {
        // Create a dynamic workflow definition with one simple task
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("test_concurrency_limits");
        workflowDef.setVersion(1);

        TaskDef taskDef = new TaskDef();
        taskDef.setName("test_task_with_cl");
        taskDef.setConcurrentExecLimit(1);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("test_task_with_cl");
        workflowTask.setName("test_task_with_cl");
        workflowTask.setType("SIMPLE");
        workflowTask.setTaskDefinition(taskDef);

        workflowDef.setTasks(Arrays.asList(workflowTask));

        String workflowInstanceId1 = workflowExecutor.startWorkflow(workflowDef, new HashMap<>(),
                "",
                "",
                0,
                "",
                "",
                "",
                new HashMap<>());

        assertNotNull(workflowInstanceId1);

        Workflow workflow1 = workflowExecutionService.getExecutionStatus(workflowInstanceId1, true);
        assertNotNull(workflow1);
        assertEquals(RUNNING, workflow1.getStatus());
        assertEquals(1, workflow1.getTasks().size());        //The very first task is the one that should be scheduled.

        // Polling for the first task
        Task task = workflowExecutionService.poll("test_task_with_cl", "test.worker");
        assertNotNull(task);
        assertEquals("test_task_with_cl", task.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(workflowInstanceId1, task.getWorkflowInstanceId());

        String workflowInstanceId2 = workflowExecutor.startWorkflow(workflowDef, new HashMap<>(),
                "",
                "",
                0,
                "",
                "",
                "",
                new HashMap<>());

        assertNotNull(workflowInstanceId2);

        Workflow workflow2 = workflowExecutionService.getExecutionStatus(workflowInstanceId2, true);
        assertNotNull(workflow2);
        assertEquals(RUNNING, workflow2.getStatus());
        assertEquals(1, workflow2.getTasks().size());        //The very first task is the one that should be scheduled.

        // Polling for the second task
        Task task2 = workflowExecutionService.poll("test_task_with_cl", "test.worker");
        assertNull("Polling for the task shouldn't return anything, as concurrency limit is met.", task2);

        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // Reset task offset time to make it available for poll again.
        queueDAO.resetOffsetTime("test_task_with_cl", workflow2.getTasks().get(0).getTaskId());

        // Polling for the second task
        task2 = workflowExecutionService.poll("test_task_with_cl", "test.worker");
        assertNotNull(task2);
        assertEquals("test_task_with_cl", task2.getTaskType());
        assertTrue(workflowExecutionService.ackTaskReceived(task2.getTaskId()));
        assertEquals(workflowInstanceId2, task2.getWorkflowInstanceId());
    }

    private void createSubWorkflow() {
        createSubWorkflow(null);
    }

    private void createSubWorkflow(Map<String, String> subWorkflowTaskToDomain) {

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_5");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("a1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("subWorkflowTask");
        wft2.setType(SUB_WORKFLOW.name());
        SubWorkflowParams swp = new SubWorkflowParams();
        swp.setName(LINEAR_WORKFLOW_T1_T2);
        swp.setTaskToDomain(subWorkflowTaskToDomain);
        wft2.setSubWorkflowParam(swp);
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("test", "test value");
        ip2.put("param1", "sub workflow input param1");
        ip2.put("param2", 42);
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("a2");

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_6");
        Map<String, Object> ip3 = new HashMap<>();
        ip3.put("p1", "${workflow.input.param1}");
        ip3.put("p2", "${workflow.input.param2}");
        wft3.setInputParameters(ip3);
        wft3.setTaskReferenceName("a3");

        WorkflowDef main = new WorkflowDef();
        main.setSchemaVersion(2);
        main.setInputParameters(Arrays.asList("param1", "param2"));
        main.setName(WF_WITH_SUB_WF);
        main.getTasks().addAll(Arrays.asList(wft1, wft2, wft3));

        metadataService.updateWorkflowDef(Collections.singletonList(main));

    }

    private void verify(String inputParam1, String wfid, String task1Op, boolean fail) {
        Task task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String task2Input = (String) task.getInputData().get("tp2");
        assertNotNull(task2Input);
        assertEquals(task1Op, task2Input);
        task2Input = (String) task.getInputData().get("tp1");
        assertNotNull(task2Input);
        assertEquals(inputParam1, task2Input);
        if (fail) {
            task.setStatus(FAILED);
            task.setReasonForIncompletion("failure...0");
        } else {
            task.setStatus(COMPLETED);
        }

        workflowExecutionService.updateTask(task);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, false);
        assertNotNull(es);
        if (fail) {
            assertEquals(RUNNING, es.getStatus());
        } else {
            assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        }
    }

    @Before
    public void flushAllTaskQueues() {
        queueDAO.queuesDetail().keySet().forEach(queueName -> {
            queueDAO.flush(queueName);
        });

        if (taskDefs == null) {
            return;
        }
        for (TaskDef td : taskDefs) {
            queueDAO.flush(td.getName());
        }
    }

    private void createWorkflow_TaskSubworkflowTask() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WF_T1_SWF_T2);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o3", "${t1.output.op}");
        workflowDef.setOutputParameters(outputParameters);
        workflowDef.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("tp11", "${workflow.input.param1}");
        ip1.put("tp12", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        // create the sub-workflow def
        WorkflowDef subWorkflowDef = new WorkflowDef();
        subWorkflowDef.setName("one_task_workflow");
        subWorkflowDef.setVersion(1);
        subWorkflowDef.setInputParameters(Arrays.asList("imageType", "op"));
        outputParameters = new HashMap<>();
        outputParameters.put("op", "${t3.output.op}");
        subWorkflowDef.setOutputParameters(outputParameters);
        subWorkflowDef.setSchemaVersion(2);
        LinkedList<WorkflowTask> subWfTasks = new LinkedList<>();

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        Map<String, Object> ip3 = new HashMap<>();
        ip3.put("p1", "${workflow.input.imageType}");
        wft3.setInputParameters(ip3);
        wft3.setTaskReferenceName("t3");

        subWfTasks.add(wft3);
        subWorkflowDef.setTasks(subWfTasks);
        metadataService.updateWorkflowDef(subWorkflowDef);

        // create the sub workflow task
        WorkflowTask subWorkflow = new WorkflowTask();
        subWorkflow.setType(SUB_WORKFLOW.name());
        SubWorkflowParams sw = new SubWorkflowParams();
        sw.setName("one_task_workflow");
        subWorkflow.setSubWorkflowParam(sw);
        subWorkflow.setTaskReferenceName("swt");
        Map<String, Object> ipsw = new HashMap<>();
        ipsw.put("imageType", "${t1.output.imageType}");
        ipsw.put("op", "${t1.output.op}");
        subWorkflow.setInputParameters(ipsw);

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("op", "${t1.output.op}");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        wftasks.add(wft1);
        wftasks.add(subWorkflow);
        wftasks.add(wft2);
        workflowDef.setTasks(wftasks);

        metadataService.updateWorkflowDef(workflowDef);
    }

    private void createWorkflowWithSubWorkflow() {
        WorkflowDef defSW = new WorkflowDef();
        defSW.setName(LINEAR_WORKFLOW_T1_T2_SW);
        defSW.setDescription(defSW.getName());
        defSW.setVersion(1);
        defSW.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${t2.output.uuid}");
        outputParameters.put("o3", "${t1.output.op}");
        defSW.setOutputParameters(outputParameters);
        defSW.setFailureWorkflow("$workflow.input.failureWfName");
        defSW.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_3");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask subWorkflow = new WorkflowTask();
        subWorkflow.setType(SUB_WORKFLOW.name());
        SubWorkflowParams sw = new SubWorkflowParams();
        sw.setName(LINEAR_WORKFLOW_T1_T2);
        subWorkflow.setSubWorkflowParam(sw);
        subWorkflow.setTaskReferenceName("sw1");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "${workflow.input.param1}");
        ip2.put("tp2", "${t1.output.op}");
        subWorkflow.setInputParameters(ip2);

        wftasks.add(wft1);
        wftasks.add(subWorkflow);
        defSW.setTasks(wftasks);

        try {
            metadataService.updateWorkflowDef(defSW);
        } catch (Exception e) {
        }
    }

    private void createConditionalWFWithSystemTask() {
        WorkflowDef defConditionalHttp = new WorkflowDef();
        defConditionalHttp.setName(CONDITIONAL_SYSTEM_WORKFLOW);
        defConditionalHttp.setDescription(defConditionalHttp.getName());
        defConditionalHttp.setVersion(1);
        defConditionalHttp.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o2", "${t1.output.op}");
        defConditionalHttp.setOutputParameters(outputParameters);
        defConditionalHttp.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("tp11", "${workflow.input.param1}");
        ip1.put("tp12", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        TaskDef taskDef = new TaskDef();
        taskDef.setName("user_task");
        taskDef.setTimeoutSeconds(20);
        taskDef.setRetryCount(1);
        taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
        taskDef.setRetryDelaySeconds(10);
        metadataService.registerTaskDef(Collections.singletonList(taskDef));

        WorkflowTask userTask = new WorkflowTask();
        userTask.setName(taskDef.getName());
        userTask.setType(UserTask.NAME);
        Map<String, Object> userIP = new HashMap<>();
        userIP.put("largeInput", "${t1.output.op}");
        userTask.setInputParameters(userIP);
        userTask.setTaskReferenceName("user_task");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp21", "${workflow.input.param1}");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setCaseValueParam("case");
        decisionTask.setName("conditional2");
        decisionTask.setTaskReferenceName("conditional2");
        Map<String, Object> decisionIP = new HashMap<>();
        decisionIP.put("case", "${t1.output.case}");
        decisionTask.setInputParameters(decisionIP);
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("one", Collections.singletonList(wft2));
        decisionCases.put("two", Collections.singletonList(userTask));
        decisionTask.setDecisionCases(decisionCases);

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        Map<String, Object> ip3 = new HashMap<>();
        ip3.put("tp31", "${workflow.input.param2}");
        wft3.setInputParameters(ip3);
        wft3.setTaskReferenceName("t3");

        wftasks.add(wft1);
        wftasks.add(decisionTask);
        wftasks.add(wft3);
        defConditionalHttp.setTasks(wftasks);

        metadataService.updateWorkflowDef(defConditionalHttp);
    }

    private void createWFWithResponseTimeout() {
        TaskDef task = new TaskDef();
        task.setName("task_rt");
        task.setTimeoutSeconds(120);
        task.setRetryCount(RETRY_COUNT);
        task.setRetryDelaySeconds(0);
        task.setResponseTimeoutSeconds(10);
        metadataService.registerTaskDef(Collections.singletonList(task));

        WorkflowDef def = new WorkflowDef();
        def.setName("RTOWF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));
        Map<String, Object> outputParameters = new HashMap<>();
        outputParameters.put("o1", "${workflow.input.param1}");
        outputParameters.put("o2", "${t2.output.uuid}");
        outputParameters.put("o3", "${t1.output.op}");
        def.setOutputParameters(outputParameters);
        def.setFailureWorkflow("$workflow.input.failureWfName");
        def.setSchemaVersion(2);
        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("task_rt");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("task_rt_t1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "${workflow.input.param1}");
        ip2.put("tp2", "${t1.output.op}");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        wftasks.add(wft1);
        wftasks.add(wft2);
        def.setTasks(wftasks);

        metadataService.updateWorkflowDef(def);
    }

    private void createWorkflowWthMultiLevelSubWorkflows() {
        final String subWorkflowLevel1 = "junit_sw_level_1";
        final String subWorkflowLevel2 = "junit_sw_level_2";
        final String subWorkflowLevel3 = "junit_sw_level_3";

        // level 3
        WorkflowDef workflowDef_level3 = new WorkflowDef();
        workflowDef_level3.setName(subWorkflowLevel3);
        workflowDef_level3.setDescription(workflowDef_level3.getName());
        workflowDef_level3.setVersion(1);
        workflowDef_level3.setSchemaVersion(2);

        LinkedList<WorkflowTask> workflowTasks_level3 = new LinkedList<>();
        WorkflowTask simpleWorkflowTask = new WorkflowTask();
        simpleWorkflowTask.setName("junit_task_3");
        simpleWorkflowTask.setInputParameters(new HashMap<>());
        simpleWorkflowTask.setTaskReferenceName("t1");
        workflowTasks_level3.add(simpleWorkflowTask);
        workflowDef_level3.setTasks(workflowTasks_level3);

        metadataService.updateWorkflowDef(workflowDef_level3);

        // level 2
        WorkflowDef workflowDef_level2 = new WorkflowDef();
        workflowDef_level2.setName(subWorkflowLevel2);
        workflowDef_level2.setDescription(workflowDef_level2.getName());
        workflowDef_level2.setVersion(1);
        workflowDef_level2.setSchemaVersion(2);

        LinkedList<WorkflowTask> workflowTasks_level2 = new LinkedList<>();
        workflowTasks_level2.add(createSubWorkflowTask(subWorkflowLevel3));
        workflowDef_level2.setTasks(workflowTasks_level2);

        metadataService.updateWorkflowDef(workflowDef_level2);

        // level 1
        WorkflowDef workflowDef_level1 = new WorkflowDef();
        workflowDef_level1.setName(subWorkflowLevel1);
        workflowDef_level1.setDescription(workflowDef_level1.getName());
        workflowDef_level1.setVersion(1);
        workflowDef_level1.setSchemaVersion(2);

        LinkedList<WorkflowTask> workflowTasks_level1 = new LinkedList<>();
        workflowTasks_level1.add(createSubWorkflowTask(subWorkflowLevel2));
        workflowDef_level1.setTasks(workflowTasks_level1);

        metadataService.updateWorkflowDef(workflowDef_level1);

        // top-level parent workflow
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WORKFLOW_MULTI_LEVEL_SW);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));
        workflowDef.setSchemaVersion(2);

        LinkedList<WorkflowTask> workflowTasks = new LinkedList<>();
        workflowTasks.add(createSubWorkflowTask(subWorkflowLevel1));
        workflowDef.setTasks(workflowTasks);

        metadataService.updateWorkflowDef(workflowDef);
    }

    private WorkflowTask createSubWorkflowTask(String subWorkflowName) {
        WorkflowTask subWorkflowTask = new WorkflowTask();
        subWorkflowTask.setType(SUB_WORKFLOW.name());
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        subWorkflowTask.setSubWorkflowParam(subWorkflowParams);
        subWorkflowTask.setTaskReferenceName(subWorkflowName + "_task");
        return subWorkflowTask;
    }

    private void createForkJoinWorkflowWithOptionalSubworkflowForks() {
        String taskName = "simple_task_in_sub_wf";
        TaskDef task = new TaskDef();
        task.setName(taskName);
        task.setRetryCount(0);
        metadataService.registerTaskDef(Collections.singletonList(task));

        // sub workflow
        WorkflowDef subworkflow_def = new WorkflowDef();
        subworkflow_def.setName("sub_workflow");
        subworkflow_def.setDescription(subworkflow_def.getName());
        subworkflow_def.setVersion(1);
        subworkflow_def.setSchemaVersion(2);

        LinkedList<WorkflowTask> subworkflowDef_Task = new LinkedList<>();
        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setName(taskName);
        simpleTask.setInputParameters(new HashMap<>());
        simpleTask.setTaskReferenceName("t1");
        subworkflowDef_Task.add(simpleTask);
        subworkflow_def.setTasks(subworkflowDef_Task);

        metadataService.updateWorkflowDef(subworkflow_def);

        // parent workflow
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WORKFLOW_FORK_JOIN_OPTIONAL_SW);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        // fork task
        WorkflowTask fanoutTask = new WorkflowTask();
        fanoutTask.setType(TaskType.FORK_JOIN.name());
        fanoutTask.setTaskReferenceName("fanouttask");

        // sub workflow tasks
        WorkflowTask subWorkflowTask1 = new WorkflowTask();
        subWorkflowTask1.setType(SUB_WORKFLOW.name());
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("sub_workflow");
        subWorkflowTask1.setSubWorkflowParam(subWorkflowParams);
        subWorkflowTask1.setTaskReferenceName("st1");
        subWorkflowTask1.setOptional(true);

        WorkflowTask subWorkflowTask2 = new WorkflowTask();
        subWorkflowTask2.setType(SUB_WORKFLOW.name());
        subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("sub_workflow");
        subWorkflowTask2.setSubWorkflowParam(subWorkflowParams);
        subWorkflowTask2.setTaskReferenceName("st2");
        subWorkflowTask2.setOptional(true);

        // join task
        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType(TaskType.JOIN.name());
        joinTask.setTaskReferenceName("fanouttask_join");
        joinTask.setJoinOn(Arrays.asList("st1", "st2"));

        fanoutTask.getForkTasks().add(Collections.singletonList(subWorkflowTask1));
        fanoutTask.getForkTasks().add(Collections.singletonList(subWorkflowTask2));

        workflowDef.getTasks().add(fanoutTask);
        workflowDef.getTasks().add(joinTask);
        metadataService.updateWorkflowDef(workflowDef);
    }

    private String runWorkflowWithSubworkflow() throws Exception {
        clearWorkflows();
        createWorkflowWithSubWorkflow();

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2_SW, 1);

        String correlationId = "unit_test_sw";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");

        String workflowId = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2_SW, 1, correlationId, input, null, null);
        System.out.println("testSimpleWorkflow.wfid=" + workflowId);
        assertNotNull(workflowId);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        // Poll for first task and execute it
        Task task = workflowExecutionService.poll("junit_task_3", "task3.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        task.getOutputData().put("op", "junit_task_3.done");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        // Simulating SystemTaskWorkerCoordinator to execute async system tasks
        String subWorkflowTaskId = workflow.getTaskByRefName("sw1").getTaskId();
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);

        // Get the sub workflow id
        String subWorkflowId = null;
        for (Task t : workflow.getTasks()) {
            if (t.getTaskType().equalsIgnoreCase("SUB_WORKFLOW")) {
                subWorkflowId = t.getSubWorkflowId();
            }
        }
        assertNotNull(subWorkflowId);

        Workflow subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(RUNNING, subWorkflow.getStatus());
        assertEquals(1, subWorkflow.getTasks().size());

        // Now the Sub workflow is triggered
        // Poll for first task of the sub workflow and execute it
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        task.getOutputData().put("op", "junit_task_1.done");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(RUNNING, subWorkflow.getStatus());
        assertEquals(2, subWorkflow.getTasks().size());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        // Poll for second task of the sub workflow and execute it
        task = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        task.getOutputData().put("op", "junit_task_2.done");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        // Now the sub workflow and the main workflow must have finished
        subWorkflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(subWorkflow);
        assertEquals(WorkflowStatus.COMPLETED, subWorkflow.getStatus());
        assertEquals(2, subWorkflow.getTasks().size());

        // Execute again to re-evaluate the Subworkflow task.
        workflowExecutor.executeSystemTask(dummySubWorkflowSystemTask, subWorkflowTaskId, 1);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());

        return workflowId;
    }

    private String runAFailedForkJoinWF() throws Exception {
        try {
            this.createForkJoinWorkflowWithZeroRetry();
        } catch (Exception e) {
        }

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(FORK_JOIN_WF + "_2", 1, "fanouttest", input, null, null);
        System.out.println("testForkJoin.wfid=" + workflowId);
        Task t1 = workflowExecutionService.poll("junit_task_0_RT_1", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_0_RT_2", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));
        assertNotNull(t1);
        assertNotNull(t2);

        t1.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t1);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());
        printTaskStatuses(workflow, "Initial");

        t2.setStatus(FAILED);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<?> future1 = executorService.submit(() -> {
            try {
                workflowExecutionService.updateTask(t2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        future1.get();

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        return workflowId;
    }

    private String runAFailedDoWhileWF() throws Exception {
        try {
            this.createForkJoinWorkflowWithZeroRetry();
        } catch (Exception e) {
        }

        Map<String, Object> input = new HashMap<>();
        String workflowId = startOrLoadWorkflowExecution(FORK_JOIN_WF + "_2", 1, "fanouttest", input, null, null);
        System.out.println("testForkJoin.wfid=" + workflowId);
        Task t1 = workflowExecutionService.poll("junit_task_0_RT_1", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t1.getTaskId()));

        Task t2 = workflowExecutionService.poll("junit_task_0_RT_2", "test");
        assertTrue(workflowExecutionService.ackTaskReceived(t2.getTaskId()));
        assertNotNull(t1);
        assertNotNull(t2);

        t1.setStatus(COMPLETED);
        workflowExecutionService.updateTask(t1);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals("Found " + workflow.getTasks(), RUNNING, workflow.getStatus());
        printTaskStatuses(workflow, "Initial");

        t2.setStatus(FAILED);
        workflowExecutionService.updateTask(t2);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        return workflowId;
    }

    private void printTaskStatuses(String wfid, String message) {
        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        printTaskStatuses(wf, message);
    }

    private String startOrLoadWorkflowExecution(String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain) {
        return startOrLoadWorkflowExecution(workflowName, workflowName, version, correlationId, input, event, taskToDomain);
    }

    abstract String startOrLoadWorkflowExecution(String snapshotResourceName, String workflowName, int version, String correlationId, Map<String, Object> input, String event, Map<String, String> taskToDomain);

    private boolean printWFTaskDetails = false;

    private void printTaskStatuses(Workflow wf, String message) {
        if (printWFTaskDetails) {
            System.out.println(message + " >>> Workflow status " + wf.getStatus().name());
            wf.getTasks().forEach(t -> {
                System.out.println("Task " + String.format("%-15s", t.getTaskType()) + "\t" + String.format("%-15s", t.getReferenceTaskName()) + "\t" + String.format("%-15s", t.getWorkflowTask().getType()) + "\t" + t.getSeq() + "\t" + t.getStatus() + "\t" + t.getTaskId());
            });
            System.out.println();
        }
    }
}
