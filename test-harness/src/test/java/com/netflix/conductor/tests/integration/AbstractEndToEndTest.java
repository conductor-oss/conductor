package com.netflix.conductor.tests.integration;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class AbstractEndToEndTest {

    protected WorkflowTask createWorkflowTask(String name) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(name);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName(name);
        return workflowTask;
    }

    protected TaskDef createTaskDefinition(String name) {
        TaskDef taskDefinition = new TaskDef();
        taskDefinition.setName(name);
        return taskDefinition;
    }
}
