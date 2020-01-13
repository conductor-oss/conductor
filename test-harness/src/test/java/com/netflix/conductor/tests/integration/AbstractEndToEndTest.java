package com.netflix.conductor.tests.integration;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AbstractEndToEndTest {

    private static final String TASK_DEFINITION_PREFIX = "task_";
    private static final String DEFAULT_DESCRIPTION = "description";
    // Represents null value deserialized from the redis in memory db
    private static final String DEFAULT_NULL_VALUE = "null";
    protected static final String DEFAULT_EMAIL_ADDRESS = "test@harness.com";

    @Test
    public void testEphemeralWorkflowsWithStoredTasks() {
        String workflowExecutionName = "testEphemeralWorkflow";

        createAndRegisterTaskDefinitions("storedTaskDef", 5);
        WorkflowDef workflowDefinition = createWorkflowDefinition(workflowExecutionName);
        WorkflowTask workflowTask1 = createWorkflowTask("storedTaskDef1");
        WorkflowTask workflowTask2 = createWorkflowTask("storedTaskDef2");
        workflowDefinition.getTasks().addAll(Arrays.asList(workflowTask1, workflowTask2));

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);
        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);
    }

    @Test
    public void testEphemeralWorkflowsWithEphemeralTasks() {
        String workflowExecutionName = "ephemeralWorkflowWithEphemeralTasks";

        WorkflowDef workflowDefinition = createWorkflowDefinition(workflowExecutionName);
        WorkflowTask workflowTask1 = createWorkflowTask("ephemeralTask1");
        TaskDef taskDefinition1 = createTaskDefinition("ephemeralTaskDef1");
        workflowTask1.setTaskDefinition(taskDefinition1);
        WorkflowTask workflowTask2 = createWorkflowTask("ephemeralTask2");
        TaskDef taskDefinition2 = createTaskDefinition("ephemeralTaskDef2");
        workflowTask2.setTaskDefinition(taskDefinition2);
        workflowDefinition.getTasks().addAll(Arrays.asList(workflowTask1, workflowTask2));

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);

        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);

        List<WorkflowTask> ephemeralTasks = ephemeralWorkflow.getTasks();
        assertEquals(2, ephemeralTasks.size());
        for (WorkflowTask ephemeralTask : ephemeralTasks) {
            assertNotNull(ephemeralTask.getTaskDefinition());
        }
    }

    @Test
    public void testEphemeralWorkflowsWithEphemeralAndStoredTasks() {
        createAndRegisterTaskDefinitions("storedTask", 1);

        WorkflowDef workflowDefinition = createWorkflowDefinition("testEphemeralWorkflowsWithEphemeralAndStoredTasks");

        WorkflowTask workflowTask1 = createWorkflowTask("ephemeralTask1");
        TaskDef taskDefinition1 = createTaskDefinition("ephemeralTaskDef1");
        workflowTask1.setTaskDefinition(taskDefinition1);

        WorkflowTask workflowTask2 = createWorkflowTask("storedTask0");

        workflowDefinition.getTasks().add(workflowTask1);
        workflowDefinition.getTasks().add(workflowTask2);

        String workflowExecutionName = "ephemeralWorkflowWithEphemeralAndStoredTasks";

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);
        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);

        TaskDef storedTaskDefinition = getTaskDefinition("storedTask0");
        List<WorkflowTask> tasks = ephemeralWorkflow.getTasks();
        assertEquals(2, tasks.size());
        assertEquals(workflowTask1, tasks.get(0));
        TaskDef currentStoredTaskDefinition = tasks.get(1).getTaskDefinition();
        assertNotNull(currentStoredTaskDefinition);
        assertEquals(storedTaskDefinition, currentStoredTaskDefinition);

    }

    protected WorkflowTask createWorkflowTask(String name) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(name);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName(name);
        workflowTask.setDescription(getDefaultDescription(name));
        workflowTask.setDynamicTaskNameParam(DEFAULT_NULL_VALUE);
        workflowTask.setCaseValueParam(DEFAULT_NULL_VALUE);
        workflowTask.setCaseExpression(DEFAULT_NULL_VALUE);
        workflowTask.setDynamicForkTasksParam(DEFAULT_NULL_VALUE);
        workflowTask.setDynamicForkTasksInputParamName(DEFAULT_NULL_VALUE);
        workflowTask.setSink(DEFAULT_NULL_VALUE);
        return workflowTask;
    }

    protected TaskDef createTaskDefinition(String name) {
        TaskDef taskDefinition = new TaskDef();
        taskDefinition.setName(name);
        return taskDefinition;
    }

    protected WorkflowDef createWorkflowDefinition(String workflowName) {
        WorkflowDef workflowDefinition = new WorkflowDef();
        workflowDefinition.setName(workflowName);
        workflowDefinition.setDescription(getDefaultDescription(workflowName));
        workflowDefinition.setFailureWorkflow(DEFAULT_NULL_VALUE);
        workflowDefinition.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);
        return workflowDefinition;
    }

    protected List<TaskDef> createAndRegisterTaskDefinitions(String prefixTaskDefinition, int numberOfTaskDefinitions) {
        String prefix = Optional.ofNullable(prefixTaskDefinition).orElse(TASK_DEFINITION_PREFIX);
        List<TaskDef> definitions = new LinkedList<>();
        for (int i = 0; i < numberOfTaskDefinitions; i++) {
            TaskDef def = new TaskDef(prefix + i, "task " + i + DEFAULT_DESCRIPTION, DEFAULT_EMAIL_ADDRESS, 3, 60 ,60);
            def.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
            definitions.add(def);
        }
        this.registerTaskDefinitions(definitions);
        return definitions;
    }

    private String getDefaultDescription(String nameResource) {
        return nameResource + " " + DEFAULT_DESCRIPTION;
    }

    protected abstract String startWorkflow(String workflowExecutionName, WorkflowDef workflowDefinition);

    protected abstract Workflow getWorkflow(String workflowId, boolean includeTasks);

    protected abstract TaskDef getTaskDefinition(String taskName);

    protected abstract void registerTaskDefinitions(List<TaskDef> taskDefinitionList);
}
