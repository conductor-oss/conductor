/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.tests.integration;

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
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.WorkflowSweeper;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
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

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.FAILED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.TIMED_OUT;
import static com.netflix.conductor.common.run.Workflow.WorkflowStatus.RUNNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractWorkflowServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWorkflowServiceTest.class);

    private static final String COND_TASK_WF = "ConditionalTaskWF";

    private static final String FORK_JOIN_NESTED_WF = "FanInOutNestedTest";

    private static final String FORK_JOIN_WF = "FanInOutTest";

    private static final String DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest";

    private static final String DYNAMIC_FORK_JOIN_WF_LEGACY = "DynamicFanInOutTestLegacy";

    private static final int RETRY_COUNT = 1;
    private static final String JUNIT_TEST_WF_NON_RESTARTABLE = "junit_test_wf_non_restartable";
    private static final String WF_WITH_SUB_WF = "WorkflowWithSubWorkflow";

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Inject
    protected ExecutionService workflowExecutionService;

    @Inject
    protected SubWorkflow subworkflow;

    @Inject
    protected MetadataService metadataService;

    @Inject
    protected WorkflowSweeper workflowSweeper;

    @Inject
    protected QueueDAO queueDAO;

    @Inject
    protected WorkflowExecutor workflowExecutor;

    @Inject
    protected MetadataMapperService metadataMapperService;

    private static boolean registered;

    private static List<TaskDef> taskDefs;

    private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";

    private static final String LINEAR_WORKFLOW_T1_T2_SW = "junit_test_wf_sw";

    private static final String LONG_RUNNING = "longRunningWf";

    private static final String TEST_WORKFLOW_NAME_3 = "junit_test_wf3";

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
        def2.setName(TEST_WORKFLOW_NAME_3);
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

        ObjectMapper om = new ObjectMapper();

        //Use the commented sysout to get the string value
        //System.out.println(om.writeValueAsString(om.writeValueAsString(taskInput)));
        String expected = "{\"http_request\":{\"method\":\"GET\",\"vipStack\":\"test_stack\",\"body\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]},\"uri\":\"/get/something\"}}";
        assertEquals(expected, om.writeValueAsString(taskInput));
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
        assertEquals(1, found1.getSchemaVersion());

    }

    @Test
    public void testForkJoin() throws Exception {
        try {
            createForkJoinWorkflow();
        } catch (Exception e) {
        }
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
        System.out.println("testForkJoin.wfid=" + workflowId);
        printTaskStatuses(workflowId, "initiated");

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

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
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
    public void testForkJoinNested() {

        createForkJoinNestedWorkflow();

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

        createForkJoinNestedWorkflowWithSubworkflow();

        Map<String, Object> input = new HashMap<>();
        input.put("case", "a");        //This should execute t16 and t19
        String wfid = startOrLoadWorkflowExecution(FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input, null, null);
        System.out.println("testForkJoinNested.wfid=" + wfid);

        Workflow wf = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(wf);
        assertEquals(RUNNING, wf.getStatus());

        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t11")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t12")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t13")));
        assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("sw1")));
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

        String[] tasks = new String[]{"junit_task_1", "junit_task_2", "junit_task_14", "junit_task_16"};
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
            createDynamicForkJoinWorkflowDefsLegacy();
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

    private void createForkJoinWorkflow() {

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(FORK_JOIN_WF);
        workflowDef.setDescription(workflowDef.getName());
        workflowDef.setVersion(1);
        workflowDef.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask fanoutTask = new WorkflowTask();
        fanoutTask.setType(TaskType.FORK_JOIN.name());
        fanoutTask.setTaskReferenceName("fanouttask");

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        Map<String, Object> inputParams1 = new HashMap<>();
        inputParams1.put("p1", "workflow.input.param1");
        inputParams1.put("p2", "workflow.input.param2");
        workflowTask1.setInputParameters(inputParams1);
        workflowTask1.setTaskReferenceName("t1");

        WorkflowTask workflowTask3 = new WorkflowTask();
        workflowTask3.setName("junit_task_3");
        workflowTask3.setInputParameters(inputParams1);
        workflowTask3.setTaskReferenceName("t3");

        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setName("junit_task_2");
        Map<String, Object> inputParams2 = new HashMap<>();
        inputParams2.put("tp1", "workflow.input.param1");
        workflowTask2.setInputParameters(inputParams2);
        workflowTask2.setTaskReferenceName("t2");

        WorkflowTask workflowTask4 = new WorkflowTask();
        workflowTask4.setName("junit_task_4");
        workflowTask4.setInputParameters(inputParams2);
        workflowTask4.setTaskReferenceName("t4");

        fanoutTask.getForkTasks().add(Arrays.asList(workflowTask1, workflowTask3));
        fanoutTask.getForkTasks().add(Collections.singletonList(workflowTask2));

        workflowDef.getTasks().add(fanoutTask);

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setType(TaskType.JOIN.name());
        joinTask.setTaskReferenceName("fanouttask_join");
        joinTask.setJoinOn(Arrays.asList("t3", "t2"));

        workflowDef.getTasks().add(joinTask);
        workflowDef.getTasks().add(workflowTask4);
        metadataService.updateWorkflowDef(workflowDef);
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

    private void createForkJoinNestedWorkflow() {

        WorkflowDef def = new WorkflowDef();
        def.setName(FORK_JOIN_NESTED_WF);
        def.setDescription(def.getName());
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

    private void createForkJoinNestedWorkflowWithSubworkflow() {

        WorkflowDef def = new WorkflowDef();
        def.setName(FORK_JOIN_NESTED_WF);
        def.setDescription(def.getName());
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
        subWorkflow.setType(TaskType.SUB_WORKFLOW.name());
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
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask workflowTask1 = new WorkflowTask();
        workflowTask1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        workflowTask1.setInputParameters(ip1);
        workflowTask1.setTaskReferenceName("dt1");

        WorkflowTask fanout = new WorkflowTask();
        fanout.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        fanout.setTaskReferenceName("dynamicfanouttask");
        fanout.setDynamicForkTasksParam("dynamicTasks");
        fanout.setDynamicForkTasksInputParamName("dynamicTasksInput");
        fanout.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        fanout.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

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
    private void createDynamicForkJoinWorkflowDefsLegacy() {

        WorkflowDef def = new WorkflowDef();
        def.setName(DYNAMIC_FORK_JOIN_WF_LEGACY);
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("dt1");

        WorkflowTask fanout = new WorkflowTask();
        fanout.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        fanout.setTaskReferenceName("dynamicfanouttask");
        fanout.setDynamicForkJoinTasksParam("dynamicTasks");
        fanout.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        fanout.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamicfanouttask_join");

        def.getTasks().add(wft1);
        def.getTasks().add(fanout);
        def.getTasks().add(join);

        metadataMapperService.populateTaskDefinitions(def);

        metadataService.updateWorkflowDef(def);

    }

    private void createConditionalWF() {

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "workflow.input.param1");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        Map<String, Object> ip3 = new HashMap<>();
        ip2.put("tp3", "workflow.input.param2");
        wft3.setInputParameters(ip3);
        wft3.setTaskReferenceName("t3");

        WorkflowDef def2 = new WorkflowDef();
        def2.setName(COND_TASK_WF);
        def2.setDescription(COND_TASK_WF);
        def2.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask c2 = new WorkflowTask();
        c2.setType(TaskType.DECISION.name());
        c2.setCaseValueParam("case");
        c2.setName("conditional2");
        c2.setTaskReferenceName("conditional2");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("one", Arrays.asList(wft1, wft3));
        dc.put("two", Arrays.asList(wft2));
        c2.setDecisionCases(dc);
        c2.getInputParameters().put("case", "workflow.input.param2");


        WorkflowTask condition = new WorkflowTask();
        condition.setType(TaskType.DECISION.name());
        condition.setCaseValueParam("case");
        condition.setName("conditional");
        condition.setTaskReferenceName("conditional");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("nested", Arrays.asList(c2));
        decisionCases.put("three", Arrays.asList(wft3));
        condition.setDecisionCases(decisionCases);
        condition.getInputParameters().put("case", "workflow.input.param1");
        condition.getDefaultCase().add(wft2);
        def2.getTasks().add(condition);

        WorkflowTask notifyTask = new WorkflowTask();
        notifyTask.setName("junit_task_4");
        notifyTask.setTaskReferenceName("junit_task_4");

        WorkflowTask finalTask = new WorkflowTask();
        finalTask.setName("finalcondition");
        finalTask.setTaskReferenceName("tf");
        finalTask.setType(TaskType.DECISION.name());
        finalTask.setCaseValueParam("finalCase");
        Map<String, Object> fi = new HashMap<>();
        fi.put("finalCase", "workflow.input.finalCase");
        finalTask.setInputParameters(fi);
        finalTask.getDecisionCases().put("notify", Arrays.asList(notifyTask));

        def2.getTasks().add(finalTask);
        metadataService.updateWorkflowDef(def2);

    }


    @Test
    public void testDefDAO() {
        List<TaskDef> taskDefs = metadataService.getTaskDefs();
        assertNotNull(taskDefs);
        assertTrue(!taskDefs.isEmpty());
    }

    @Test
    public void testSimpleWorkflowFailureWithTerminalError() {

        clearWorkflows();

        TaskDef taskDef = notFoundSafeGetTaskDef("junit_task_1");
        taskDef.setRetryCount(1);
        metadataService.updateTaskDef(taskDef);

        WorkflowDef found = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        assertNotNull(found);
        Map<String, Object> outputParameters = found.getOutputParameters();
        outputParameters.put("validationErrors", "${t1.output.ErrorMessage}");
        metadataService.updateWorkflowDef(found);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String workflowInstanceId = startOrLoadWorkflowExecution("simpleWorkflowFailureWithTerminalError", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        logger.info("testSimpleWorkflow.wfid= {}", workflowInstanceId);
        assertNotNull(workflowInstanceId);

        Workflow es = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(es);
        assertEquals(es.getReasonForIncompletion(), RUNNING, es.getStatus());

        es = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        assertEquals(1, es.getTasks().size());        //The very first task is the one that should be scheduled.

        boolean failed = false;
        try {
            workflowExecutor.rewind(workflowInstanceId);
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

        es = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        TaskDef junit_task_1 = notFoundSafeGetTaskDef("junit_task_1");
        Task t1 = es.getTaskByRefName("t1");
        assertNotNull(es);
        assertEquals(WorkflowStatus.FAILED, es.getStatus());
        assertEquals("NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down", es.getReasonForIncompletion());
        assertEquals(1, junit_task_1.getRetryCount()); //Configured retries at the task definition level
        assertEquals(0, t1.getRetryCount()); //Actual retries done on the task
        assertEquals(true, es.getOutput().containsKey("o1"));
        assertEquals("p1 value", es.getOutput().get("o1"));
        assertEquals(es.getOutput().get("validationErrors").toString(), "There was a terminal error");

        outputParameters.remove("validationErrors");
        metadataService.updateWorkflowDef(found);
    }

    @Test
    public void testSimpleWorkflow() {

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
        assertEquals(workflow.getReasonForIncompletion(), RUNNING, workflow.getStatus());

        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());        //The very first task is the one that should be scheduled.

        boolean failed = false;
        try {
            workflowExecutor.rewind(workflowInstanceId);
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
    public void testSimpleWorkflowWithResponseTimeout() throws Exception {

        createWFWithResponseTimeout();

        String correlationId = "unit_test_1";
        Map<String, Object> workflowInput = new HashMap<String, Object>();
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
        taskAgain.setCallbackAfterSeconds(20);
        workflowExecutionService.updateTask(taskAgain);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals(IN_PROGRESS, workflow.getTasks().get(1).getStatus());

        // wait for callback after seconds which is longer than response timeout seconds and then call decide
        Thread.sleep(20000);
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
    public void testWorkflowRerunWithSubWorkflows() {
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
                subWorkflowId = task.getOutputData().get(SubWorkflow.SUB_WORKFLOW_ID).toString();
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
        rerunWorkflowRequest.setReRunFromTaskId(subWorkflowTask1.getTaskId());

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

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
    }

    @Test
    public void testSimpleWorkflowWithTaskSpecificDomain() {

        long startTimeTimestamp = System.currentTimeMillis();

        clearWorkflows();
        createWorkflowDefForDomain();

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

        workflow = workflowExecutionService.getExecutionStatus(workflowId, false);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

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
    public void testSimpleWorkflowWithAllTaskInOneDomain() {

        clearWorkflows();
        createWorkflowDefForDomain();

        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2_SW, 1);

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
    public void clearWorkflows() {
        List<String> workflows = metadataService.getWorkflowDefs().stream()
                .map(WorkflowDef::getName)
                .collect(Collectors.toList());
        for (String wfName : workflows) {
            List<String> running = workflowExecutionService.getRunningWorkflows(wfName);
            for (String wfid : running) {
                workflowExecutor.terminateWorkflow(wfid, "cleanup");
            }
        }
        queueDAO.queuesDetail().keySet().forEach(queueDAO::flush);
    }

    @Test
    public void testLongRunning() {

        clearWorkflows();

        metadataService.getWorkflowDef(LONG_RUNNING, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<String, Object>();
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
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Arrays.asList("junit_task_1")).get("junit_task_1"));

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

        clearWorkflows();

        metadataService.getWorkflowDef(LONG_RUNNING, 1);

        String correlationId = "unit_test_1";
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LONG_RUNNING, 1, correlationId, input, null, null);
        System.out.println("testLongRunning.wfid=" + wfid);
        assertNotNull(wfid);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());


        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Arrays.asList("junit_task_1")).get("junit_task_1"));
        ///

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        String param1 = (String) task.getInputData().get("p1");
        String param2 = (String) task.getInputData().get("p2");

        assertNotNull(param1);
        assertNotNull(param2);
        assertEquals("p1 value", param1);
        assertEquals("p2 value", param2);


        String task1Op = "task1.In.Progress";
        task.getOutputData().put("op", task1Op);
        task.setStatus(IN_PROGRESS);
        task.setCallbackAfterSeconds(3600);
        workflowExecutionService.updateTask(task);
        String taskId = task.getTaskId();

        // Check the queue
        assertEquals(Integer.valueOf(1), workflowExecutionService.getTaskQueueSizes(Arrays.asList("junit_task_1")).get("junit_task_1"));
        ///


        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        // Polling for next task should not return anything
        Task task2 = workflowExecutionService.poll("junit_task_2", "task2.junit.worker");
        assertNull(task2);

        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNull(task);

        //Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        // Reset
        workflowExecutor.resetCallbacksForInProgressTasks(wfid);


        // Now Polling for the first task should return the same task as before
        task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));
        assertEquals(task.getTaskId(), taskId);
        assertEquals(task.getCallbackAfterSeconds(), 0);

        task1Op = "task1.Done";
        List<Task> tasks = workflowExecutionService.getTasks(task.getTaskType(), null, 1);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals(wfid, task.getWorkflowInstanceId());
        task = tasks.get(0);
        task.getOutputData().put("op", task1Op);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

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
        tasks = es.getTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());


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

            List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
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
    public void testCaseStatements() {
        createConditionalWF();

        String correlationId = "testCaseStatements: " + System.currentTimeMillis();
        Map<String, Object> input = new HashMap<String, Object>();
        String wfid;
        String[] sequence;


        //default case
        input.put("param1", "xxx");
        input.put("param2", "two");
        wfid = startOrLoadWorkflowExecution(COND_TASK_WF, 1, correlationId, input, null, null);
        System.out.println("testCaseStatements.wfid=" + wfid);
        assertNotNull(wfid);
        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        Task task = workflowExecutionService.poll("junit_task_2", "junit");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        assertEquals(3, es.getTasks().size());

        ///


        //nested - one
        input.put("param1", "nested");
        input.put("param2", "one");
        wfid = startOrLoadWorkflowExecution(COND_TASK_WF + 2, COND_TASK_WF, 1, correlationId, input, null, null);
        System.out.println("testCaseStatements.wfid=" + wfid);
        assertNotNull(wfid);
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        sequence = new String[]{"junit_task_1", "junit_task_3"};

        validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(), SystemTaskType.DECISION.name(), "junit_task_1", "junit_task_3", SystemTaskType.DECISION.name()}, 5);
        //

        //nested - two
        input.put("param1", "nested");
        input.put("param2", "two");
        wfid = startOrLoadWorkflowExecution(COND_TASK_WF + 3, COND_TASK_WF, 1, correlationId, input, null, null);
        System.out.println("testCaseStatements.wfid=" + wfid);
        assertNotNull(wfid);
        sequence = new String[]{"junit_task_2"};
        validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(), SystemTaskType.DECISION.name(), "junit_task_2", SystemTaskType.DECISION.name()}, 4);
        //

        //three
        input.put("param1", "three");
        input.put("param2", "two");
        input.put("finalCase", "notify");
        wfid = startOrLoadWorkflowExecution(COND_TASK_WF + 4, COND_TASK_WF, 1, correlationId, input, null, null);
        System.out.println("testCaseStatements.wfid=" + wfid);
        assertNotNull(wfid);
        sequence = new String[]{"junit_task_3", "junit_task_4"};
        validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(), "junit_task_3", SystemTaskType.DECISION.name(), "junit_task_4"}, 3);
        //

    }

    private void validate(String wfid, String[] sequence, String[] executedTasks, int expectedTotalTasks) {
        for (int i = 0; i < sequence.length; i++) {
            String t = sequence[i];
            Task task = getTask(t);
            if (task == null) {
                System.out.println("Missing task for " + t + ", below are the workflow tasks completed...");
                Workflow workflow = workflowExecutionService.getExecutionStatus(wfid, true);
                for (Task x : workflow.getTasks()) {
                    System.out.println(x.getTaskType() + "/" + x.getReferenceTaskName());
                }
            }
            assertNotNull("No task for " + t, task);
            assertEquals(wfid, task.getWorkflowInstanceId());
            task.setStatus(COMPLETED);
            workflowExecutionService.updateTask(task);

            Workflow workflow = workflowExecutionService.getExecutionStatus(wfid, true);
            assertNotNull(workflow);
            assertTrue(!workflow.getTasks().isEmpty());
            if (i < sequence.length - 1) {
                assertEquals(RUNNING, workflow.getStatus());
            } else {
                workflow = workflowExecutionService.getExecutionStatus(wfid, true);
                List<Task> workflowTasks = workflow.getTasks();
                assertEquals(workflowTasks.toString(), executedTasks.length, workflowTasks.size());
                for (int k = 0; k < executedTasks.length; k++) {
                    assertEquals("Tasks: " + workflowTasks.toString() + "\n", executedTasks[k], workflowTasks.get(k).getTaskType());
                }

                assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
            }
        }
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

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
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

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
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

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
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

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        input.put("param1", "p1 value");
        input.put("param2", "p2 value");
        String workflowId = startOrLoadWorkflowExecution("retry", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(workflowId);
        printTaskStatuses(workflowId, "initial");

        Task task = getTask("junit_task_1");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = getTask("junit_task_1");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        printTaskStatuses(workflowId, "before retry");

        workflowExecutor.retry(workflowId);

        printTaskStatuses(workflowId, "after retry");
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

        workflowExecutor.rewind(workflow.getWorkflowId());

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
        expectedException.expectMessage(String.format("is an instance of WorkflowDef: %s and version: %d and is non restartable", JUNIT_TEST_WF_NON_RESTARTABLE, 1));
        workflowExecutor.rewind(workflow.getWorkflowId());
    }


    @Test
    public void testRestart() {
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
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        Task task = getTask("junit_task_1");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.FAILED, es.getStatus());

        workflowExecutor.rewind(es.getWorkflowId());
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        task = getTask("junit_task_1");
        assertNotNull(task);
        assertEquals(wfid, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());

        task = getTask("junit_task_2");
        assertNotNull(task);
        assertEquals(wfid, task.getWorkflowInstanceId());
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
    }


    @Test
    public void testTimeout() throws Exception {

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
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        input.put("failureWfName", "FanInOutTest");
        String wfid = startOrLoadWorkflowExecution("timeout", LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        //Ensure that we have a workflow queued up for evaluation here...
        long size = queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE);
        assertEquals(1, size);

        // If we get the full workflow here then, last task should be completed and the next task should be scheduled
        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        assertEquals("fond: " + es.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 1, es.getTasks().size());

        Task task = workflowExecutionService.poll("junit_task_1", "task1.junit.worker");
        assertNotNull(task);
        assertEquals(wfid, task.getWorkflowInstanceId());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));


        //Ensure that we have a workflow queued up for evaluation here...
        size = queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE);
        assertEquals(1, size);


        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        workflowSweeper.sweep(Arrays.asList(wfid), workflowExecutor);
        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals("fond: " + es.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 2, es.getTasks().size());

        Task task1 = es.getTasks().get(0);
        assertEquals(TIMED_OUT, task1.getStatus());
        Task task2 = es.getTasks().get(1);
        assertEquals(SCHEDULED, task2.getStatus());

        task = workflowExecutionService.poll(task2.getTaskDefName(), "task1.junit.worker");
        assertNotNull(task);
        assertEquals(wfid, task.getWorkflowInstanceId());
        assertTrue(workflowExecutionService.ackTaskReceived(task.getTaskId()));

        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        workflowExecutor.decide(wfid);

        es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(2, es.getTasks().size());

        assertEquals(TIMED_OUT, es.getTasks().get(0).getStatus());
        assertEquals(TIMED_OUT, es.getTasks().get(1).getStatus());
        assertEquals(WorkflowStatus.TIMED_OUT, es.getStatus());

        assertEquals(1, queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE));

        taskDef.setTimeoutSeconds(0);
        taskDef.setRetryCount(RETRY_COUNT);
        metadataService.updateTaskDef(taskDef);
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


        metadataService.getWorkflowDef(TEST_WORKFLOW_NAME_3, 1);

        String correlationId = "unit_test_1" + UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(TEST_WORKFLOW_NAME_3, 1, correlationId, input, null, null);
        assertNotNull(wfid);

        // Now Skip the second task
        workflowExecutor.skipTaskFromWorkflow(wfid, "t2", null);

        Workflow es = workflowExecutionService.getExecutionStatus(wfid, true);
        assertNotNull(es);
        assertEquals(RUNNING, es.getStatus());
        // Check the tasks, at this time there should be 3 task
        assertEquals(2, es.getTasks().size());
        assertEquals(SCHEDULED, es.getTasks().get(0).getStatus());
        assertEquals(Status.SKIPPED, es.getTasks().get(1).getStatus());

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
        Map<String, Object> input = new HashMap<String, Object>();
        String inputParam1 = "p1 value";
        input.put("param1", inputParam1);
        input.put("param2", "p2 value");
        String wfid = startOrLoadWorkflowExecution(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input, null, null);

        assertNotNull(wfid);

        List<String> ids = workflowExecutionService.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
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

        Workflow es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());

        task = es.getTasks().stream().filter(t -> t.getTaskType().equals(TaskType.SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull("Output: " + task.getOutputData().toString() + ", status: " + task.getStatus(), task.getOutputData().get("subWorkflowId"));
        assertNotNull(task.getInputData());
        assertTrue(task.getInputData().containsKey("workflowInput"));
        assertEquals(42, ((Map<String, Object>)task.getInputData().get("workflowInput")).get("param2"));
        String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());
        assertEquals(wfId, es.getParentWorkflowId());
        assertEquals(RUNNING, es.getStatus());

        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        task = workflowExecutionService.poll("junit_task_2", "test");
        assertEquals(subWorkflowId, task.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        task.getOutputData().put("uuid", uuid);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
        assertNotNull(es.getOutput());
        assertTrue(es.getOutput().containsKey("o1"));
        assertTrue(es.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", es.getOutput().get("o1"));
        assertEquals(uuid, es.getOutput().get("o2"));

        task = workflowExecutionService.poll("junit_task_6", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
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
        String wfId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(wfId);

        Workflow es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());
        task = es.getTasks().stream().filter(t -> t.getTaskType().equals(TaskType.SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull(task.getOutputData().get("subWorkflowId"));
        String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());

        assertEquals(wfId, es.getParentWorkflowId());
        assertEquals(RUNNING, es.getStatus());

        task = workflowExecutionService.poll("junit_task_1", "test");
        assertNotNull(task);
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(es);
        assertEquals(WorkflowStatus.FAILED, es.getStatus());
        workflowExecutor.executeSystemTask(subworkflow, es.getParentWorkflowTaskId(), 1);
        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertEquals(WorkflowStatus.FAILED, es.getStatus());

        taskDef.setTimeoutSeconds(0);
        taskDef.setRetryCount(RETRY_COUNT);
        metadataService.updateTaskDef(taskDef);
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
        String wfId = startOrLoadWorkflowExecution(WF_WITH_SUB_WF, 1, "test", input, null, null);
        assertNotNull(wfId);

        Workflow es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);

        Task task = workflowExecutionService.poll("junit_task_5", "test");
        assertNotNull(task);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());
        task = es.getTasks().stream().filter(t -> t.getTaskType().equals(TaskType.SUB_WORKFLOW.name())).findAny().get();
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull(task.getOutputData().get("subWorkflowId"));
        String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(es);
        assertNotNull(es.getTasks());
        assertEquals(wfId, es.getParentWorkflowId());
        assertEquals(RUNNING, es.getStatus());

        workflowExecutor.terminateWorkflow(wfId, "fail");
        es = workflowExecutionService.getExecutionStatus(wfId, true);
        assertEquals(WorkflowStatus.TERMINATED, es.getStatus());

        es = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertEquals(WorkflowStatus.TERMINATED, es.getStatus());

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
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(2, workflow.getTasks().size());

        task = workflow.getTasks().stream().filter(t -> t.getTaskType().equals(TaskType.SUB_WORKFLOW.name())).findAny().orElse(null);
        assertNotNull(task);
        assertNotNull(task.getOutputData());
        assertNotNull("Output: " + task.getOutputData().toString() + ", status: " + task.getStatus(), task.getOutputData().get("subWorkflowId"));
        String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertNotNull(workflow.getTasks());
        assertEquals(workflowId, workflow.getParentWorkflowId());
        assertEquals(RUNNING, workflow.getStatus());

        // poll and fail the first task in sub-workflow
        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(FAILED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        workflow = workflowExecutionService.getExecutionStatus(workflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.FAILED, workflow.getStatus());

        // Retry the failed sub workflow
        workflowExecutor.retry(subWorkflowId);
        task = workflowExecutionService.poll("junit_task_1", "test");
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertEquals(RUNNING, workflow.getStatus());

        task = workflowExecutionService.poll("junit_task_2", "test");
        assertEquals(subWorkflowId, task.getWorkflowInstanceId());
        String uuid = UUID.randomUUID().toString();
        task.getOutputData().put("uuid", uuid);
        task.setStatus(COMPLETED);
        workflowExecutionService.updateTask(task);

        workflow = workflowExecutionService.getExecutionStatus(subWorkflowId, true);
        assertNotNull(workflow);
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
        assertNotNull(workflow.getOutput());
        assertTrue(workflow.getOutput().containsKey("o1"));
        assertTrue(workflow.getOutput().containsKey("o2"));
        assertEquals("sub workflow input param1", workflow.getOutput().get("o1"));
        assertEquals(uuid, workflow.getOutput().get("o2"));

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
        assertTrue(!eventTask.getOutputData().isEmpty());
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
    public void testTaskWithCallbackAfterSecondsInWorkflow() {
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
        task.setCallbackAfterSeconds(5L);
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

        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

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

        String workflowInputPath = "workflow/input";
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
        String taskOutputPath = "task/output";
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
        assertEquals("task/input", workflow.getTasks().get(1).getExternalInputPayloadStoragePath());

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
        assertEquals("task/input", workflow.getTasks().get(1).getExternalInputPayloadStoragePath());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals("workflow/output", workflow.getExternalOutputPayloadStoragePath());
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

        String workflowInputPath = "workflow/input";
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
        String taskOutputPath = "task/output";
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
        assertEquals("task/input", workflow.getTasks().get(1).getExternalInputPayloadStoragePath());
        assertEquals("task/input", workflow.getTasks().get(2).getExternalInputPayloadStoragePath());
        assertTrue(workflow.getOutput().isEmpty());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
        assertEquals("workflow/output", workflow.getExternalOutputPayloadStoragePath());
    }

    //@Test
    public void testRateLimiting() {

        TaskDef td = new TaskDef();
        td.setName("eventX1");
        td.setTimeoutSeconds(1);
        td.setConcurrentExecLimit(1);

        metadataService.registerTaskDef(Arrays.asList(td));

        WorkflowDef def = new WorkflowDef();
        def.setName("test_rate_limit");
        def.setSchemaVersion(2);

        WorkflowTask event = new WorkflowTask();
        event.setType("USER_TASK");
        event.setName("eventX1");
        event.setTaskReferenceName("event0");
        event.setSink("conductor");

        def.getTasks().add(event);
        metadataService.registerWorkflowDef(def);

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            queueDAO.processUnacks("USER_TASK");
        }, 2, 2, TimeUnit.SECONDS);

        String[] ids = new String[100];
        ExecutorService es = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            final int index = i;
            es.submit(() -> {
                try {
                    String id = startOrLoadWorkflowExecution(def.getName(), def.getVersion(), "", new HashMap<>(), null, null);
                    ids[index] = id;
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });
        }
        Uninterruptibles.sleepUninterruptibly(20, TimeUnit.SECONDS);
        for (int i = 0; i < 10; i++) {
            String id = ids[i];
            Workflow workflow = workflowExecutor.getWorkflow(id, true);
            assertNotNull(workflow);
            assertEquals(1, workflow.getTasks().size());

            Task eventTask = workflow.getTasks().get(0);
            assertEquals(COMPLETED, eventTask.getStatus());
            assertEquals("tasks:" + workflow.getTasks(), WorkflowStatus.COMPLETED, workflow.getStatus());
            assertTrue(!eventTask.getOutputData().isEmpty());
            assertNotNull(eventTask.getOutputData().get("event_produced"));
        }
    }

    private void createSubWorkflow() {

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_5");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("a1");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("subWorkflowTask");
        wft2.setType(TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams swp = new SubWorkflowParams();
        swp.setName(LINEAR_WORKFLOW_T1_T2);
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

    private void createWorkflowDefForDomain() {
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
        subWorkflow.setType(TaskType.SUB_WORKFLOW.name());
        SubWorkflowParams sw = new SubWorkflowParams();
        sw.setName(LINEAR_WORKFLOW_T1_T2);
        subWorkflow.setSubWorkflowParam(sw);
        subWorkflow.setTaskReferenceName("sw1");

        wftasks.add(wft1);
        wftasks.add(subWorkflow);
        defSW.setTasks(wftasks);

        try {
            metadataService.updateWorkflowDef(defSW);
        } catch (Exception e) {
        }
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

    private String runWorkflowWithSubworkflow() {
        clearWorkflows();
        createWorkflowDefForDomain();

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

        // Get the sub workflow id
        String subWorkflowId = null;
        for (Task t : workflow.getTasks()) {
            if (t.getTaskType().equalsIgnoreCase("SUB_WORKFLOW")) {
                subWorkflowId = t.getOutputData().get("subWorkflowId").toString();
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
