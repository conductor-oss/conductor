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
package com.netflix.conductor.core.execution.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#FORK_JOIN_DYNAMIC} to a LinkedList of {@link TaskModel} beginning with a {@link
 * TaskType#TASK_TYPE_FORK}, followed by the user defined dynamic tasks and a {@link TaskType#JOIN}
 * at the end
 */
@Component
public class ForkJoinDynamicTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(ForkJoinDynamicTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final ObjectMapper objectMapper;
    private final MetadataDAO metadataDAO;
    private static final TypeReference<List<WorkflowTask>> ListOfWorkflowTasks =
            new TypeReference<>() {};

    @Autowired
    public ForkJoinDynamicTaskMapper(
            ParametersUtils parametersUtils, ObjectMapper objectMapper, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.objectMapper = objectMapper;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.FORK_JOIN_DYNAMIC;
    }

    /**
     * This method gets the list of tasks that need to scheduled when the task to scheduled is of
     * type {@link TaskType#FORK_JOIN_DYNAMIC}. Creates a Fork Task, followed by the Dynamic tasks
     * and a final JOIN task.
     *
     * <p>The definitions of the dynamic forks that need to be scheduled are available in the {@link
     * WorkflowTask#getInputParameters()} which are accessed using the {@link
     * TaskMapperContext#getTaskToSchedule()}. The dynamic fork task definitions are referred by a
     * key value either by {@link WorkflowTask#getDynamicForkTasksParam()} or by {@link
     * WorkflowTask#getDynamicForkJoinTasksParam()} When creating the list of tasks to be scheduled
     * a set of preconditions are validated:
     *
     * <ul>
     *   <li>If the input parameter representing the Dynamic fork tasks is available as part of
     *       {@link WorkflowTask#getDynamicForkTasksParam()} then the input for the dynamic task is
     *       validated to be a map by using {@link WorkflowTask#getDynamicForkTasksInputParamName()}
     *   <li>If the input parameter representing the Dynamic fork tasks is available as part of
     *       {@link WorkflowTask#getDynamicForkJoinTasksParam()} then the input for the dynamic
     *       tasks is available in the payload of the tasks definition.
     *   <li>A check is performed that the next following task in the {@link WorkflowDef} is a
     *       {@link TaskType#JOIN}
     * </ul>
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return List of tasks in the following order:
     *     <ul>
     *       <li>{@link TaskType#TASK_TYPE_FORK} with {@link TaskModel.Status#COMPLETED}
     *       <li>Might be any kind of task, but this is most cases is a UserDefinedTask with {@link
     *           TaskModel.Status#SCHEDULED}
     *       <li>{@link TaskType#JOIN} with {@link TaskModel.Status#IN_PROGRESS}
     *     </ul>
     *
     * @throws TerminateWorkflowException In case of:
     *     <ul>
     *       <li>When the task after {@link TaskType#FORK_JOIN_DYNAMIC} is not a {@link
     *           TaskType#JOIN}
     *       <li>When the input parameters for the dynamic tasks are not of type {@link Map}
     *     </ul>
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        LOGGER.debug("TaskMapperContext {} in ForkJoinDynamicTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        List<TaskModel> mappedTasks = new LinkedList<>();
        // Get the list of dynamic tasks and the input for the tasks
        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> workflowTasksAndInputPair =
                Optional.ofNullable(taskToSchedule.getDynamicForkTasksParam())
                        .map(
                                dynamicForkTaskParam ->
                                        getDynamicForkTasksAndInput(
                                                taskToSchedule,
                                                workflowInstance,
                                                dynamicForkTaskParam))
                        .orElseGet(
                                () ->
                                        getDynamicForkJoinTasksAndInput(
                                                taskToSchedule, workflowInstance));

        List<WorkflowTask> dynForkTasks = workflowTasksAndInputPair.getLeft();
        Map<String, Map<String, Object>> tasksInput = workflowTasksAndInputPair.getRight();

        // Create Fork Task which needs to be followed by the dynamic tasks
        TaskModel forkDynamicTask =
                createDynamicForkTask(taskToSchedule, workflowInstance, taskId, dynForkTasks);

        mappedTasks.add(forkDynamicTask);

        List<String> joinOnTaskRefs = new LinkedList<>();
        // Add each dynamic task to the mapped tasks and also get the last dynamic task in the list,
        // which indicates that the following task after that needs to be a join task
        for (WorkflowTask dynForkTask :
                dynForkTasks) { // TODO this is a cyclic dependency, break it out using function
            // composition
            List<TaskModel> forkedTasks =
                    taskMapperContext
                            .getDeciderService()
                            .getTasksToBeScheduled(workflowInstance, dynForkTask, retryCount);

            // It's an error state if no forkedTasks can be decided upon. In the cases where we've
            // seen
            // this happen is when a dynamic task is attempting to be created here, but a task with
            // the
            // same reference name has already been created in the Workflow.
            if (forkedTasks == null || forkedTasks.isEmpty()) {
                Optional<String> existingTaskRefName =
                        workflowInstance.getTasks().stream()
                                .filter(
                                        runningTask ->
                                                runningTask
                                                                .getStatus()
                                                                .equals(
                                                                        TaskModel.Status
                                                                                .IN_PROGRESS)
                                                        || runningTask.getStatus().isTerminal())
                                .map(TaskModel::getReferenceTaskName)
                                .filter(
                                        refTaskName ->
                                                refTaskName.equals(
                                                        dynForkTask.getTaskReferenceName()))
                                .findAny();

                // Construct an informative error message
                String terminateMessage =
                        "No dynamic tasks could be created for the Workflow: "
                                + workflowInstance.toShortString()
                                + ", Dynamic Fork Task: "
                                + dynForkTask;
                if (existingTaskRefName.isPresent()) {
                    terminateMessage +=
                            "Attempted to create a duplicate task reference name: "
                                    + existingTaskRefName.get();
                }
                throw new TerminateWorkflowException(terminateMessage);
            }

            for (TaskModel forkedTask : forkedTasks) {
                Map<String, Object> forkedTaskInput =
                        tasksInput.get(forkedTask.getReferenceTaskName());
                forkedTask.getInputData().putAll(forkedTaskInput);
            }
            mappedTasks.addAll(forkedTasks);
            // Get the last of the dynamic tasks so that the join can be performed once this task is
            // done
            TaskModel last = forkedTasks.get(forkedTasks.size() - 1);
            joinOnTaskRefs.add(last.getReferenceTaskName());
        }

        // From the workflow definition get the next task and make sure that it is a JOIN task.
        // The dynamic fork tasks need to be followed by a join task
        WorkflowTask joinWorkflowTask =
                workflowInstance
                        .getWorkflowDefinition()
                        .getNextTask(taskToSchedule.getTaskReferenceName());

        if (joinWorkflowTask == null || !joinWorkflowTask.getType().equals(TaskType.JOIN.name())) {
            throw new TerminateWorkflowException(
                    "Dynamic join definition is not followed by a join task.  Check the blueprint");
        }

        // Create Join task
        HashMap<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", joinOnTaskRefs);
        TaskModel joinTask = createJoinTask(workflowInstance, joinWorkflowTask, joinInput);
        mappedTasks.add(joinTask);

        return mappedTasks;
    }

    /**
     * This method creates a FORK task and adds the list of dynamic fork tasks keyed by
     * "forkedTaskDefs" and their names keyed by "forkedTasks" into {@link TaskModel#getInputData()}
     *
     * @param taskToSchedule A {@link WorkflowTask} representing {@link TaskType#FORK_JOIN_DYNAMIC}
     * @param workflowInstance: A instance of the {@link WorkflowModel} which represents the
     *     workflow being executed.
     * @param taskId: The string representation of {@link java.util.UUID} which will be set as the
     *     taskId.
     * @param dynForkTasks: The list of dynamic forked tasks, the reference names of these tasks
     *     will be added to the forkDynamicTask
     * @return A new instance of {@link TaskModel} representing a {@link TaskType#TASK_TYPE_FORK}
     */
    @VisibleForTesting
    TaskModel createDynamicForkTask(
            WorkflowTask taskToSchedule,
            WorkflowModel workflowInstance,
            String taskId,
            List<WorkflowTask> dynForkTasks) {
        TaskModel forkDynamicTask = new TaskModel();
        forkDynamicTask.setTaskType(TaskType.TASK_TYPE_FORK);
        forkDynamicTask.setTaskDefName(TaskType.TASK_TYPE_FORK);
        forkDynamicTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        forkDynamicTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        forkDynamicTask.setCorrelationId(workflowInstance.getCorrelationId());
        forkDynamicTask.setScheduledTime(System.currentTimeMillis());
        forkDynamicTask.setEndTime(System.currentTimeMillis());
        List<String> forkedTaskNames =
                dynForkTasks.stream()
                        .map(WorkflowTask::getTaskReferenceName)
                        .collect(Collectors.toList());
        forkDynamicTask.getInputData().put("forkedTasks", forkedTaskNames);
        forkDynamicTask
                .getInputData()
                .put(
                        "forkedTaskDefs",
                        dynForkTasks); // TODO: Remove this parameter in the later releases
        forkDynamicTask.setTaskId(taskId);
        forkDynamicTask.setStatus(TaskModel.Status.COMPLETED);
        forkDynamicTask.setWorkflowTask(taskToSchedule);
        forkDynamicTask.setWorkflowPriority(workflowInstance.getPriority());
        return forkDynamicTask;
    }

    /**
     * This method creates a JOIN task that is used in the {@link
     * this#getMappedTasks(TaskMapperContext)} at the end to add a join task to be scheduled after
     * all the fork tasks
     *
     * @param workflowInstance: A instance of the {@link WorkflowModel} which represents the
     *     workflow being executed.
     * @param joinWorkflowTask: A instance of {@link WorkflowTask} which is of type {@link
     *     TaskType#JOIN}
     * @param joinInput: The input which is set in the {@link TaskModel#setInputData(Map)}
     * @return a new instance of {@link TaskModel} representing a {@link TaskType#JOIN}
     */
    @VisibleForTesting
    TaskModel createJoinTask(
            WorkflowModel workflowInstance,
            WorkflowTask joinWorkflowTask,
            HashMap<String, Object> joinInput) {
        TaskModel joinTask = new TaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_JOIN);
        joinTask.setReferenceTaskName(joinWorkflowTask.getTaskReferenceName());
        joinTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        joinTask.setWorkflowType(workflowInstance.getWorkflowName());
        joinTask.setCorrelationId(workflowInstance.getCorrelationId());
        joinTask.setScheduledTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setTaskId(IDGenerator.generate());
        joinTask.setStatus(TaskModel.Status.IN_PROGRESS);
        joinTask.setWorkflowTask(joinWorkflowTask);
        joinTask.setWorkflowPriority(workflowInstance.getPriority());
        return joinTask;
    }

    /**
     * This method is used to get the List of dynamic workflow tasks and their input based on the
     * {@link WorkflowTask#getDynamicForkTasksParam()}
     *
     * @param taskToSchedule: The Task of type FORK_JOIN_DYNAMIC that needs to scheduled, which has
     *     the input parameters
     * @param workflowInstance: The instance of the {@link WorkflowModel} which represents the
     *     workflow being executed.
     * @param dynamicForkTaskParam: The key representing the dynamic fork join json payload which is
     *     available in {@link WorkflowTask#getInputParameters()}
     * @return a {@link Pair} representing the list of dynamic fork tasks in {@link Pair#getLeft()}
     *     and the input for the dynamic fork tasks in {@link Pair#getRight()}
     * @throws TerminateWorkflowException : In case of input parameters of the dynamic fork tasks
     *     not represented as {@link Map}
     */
    @SuppressWarnings("unchecked")
    @VisibleForTesting
    Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> getDynamicForkTasksAndInput(
            WorkflowTask taskToSchedule,
            WorkflowModel workflowInstance,
            String dynamicForkTaskParam)
            throws TerminateWorkflowException {

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        taskToSchedule.getInputParameters(), workflowInstance, null, null);
        Object dynamicForkTasksJson = input.get(dynamicForkTaskParam);
        List<WorkflowTask> dynamicForkWorkflowTasks =
                objectMapper.convertValue(dynamicForkTasksJson, ListOfWorkflowTasks);
        if (dynamicForkWorkflowTasks == null) {
            dynamicForkWorkflowTasks = new ArrayList<>();
        }
        for (WorkflowTask workflowTask : dynamicForkWorkflowTasks) {
            if ((workflowTask.getTaskDefinition() == null)
                    && StringUtils.isNotBlank(workflowTask.getName())) {
                workflowTask.setTaskDefinition(metadataDAO.getTaskDef(workflowTask.getName()));
            }
        }
        Object dynamicForkTasksInput =
                input.get(taskToSchedule.getDynamicForkTasksInputParamName());
        if (!(dynamicForkTasksInput instanceof Map)) {
            throw new TerminateWorkflowException(
                    "Input to the dynamically forked tasks is not a map -> expecting a map of K,V  but found "
                            + dynamicForkTasksInput);
        }
        return new ImmutablePair<>(
                dynamicForkWorkflowTasks, (Map<String, Map<String, Object>>) dynamicForkTasksInput);
    }

    /**
     * This method is used to get the List of dynamic workflow tasks and their input based on the
     * {@link WorkflowTask#getDynamicForkJoinTasksParam()}
     *
     * <p><b>NOTE:</b> This method is kept for legacy reasons, new workflows should use the {@link
     * #getDynamicForkTasksAndInput}
     *
     * @param taskToSchedule: The Task of type FORK_JOIN_DYNAMIC that needs to scheduled, which has
     *     the input parameters
     * @param workflowInstance: The instance of the {@link WorkflowModel} which represents the
     *     workflow being executed.
     * @return {@link Pair} representing the list of dynamic fork tasks in {@link Pair#getLeft()}
     *     and the input for the dynamic fork tasks in {@link Pair#getRight()}
     * @throws TerminateWorkflowException : In case of the {@link WorkflowTask#getInputParameters()}
     *     does not have a payload that contains the list of the dynamic tasks
     */
    @VisibleForTesting
    Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> getDynamicForkJoinTasksAndInput(
            WorkflowTask taskToSchedule, WorkflowModel workflowInstance)
            throws TerminateWorkflowException {
        String dynamicForkJoinTaskParam = taskToSchedule.getDynamicForkJoinTasksParam();
        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        taskToSchedule.getInputParameters(), workflowInstance, null, null);
        Object paramValue = input.get(dynamicForkJoinTaskParam);
        DynamicForkJoinTaskList dynamicForkJoinTaskList =
                objectMapper.convertValue(paramValue, DynamicForkJoinTaskList.class);

        if (dynamicForkJoinTaskList == null) {
            String reason =
                    String.format(
                            "Dynamic tasks could not be created. The value of %s from task's input %s has no dynamic tasks to be scheduled",
                            dynamicForkJoinTaskParam, input);
            LOGGER.error(reason);
            throw new TerminateWorkflowException(reason);
        }

        Map<String, Map<String, Object>> dynamicForkJoinTasksInput = new HashMap<>();

        List<WorkflowTask> dynamicForkJoinWorkflowTasks =
                dynamicForkJoinTaskList.getDynamicTasks().stream()
                        .peek(
                                dynamicForkJoinTask ->
                                        dynamicForkJoinTasksInput.put(
                                                dynamicForkJoinTask.getReferenceName(),
                                                dynamicForkJoinTask
                                                        .getInput())) // TODO create a custom pair
                        // collector
                        .map(
                                dynamicForkJoinTask -> {
                                    WorkflowTask dynamicForkJoinWorkflowTask = new WorkflowTask();
                                    dynamicForkJoinWorkflowTask.setTaskReferenceName(
                                            dynamicForkJoinTask.getReferenceName());
                                    dynamicForkJoinWorkflowTask.setName(
                                            dynamicForkJoinTask.getTaskName());
                                    dynamicForkJoinWorkflowTask.setType(
                                            dynamicForkJoinTask.getType());
                                    if (dynamicForkJoinWorkflowTask.getTaskDefinition() == null
                                            && StringUtils.isNotBlank(
                                                    dynamicForkJoinWorkflowTask.getName())) {
                                        dynamicForkJoinWorkflowTask.setTaskDefinition(
                                                metadataDAO.getTaskDef(
                                                        dynamicForkJoinTask.getTaskName()));
                                    }
                                    return dynamicForkJoinWorkflowTask;
                                })
                        .collect(Collectors.toCollection(LinkedList::new));

        return new ImmutablePair<>(dynamicForkJoinWorkflowTasks, dynamicForkJoinTasksInput);
    }
}
