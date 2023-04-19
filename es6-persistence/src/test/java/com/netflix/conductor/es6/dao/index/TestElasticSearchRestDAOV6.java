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
package com.netflix.conductor.es6.dao.index;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.Test;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.es6.utils.TestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.*;

public class TestElasticSearchRestDAOV6 extends ElasticSearchRestDaoBaseTest {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMWW");

    private static final String INDEX_PREFIX = "conductor";
    private static final String WORKFLOW_DOC_TYPE = "workflow";
    private static final String TASK_DOC_TYPE = "task";
    private static final String MSG_DOC_TYPE = "message";
    private static final String EVENT_DOC_TYPE = "event";
    private static final String LOG_INDEX_PREFIX = "task_log";

    private boolean indexExists(final String index) throws IOException {
        return indexDAO.doesResourceExist("/" + index);
    }

    private boolean doesMappingExist(final String index, final String mappingName)
            throws IOException {
        return indexDAO.doesResourceExist("/" + index + "/_mapping/" + mappingName);
    }

    @Test
    public void assertInitialSetup() throws IOException {
        SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));

        String workflowIndex = INDEX_PREFIX + "_" + WORKFLOW_DOC_TYPE;
        String taskIndex = INDEX_PREFIX + "_" + TASK_DOC_TYPE;

        String taskLogIndex =
                INDEX_PREFIX + "_" + LOG_INDEX_PREFIX + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        String messageIndex =
                INDEX_PREFIX + "_" + MSG_DOC_TYPE + "_" + SIMPLE_DATE_FORMAT.format(new Date());
        String eventIndex =
                INDEX_PREFIX + "_" + EVENT_DOC_TYPE + "_" + SIMPLE_DATE_FORMAT.format(new Date());

        assertTrue("Index 'conductor_workflow' should exist", indexExists("conductor_workflow"));
        assertTrue("Index 'conductor_task' should exist", indexExists("conductor_task"));

        assertTrue("Index '" + taskLogIndex + "' should exist", indexExists(taskLogIndex));
        assertTrue("Index '" + messageIndex + "' should exist", indexExists(messageIndex));
        assertTrue("Index '" + eventIndex + "' should exist", indexExists(eventIndex));

        assertTrue(
                "Mapping 'workflow' for index 'conductor' should exist",
                doesMappingExist(workflowIndex, WORKFLOW_DOC_TYPE));
        assertTrue(
                "Mapping 'task' for index 'conductor' should exist",
                doesMappingExist(taskIndex, TASK_DOC_TYPE));
    }

    @Test
    public void shouldIndexWorkflow() throws JsonProcessingException {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        assertWorkflowSummary(workflowSummary.getWorkflowId(), workflowSummary);
    }

    @Test
    public void shouldIndexWorkflowAsync() throws Exception {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.asyncIndexWorkflow(workflowSummary).get();

        assertWorkflowSummary(workflowSummary.getWorkflowId(), workflowSummary);
    }

    @Test
    public void shouldRemoveWorkflow() {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        // wait for workflow to be indexed
        List<String> workflows =
                tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 1);
        assertEquals(1, workflows.size());

        indexDAO.removeWorkflow(workflowSummary.getWorkflowId());

        workflows = tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 0);

        assertTrue("Workflow was not removed.", workflows.isEmpty());
    }

    @Test
    public void shouldAsyncRemoveWorkflow() throws Exception {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        // wait for workflow to be indexed
        List<String> workflows =
                tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 1);
        assertEquals(1, workflows.size());

        indexDAO.asyncRemoveWorkflow(workflowSummary.getWorkflowId()).get();

        workflows = tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 0);

        assertTrue("Workflow was not removed.", workflows.isEmpty());
    }

    @Test
    public void shouldUpdateWorkflow() throws JsonProcessingException {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        indexDAO.updateWorkflow(
                workflowSummary.getWorkflowId(),
                new String[] {"status"},
                new Object[] {WorkflowStatus.COMPLETED});

        workflowSummary.setStatus(WorkflowStatus.COMPLETED);
        assertWorkflowSummary(workflowSummary.getWorkflowId(), workflowSummary);
    }

    @Test
    public void shouldAsyncUpdateWorkflow() throws Exception {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        indexDAO.asyncUpdateWorkflow(
                        workflowSummary.getWorkflowId(),
                        new String[] {"status"},
                        new Object[] {WorkflowStatus.FAILED})
                .get();

        workflowSummary.setStatus(WorkflowStatus.FAILED);
        assertWorkflowSummary(workflowSummary.getWorkflowId(), workflowSummary);
    }

    @Test
    public void shouldIndexTask() {
        TaskSummary taskSummary = TestUtils.loadTaskSnapshot(objectMapper, "task_summary");
        indexDAO.indexTask(taskSummary);

        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary));

        assertEquals(taskSummary.getTaskId(), tasks.get(0));
    }

    @Test
    public void shouldIndexTaskAsync() throws Exception {
        TaskSummary taskSummary = TestUtils.loadTaskSnapshot(objectMapper, "task_summary");
        indexDAO.asyncIndexTask(taskSummary).get();

        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary));

        assertEquals(taskSummary.getTaskId(), tasks.get(0));
    }

    @Test
    public void shouldRemoveTask() {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        // wait for workflow to be indexed
        tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 1);

        TaskSummary taskSummary =
                TestUtils.loadTaskSnapshot(
                        objectMapper, "task_summary", workflowSummary.getWorkflowId());
        indexDAO.indexTask(taskSummary);

        // Wait for the task to be indexed
        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary), 1);

        indexDAO.removeTask(workflowSummary.getWorkflowId(), taskSummary.getTaskId());

        tasks = tryFindResults(() -> searchTasks(taskSummary), 0);

        assertTrue("Task was not removed.", tasks.isEmpty());
    }

    @Test
    public void shouldAsyncRemoveTask() throws Exception {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        // wait for workflow to be indexed
        tryFindResults(() -> searchWorkflows(workflowSummary.getWorkflowId()), 1);

        TaskSummary taskSummary =
                TestUtils.loadTaskSnapshot(
                        objectMapper, "task_summary", workflowSummary.getWorkflowId());
        indexDAO.indexTask(taskSummary);

        // Wait for the task to be indexed
        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary), 1);

        indexDAO.asyncRemoveTask(workflowSummary.getWorkflowId(), taskSummary.getTaskId()).get();

        tasks = tryFindResults(() -> searchTasks(taskSummary), 0);

        assertTrue("Task was not removed.", tasks.isEmpty());
    }

    @Test
    public void shouldNotRemoveTaskWhenNotAssociatedWithWorkflow() {
        TaskSummary taskSummary = TestUtils.loadTaskSnapshot(objectMapper, "task_summary");
        indexDAO.indexTask(taskSummary);

        // Wait for the task to be indexed
        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary), 1);

        indexDAO.removeTask("InvalidWorkflow", taskSummary.getTaskId());

        tasks = tryFindResults(() -> searchTasks(taskSummary), 0);

        assertFalse("Task was removed.", tasks.isEmpty());
    }

    @Test
    public void shouldNotAsyncRemoveTaskWhenNotAssociatedWithWorkflow() throws Exception {
        TaskSummary taskSummary = TestUtils.loadTaskSnapshot(objectMapper, "task_summary");
        indexDAO.indexTask(taskSummary);

        // Wait for the task to be indexed
        List<String> tasks = tryFindResults(() -> searchTasks(taskSummary), 1);

        indexDAO.asyncRemoveTask("InvalidWorkflow", taskSummary.getTaskId()).get();

        tasks = tryFindResults(() -> searchTasks(taskSummary), 0);

        assertFalse("Task was removed.", tasks.isEmpty());
    }

    @Test
    public void shouldAddTaskExecutionLogs() {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = uuid();
        logs.add(createLog(taskId, "log1"));
        logs.add(createLog(taskId, "log2"));
        logs.add(createLog(taskId, "log3"));

        indexDAO.addTaskExecutionLogs(logs);

        List<TaskExecLog> indexedLogs =
                tryFindResults(() -> indexDAO.getTaskExecutionLogs(taskId), 3);

        assertEquals(3, indexedLogs.size());

        assertTrue("Not all logs was indexed", indexedLogs.containsAll(logs));
    }

    @Test
    public void shouldAddTaskExecutionLogsAsync() throws Exception {
        List<TaskExecLog> logs = new ArrayList<>();
        String taskId = uuid();
        logs.add(createLog(taskId, "log1"));
        logs.add(createLog(taskId, "log2"));
        logs.add(createLog(taskId, "log3"));

        indexDAO.asyncAddTaskExecutionLogs(logs).get();

        List<TaskExecLog> indexedLogs =
                tryFindResults(() -> indexDAO.getTaskExecutionLogs(taskId), 3);

        assertEquals(3, indexedLogs.size());

        assertTrue("Not all logs was indexed", indexedLogs.containsAll(logs));
    }

    @Test
    public void shouldAddMessage() {
        String queue = "queue";
        Message message1 = new Message(uuid(), "payload1", null);
        Message message2 = new Message(uuid(), "payload2", null);

        indexDAO.addMessage(queue, message1);
        indexDAO.addMessage(queue, message2);

        List<Message> indexedMessages = tryFindResults(() -> indexDAO.getMessages(queue), 2);

        assertEquals(2, indexedMessages.size());

        assertTrue(
                "Not all messages was indexed",
                indexedMessages.containsAll(Arrays.asList(message1, message2)));
    }

    @Test
    public void shouldAddEventExecution() {
        String event = "event";
        EventExecution execution1 = createEventExecution(event);
        EventExecution execution2 = createEventExecution(event);

        indexDAO.addEventExecution(execution1);
        indexDAO.addEventExecution(execution2);

        List<EventExecution> indexedExecutions =
                tryFindResults(() -> indexDAO.getEventExecutions(event), 2);

        assertEquals(2, indexedExecutions.size());

        assertTrue(
                "Not all event executions was indexed",
                indexedExecutions.containsAll(Arrays.asList(execution1, execution2)));
    }

    @Test
    public void shouldAsyncAddEventExecution() throws Exception {
        String event = "event2";
        EventExecution execution1 = createEventExecution(event);
        EventExecution execution2 = createEventExecution(event);

        indexDAO.asyncAddEventExecution(execution1).get();
        indexDAO.asyncAddEventExecution(execution2).get();

        List<EventExecution> indexedExecutions =
                tryFindResults(() -> indexDAO.getEventExecutions(event), 2);

        assertEquals(2, indexedExecutions.size());

        assertTrue(
                "Not all event executions was indexed",
                indexedExecutions.containsAll(Arrays.asList(execution1, execution2)));
    }

    @Test
    public void shouldAddIndexPrefixToIndexTemplate() throws Exception {
        String json = TestUtils.loadJsonResource("expected_template_task_log");

        String content = indexDAO.loadTypeMappingSource("/template_task_log.json");

        assertEquals(json, content);
    }

    @Test
    public void shouldCountWorkflows() {
        int counts = 1100;
        for (int i = 0; i < counts; i++) {
            WorkflowSummary workflowSummary =
                    TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
            indexDAO.indexWorkflow(workflowSummary);
        }

        // wait for workflow to be indexed
        long result = tryGetCount(() -> getWorkflowCount("template_workflow", "RUNNING"), counts);
        assertEquals(counts, result);
    }

    @Test
    public void shouldFindWorkflow() {
        WorkflowSummary workflowSummary =
                TestUtils.loadWorkflowSnapshot(objectMapper, "workflow_summary");
        indexDAO.indexWorkflow(workflowSummary);

        // wait for workflow to be indexed
        List<WorkflowSummary> workflows =
                tryFindResults(() -> searchWorkflowSummary(workflowSummary.getWorkflowId()), 1);
        assertEquals(1, workflows.size());
        assertEquals(workflowSummary, workflows.get(0));
    }

    @Test
    public void shouldFindTask() {
        TaskSummary taskSummary = TestUtils.loadTaskSnapshot(objectMapper, "task_summary");
        indexDAO.indexTask(taskSummary);

        List<TaskSummary> tasks = tryFindResults(() -> searchTaskSummary(taskSummary));
        assertEquals(1, tasks.size());
        assertEquals(taskSummary, tasks.get(0));
    }

    private long tryGetCount(Supplier<Long> countFunction, int resultsCount) {
        long result = 0;
        for (int i = 0; i < 20; i++) {
            result = countFunction.get();
            if (result == resultsCount) {
                return result;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return result;
    }

    // Get total workflow counts given the name and status
    private long getWorkflowCount(String workflowName, String status) {
        return indexDAO.getWorkflowCount(
                "status=\"" + status + "\" AND workflowType=\"" + workflowName + "\"", "*");
    }

    private void assertWorkflowSummary(String workflowId, WorkflowSummary summary)
            throws JsonProcessingException {
        assertEquals(summary.getWorkflowType(), indexDAO.get(workflowId, "workflowType"));
        assertEquals(String.valueOf(summary.getVersion()), indexDAO.get(workflowId, "version"));
        assertEquals(summary.getWorkflowId(), indexDAO.get(workflowId, "workflowId"));
        assertEquals(summary.getCorrelationId(), indexDAO.get(workflowId, "correlationId"));
        assertEquals(summary.getStartTime(), indexDAO.get(workflowId, "startTime"));
        assertEquals(summary.getUpdateTime(), indexDAO.get(workflowId, "updateTime"));
        assertEquals(summary.getEndTime(), indexDAO.get(workflowId, "endTime"));
        assertEquals(summary.getStatus().name(), indexDAO.get(workflowId, "status"));
        assertEquals(summary.getInput(), indexDAO.get(workflowId, "input"));
        assertEquals(summary.getOutput(), indexDAO.get(workflowId, "output"));
        assertEquals(
                summary.getReasonForIncompletion(),
                indexDAO.get(workflowId, "reasonForIncompletion"));
        assertEquals(
                String.valueOf(summary.getExecutionTime()),
                indexDAO.get(workflowId, "executionTime"));
        assertEquals(summary.getEvent(), indexDAO.get(workflowId, "event"));
        assertEquals(
                summary.getFailedReferenceTaskNames(),
                indexDAO.get(workflowId, "failedReferenceTaskNames"));
        assertEquals(
                summary.getFailedTaskNames(),
                objectMapper.readValue(indexDAO.get(workflowId, "failedTaskNames"), Set.class));
    }

    private <T> List<T> tryFindResults(Supplier<List<T>> searchFunction) {
        return tryFindResults(searchFunction, 1);
    }

    private <T> List<T> tryFindResults(Supplier<List<T>> searchFunction, int resultsCount) {
        List<T> result = Collections.emptyList();
        for (int i = 0; i < 20; i++) {
            result = searchFunction.get();
            if (result.size() == resultsCount) {
                return result;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return result;
    }

    private List<String> searchWorkflows(String workflowId) {
        return indexDAO.searchWorkflows(
                        "", "workflowId:\"" + workflowId + "\"", 0, 100, Collections.emptyList())
                .getResults();
    }

    private List<WorkflowSummary> searchWorkflowSummary(String workflowId) {
        return indexDAO.searchWorkflowSummary(
                        "", "workflowId:\"" + workflowId + "\"", 0, 100, Collections.emptyList())
                .getResults();
    }

    private List<String> searchWorkflows(String workflowName, String status) {
        List<String> sortOptions = new ArrayList<>();
        sortOptions.add("startTime:DESC");
        return indexDAO.searchWorkflows(
                        "status=\"" + status + "\" AND workflowType=\"" + workflowName + "\"",
                        "*",
                        0,
                        1000,
                        sortOptions)
                .getResults();
    }

    private List<String> searchTasks(TaskSummary taskSummary) {
        return indexDAO.searchTasks(
                        "",
                        "workflowId:\"" + taskSummary.getWorkflowId() + "\"",
                        0,
                        100,
                        Collections.emptyList())
                .getResults();
    }

    private List<TaskSummary> searchTaskSummary(TaskSummary taskSummary) {
        return indexDAO.searchTaskSummary(
                        "",
                        "workflowId:\"" + taskSummary.getWorkflowId() + "\"",
                        0,
                        100,
                        Collections.emptyList())
                .getResults();
    }

    private TaskExecLog createLog(String taskId, String log) {
        TaskExecLog taskExecLog = new TaskExecLog(log);
        taskExecLog.setTaskId(taskId);
        return taskExecLog;
    }

    private EventExecution createEventExecution(String event) {
        EventExecution execution = new EventExecution(uuid(), uuid());
        execution.setName("name");
        execution.setEvent(event);
        execution.setCreated(System.currentTimeMillis());
        execution.setStatus(EventExecution.Status.COMPLETED);
        execution.setAction(EventHandler.Action.Type.start_workflow);
        execution.setOutput(ImmutableMap.of("a", 1, "b", 2, "c", 3));
        return execution;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }
}
