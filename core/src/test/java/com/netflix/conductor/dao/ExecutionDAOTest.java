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
package com.netflix.conductor.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;

public abstract class ExecutionDAOTest {

    protected abstract ExecutionDAO getExecutionDAO();

    protected ConcurrentExecutionLimitDAO getConcurrentExecutionLimitDAO() {
        return (ConcurrentExecutionLimitDAO) getExecutionDAO();
    }

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testTaskExceedsLimit() {
        TaskDef taskDefinition = new TaskDef();
        taskDefinition.setName("task1");
        taskDefinition.setConcurrentExecLimit(1);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("task1");
        workflowTask.setTaskDefinition(taskDefinition);
        workflowTask.setTaskDefinition(taskDefinition);

        List<TaskModel> tasks = new LinkedList<>();
        for (int i = 0; i < 15; i++) {
            TaskModel task = new TaskModel();
            task.setScheduledTime(1L);
            task.setSeq(i + 1);
            task.setTaskId("t_" + i);
            task.setWorkflowInstanceId("workflow_" + i);
            task.setReferenceTaskName("task1");
            task.setTaskDefName("task1");
            tasks.add(task);
            task.setStatus(TaskModel.Status.SCHEDULED);
            task.setWorkflowTask(workflowTask);
        }

        getExecutionDAO().createTasks(tasks);
        assertFalse(getConcurrentExecutionLimitDAO().exceedsLimit(tasks.get(0)));
        tasks.get(0).setStatus(TaskModel.Status.IN_PROGRESS);
        getExecutionDAO().updateTask(tasks.get(0));

        for (TaskModel task : tasks) {
            assertTrue(getConcurrentExecutionLimitDAO().exceedsLimit(task));
        }
    }

    @Test
    public void testCreateTaskException() {
        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName("task1");

        expectedException.expect(ApplicationException.class);
        expectedException.expectMessage("Workflow instance id cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));

        task.setWorkflowInstanceId(UUID.randomUUID().toString());
        expectedException.expect(ApplicationException.class);
        expectedException.expectMessage("Task reference name cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));
    }

    @Test
    public void testCreateTaskException2() {
        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName("task1");
        task.setWorkflowInstanceId(UUID.randomUUID().toString());

        expectedException.expect(ApplicationException.class);
        expectedException.expectMessage("Task reference name cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));
    }

    @Test
    public void testTaskCreateDups() {
        List<TaskModel> tasks = new LinkedList<>();
        String workflowId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            TaskModel task = new TaskModel();
            task.setScheduledTime(1L);
            task.setSeq(i + 1);
            task.setTaskId(workflowId + "_t" + i);
            task.setReferenceTaskName("t" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId(workflowId);
            task.setTaskDefName("task" + i);
            task.setStatus(TaskModel.Status.IN_PROGRESS);
            tasks.add(task);
        }

        // Let's insert a retried task
        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(workflowId + "_t" + 2);
        task.setReferenceTaskName("t" + 2);
        task.setRetryCount(1);
        task.setWorkflowInstanceId(workflowId);
        task.setTaskDefName("task" + 2);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        tasks.add(task);

        // Duplicate task!
        task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(workflowId + "_t" + 1);
        task.setReferenceTaskName("t" + 1);
        task.setRetryCount(0);
        task.setWorkflowInstanceId(workflowId);
        task.setTaskDefName("task" + 1);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        tasks.add(task);

        List<TaskModel> created = getExecutionDAO().createTasks(tasks);
        assertEquals(tasks.size() - 1, created.size()); // 1 less

        Set<String> srcIds =
                tasks.stream()
                        .map(t -> t.getReferenceTaskName() + "." + t.getRetryCount())
                        .collect(Collectors.toSet());
        Set<String> createdIds =
                created.stream()
                        .map(t -> t.getReferenceTaskName() + "." + t.getRetryCount())
                        .collect(Collectors.toSet());

        assertEquals(srcIds, createdIds);

        List<TaskModel> pending = getExecutionDAO().getPendingTasksByWorkflow("task0", workflowId);
        assertNotNull(pending);
        assertEquals(1, pending.size());
        assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), pending.get(0)));

        List<TaskModel> found = getExecutionDAO().getTasks(tasks.get(0).getTaskDefName(), null, 1);
        assertNotNull(found);
        assertEquals(1, found.size());
        assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), found.get(0)));
    }

    @Test
    public void testTaskOps() {
        List<TaskModel> tasks = new LinkedList<>();
        String workflowId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            TaskModel task = new TaskModel();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId(workflowId + "_t" + i);
            task.setReferenceTaskName("testTaskOps" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId(workflowId);
            task.setTaskDefName("testTaskOps" + i);
            task.setStatus(TaskModel.Status.IN_PROGRESS);
            tasks.add(task);
        }

        for (int i = 0; i < 3; i++) {
            TaskModel task = new TaskModel();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId("x" + workflowId + "_t" + i);
            task.setReferenceTaskName("testTaskOps" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId("x" + workflowId);
            task.setTaskDefName("testTaskOps" + i);
            task.setStatus(TaskModel.Status.IN_PROGRESS);
            getExecutionDAO().createTasks(Collections.singletonList(task));
        }

        List<TaskModel> created = getExecutionDAO().createTasks(tasks);
        assertEquals(tasks.size(), created.size());

        List<TaskModel> pending =
                getExecutionDAO().getPendingTasksForTaskType(tasks.get(0).getTaskDefName());
        assertNotNull(pending);
        assertEquals(2, pending.size());
        // Pending list can come in any order.  finding the one we are looking for and then
        // comparing
        TaskModel matching =
                pending.stream()
                        .filter(task -> task.getTaskId().equals(tasks.get(0).getTaskId()))
                        .findAny()
                        .get();
        assertTrue(EqualsBuilder.reflectionEquals(matching, tasks.get(0)));

        for (int i = 0; i < 3; i++) {
            TaskModel found = getExecutionDAO().getTask(workflowId + "_t" + i);
            assertNotNull(found);
            found.getOutputData().put("updated", true);
            found.setStatus(TaskModel.Status.COMPLETED);
            getExecutionDAO().updateTask(found);
        }

        List<String> taskIds =
                tasks.stream().map(TaskModel::getTaskId).collect(Collectors.toList());
        List<TaskModel> found = getExecutionDAO().getTasks(taskIds);
        assertEquals(taskIds.size(), found.size());
        found.forEach(
                task -> {
                    assertTrue(task.getOutputData().containsKey("updated"));
                    assertEquals(true, task.getOutputData().get("updated"));
                    boolean removed = getExecutionDAO().removeTask(task.getTaskId());
                    assertTrue(removed);
                });

        found = getExecutionDAO().getTasks(taskIds);
        assertTrue(found.isEmpty());
    }

    @Test
    public void testPending() {
        WorkflowDef def = new WorkflowDef();
        def.setName("pending_count_test");

        WorkflowModel workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        List<String> workflowIds = generateWorkflows(workflow, 10);
        long count = getExecutionDAO().getPendingWorkflowCount(def.getName());
        assertEquals(10, count);

        for (int i = 0; i < 10; i++) {
            getExecutionDAO().removeFromPendingWorkflow(def.getName(), workflowIds.get(i));
        }

        count = getExecutionDAO().getPendingWorkflowCount(def.getName());
        assertEquals(0, count);
    }

    @Test
    public void complexExecutionTest() {
        WorkflowModel workflow = createTestWorkflow();
        int numTasks = workflow.getTasks().size();

        String workflowId = getExecutionDAO().createWorkflow(workflow);
        assertEquals(workflow.getWorkflowId(), workflowId);

        List<TaskModel> created = getExecutionDAO().createTasks(workflow.getTasks());
        assertEquals(workflow.getTasks().size(), created.size());

        WorkflowModel workflowWithTasks =
                getExecutionDAO().getWorkflow(workflow.getWorkflowId(), true);
        assertEquals(workflowId, workflowWithTasks.getWorkflowId());
        assertEquals(numTasks, workflowWithTasks.getTasks().size());

        WorkflowModel found = getExecutionDAO().getWorkflow(workflowId, false);
        assertTrue(found.getTasks().isEmpty());

        workflow.getTasks().clear();
        assertEquals(workflow, found);

        workflow.getInput().put("updated", true);
        getExecutionDAO().updateWorkflow(workflow);
        found = getExecutionDAO().getWorkflow(workflowId);
        assertNotNull(found);
        assertTrue(found.getInput().containsKey("updated"));
        assertEquals(true, found.getInput().get("updated"));

        List<String> running =
                getExecutionDAO()
                        .getRunningWorkflowIds(
                                workflow.getWorkflowName(), workflow.getWorkflowVersion());
        assertNotNull(running);
        assertTrue(running.isEmpty());

        workflow.setStatus(WorkflowModel.Status.RUNNING);
        getExecutionDAO().updateWorkflow(workflow);

        running =
                getExecutionDAO()
                        .getRunningWorkflowIds(
                                workflow.getWorkflowName(), workflow.getWorkflowVersion());
        assertNotNull(running);
        assertEquals(1, running.size());
        assertEquals(workflow.getWorkflowId(), running.get(0));

        List<WorkflowModel> pending =
                getExecutionDAO()
                        .getPendingWorkflowsByType(
                                workflow.getWorkflowName(), workflow.getWorkflowVersion());
        assertNotNull(pending);
        assertEquals(1, pending.size());
        assertEquals(3, pending.get(0).getTasks().size());
        pending.get(0).getTasks().clear();
        assertEquals(workflow, pending.get(0));

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        getExecutionDAO().updateWorkflow(workflow);
        running =
                getExecutionDAO()
                        .getRunningWorkflowIds(
                                workflow.getWorkflowName(), workflow.getWorkflowVersion());
        assertNotNull(running);
        assertTrue(running.isEmpty());

        List<WorkflowModel> bytime =
                getExecutionDAO()
                        .getWorkflowsByType(
                                workflow.getWorkflowName(),
                                System.currentTimeMillis(),
                                System.currentTimeMillis() + 100);
        assertNotNull(bytime);
        assertTrue(bytime.isEmpty());

        bytime =
                getExecutionDAO()
                        .getWorkflowsByType(
                                workflow.getWorkflowName(),
                                workflow.getCreateTime() - 10,
                                workflow.getCreateTime() + 10);
        assertNotNull(bytime);
        assertEquals(1, bytime.size());
    }

    protected WorkflowModel createTestWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("Junit Workflow");
        def.setVersion(3);
        def.setSchemaVersion(2);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);
        workflow.setCorrelationId("correlationX");
        workflow.setCreatedBy("junit_tester");
        workflow.setEndTime(200L);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param1 value");
        input.put("param2", 100);
        workflow.setInput(input);

        Map<String, Object> output = new HashMap<>();
        output.put("ouput1", "output 1 value");
        output.put("op2", 300);
        workflow.setOutput(output);

        workflow.setOwnerApp("workflow");
        workflow.setParentWorkflowId("parentWorkflowId");
        workflow.setParentWorkflowTaskId("parentWFTaskId");
        workflow.setReasonForIncompletion("missing recipe");
        workflow.setReRunFromWorkflowId("re-run from id1");
        workflow.setCreateTime(90L);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setWorkflowId(UUID.randomUUID().toString());

        List<TaskModel> tasks = new LinkedList<>();

        TaskModel task = new TaskModel();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(UUID.randomUUID().toString());
        task.setReferenceTaskName("t1");
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setTaskDefName("task1");

        TaskModel task2 = new TaskModel();
        task2.setScheduledTime(2L);
        task2.setSeq(2);
        task2.setTaskId(UUID.randomUUID().toString());
        task2.setReferenceTaskName("t2");
        task2.setWorkflowInstanceId(workflow.getWorkflowId());
        task2.setTaskDefName("task2");

        TaskModel task3 = new TaskModel();
        task3.setScheduledTime(2L);
        task3.setSeq(3);
        task3.setTaskId(UUID.randomUUID().toString());
        task3.setReferenceTaskName("t3");
        task3.setWorkflowInstanceId(workflow.getWorkflowId());
        task3.setTaskDefName("task3");

        tasks.add(task);
        tasks.add(task2);
        tasks.add(task3);

        workflow.setTasks(tasks);

        workflow.setUpdatedBy("junit_tester");
        workflow.setUpdatedTime(800L);

        return workflow;
    }

    protected List<String> generateWorkflows(WorkflowModel base, int count) {
        List<String> workflowIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String workflowId = UUID.randomUUID().toString();
            base.setWorkflowId(workflowId);
            base.setCorrelationId("corr001");
            base.setStatus(WorkflowModel.Status.RUNNING);
            getExecutionDAO().createWorkflow(base);
            workflowIds.add(workflowId);
        }
        return workflowIds;
    }
}
