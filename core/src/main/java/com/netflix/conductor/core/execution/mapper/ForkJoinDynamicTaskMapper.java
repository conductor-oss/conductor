package com.netflix.conductor.core.execution.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.SystemTask;
import com.netflix.conductor.core.execution.TerminateWorkflow;
import com.netflix.conductor.core.utils.IDGenerator;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ForkJoinDynamicTaskMapper implements TaskMapper {

    private ParametersUtils parametersUtils;

    private ObjectMapper objectMapper;

    private static final TypeReference<List<WorkflowTask>> ListOfWorkflowTasks = new TypeReference<List<WorkflowTask>>() {
    };

    public ForkJoinDynamicTaskMapper(ParametersUtils parametersUtils, ObjectMapper objectMapper) {
        this.parametersUtils = parametersUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        WorkflowDef workflowDef = taskMapperContext.getWorkflowDefinition();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        List<Task> tasks = new LinkedList<>();
        List<WorkflowTask> dynForkTasks = new LinkedList<>();
        Map<String, Map<String, Object>> tasksInput = new HashMap<>();

        String paramName = taskToSchedule.getDynamicForkTasksParam();

        if (paramName != null) {

            Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflowInstance, null, null);
            Object paramValue = input.get(paramName);
            dynForkTasks = objectMapper.convertValue(paramValue, ListOfWorkflowTasks);
            Object tasksInputO = input.get(taskToSchedule.getDynamicForkTasksInputParamName());
            if (!(tasksInputO instanceof Map)) {
                throw new TerminateWorkflow("Input to the dynamically forked tasks is not a map -> expecting a map of K,V  but found " + tasksInputO);
            }
            tasksInput = (Map<String, Map<String, Object>>) tasksInputO;

        } else {
            paramName = taskToSchedule.getDynamicForkJoinTasksParam();
            Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflowInstance, null, null);
            Object paramValue = input.get(paramName);
            DynamicForkJoinTaskList dynForkTasks0 = objectMapper.convertValue(paramValue, DynamicForkJoinTaskList.class);
            if (dynForkTasks0 == null) {
                throw new TerminateWorkflow("Dynamic tasks could not be created.  The value of " + paramName + " from task's input " + input + " has no dynamic tasks to be scheduled");
            }
            for (DynamicForkJoinTask dt : dynForkTasks0.getDynamicTasks()) {
                WorkflowTask wft = new WorkflowTask();
                wft.setTaskReferenceName(dt.getReferenceName());
                wft.setName(dt.getTaskName());
                wft.setType(dt.getType());
                tasksInput.put(dt.getReferenceName(), dt.getInput());
                dynForkTasks.add(wft);
            }
        }

        // Create Fork Task
        Task st = SystemTask.forkDynamicTask(workflowInstance, taskId, taskToSchedule, dynForkTasks);

        tasks.add(st);
        List<String> joinOnTaskRefs = new LinkedList<>();
        // Create Dynamic tasks
        for (WorkflowTask wft : dynForkTasks) {
            List<Task> forkedTasks = taskMapperContext.getDeciderService().getTasksToBeScheduled(workflowDef, workflowInstance, wft, retryCount);
            tasks.addAll(forkedTasks);
            Task last = forkedTasks.get(forkedTasks.size() - 1);
            joinOnTaskRefs.add(last.getReferenceTaskName());
            for (Task ft : forkedTasks) {
                Map<String, Object> forkedTaskInput = tasksInput.get(ft.getReferenceTaskName());
                if (forkedTaskInput != null && (!(forkedTaskInput instanceof Map))) {
                    throw new TerminateWorkflow("Input to the dynamically forked task " + ft.getReferenceTaskName() + " is not a map, this is what I got " + forkedTaskInput);
                }
                ft.getInputData().putAll(forkedTaskInput);
            }
        }

        WorkflowTask joinWorkflowTask = workflowDef.getNextTask(taskToSchedule.getTaskReferenceName());
        if (joinWorkflowTask == null || !joinWorkflowTask.getType().equals(WorkflowTask.Type.JOIN.name())) {
            throw new TerminateWorkflow("Dynamic join definition is not followed by a join task.  Check the blueprint");
        }
        // Create Join task
        HashMap<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", joinOnTaskRefs);
        tasks.add(SystemTask.JoinTask(workflowInstance, IDGenerator.generate(), joinWorkflowTask, joinInput));
        return tasks;
    }
}
