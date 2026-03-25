package com.netflix.conductor.core.execution.mapper;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.ConditionalBranch;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Maps a {@link WorkflowTask} of type {@link TaskType#CONDITIONAL_FORK} to a list of {@link
 * TaskModel}: a completed FORK task, the first task of each selected branch (in parallel), and a
 * JOIN task.
 *
 * <p>Branch selection: - Branches with no condition always run. - Branches with a condition run
 * only when the condition evaluates to true. - If no branches are selected, the defaultCase runs.
 */
@Component
public class ConditionalForkTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalForkTaskMapper.class);

    private final Map<String, Evaluator> evaluators;
    private final IDGenerator idGenerator;

    public ConditionalForkTaskMapper(Map<String, Evaluator> evaluators, IDGenerator idGenerator) {
        this.evaluators = evaluators;
        this.idGenerator = idGenerator;
    }

    @Override
    public String getTaskType() {
        return TaskType.CONDITIONAL_FORK.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        LOGGER.debug("TaskMapperContext {} in ConditionalForkTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();

        // 1. Create completed FORK task
        TaskModel forkTask = taskMapperContext.createTaskModel();
        forkTask.setTaskType(TaskType.TASK_TYPE_FORK);
        forkTask.setTaskDefName(TaskType.TASK_TYPE_FORK);
        long epochMillis = System.currentTimeMillis();
        forkTask.setStartTime(epochMillis);
        forkTask.setEndTime(epochMillis);
        forkTask.getInputData().putAll(taskInput);
        forkTask.setStatus(TaskModel.Status.COMPLETED);
        tasksToBeScheduled.add(forkTask);

        // 2. Select branches to run
        List<List<WorkflowTask>> selectedBranches = new LinkedList<>();
        for (ConditionalBranch branch : workflowTask.getConditionalBranches()) {
            if (branch.getTasks().isEmpty()) {
                continue;
            }
            ConditionalBranch.ConditionSpec cond = branch.getCondition();
            if (cond == null || cond.getEvaluatorType() == null || cond.getExpression() == null) {
                // No condition - always run
                selectedBranches.add(branch.getTasks());
                continue;
            }
            Evaluator evaluator = evaluators.get(cond.getEvaluatorType());
            if (evaluator == null) {
                throw new TerminateWorkflowException(
                        "No evaluator registered for type: " + cond.getEvaluatorType());
            }
            try {
                Object result = evaluator.evaluate(cond.getExpression(), taskInput);
                if (Boolean.TRUE.equals(result)
                        || "true".equalsIgnoreCase(String.valueOf(result))) {
                    selectedBranches.add(branch.getTasks());
                }
            } catch (Exception e) {
                LOGGER.warn("Condition evaluation failed for branch, skipping: {}", e.getMessage());
            }
        }

        // 3. If no branches selected, use default case
        if (selectedBranches.isEmpty()) {
            List<WorkflowTask> defaultCase = workflowTask.getDefaultCase();
            if (defaultCase != null && !defaultCase.isEmpty()) {
                selectedBranches.add(defaultCase);
            }
        }

        if (selectedBranches.isEmpty()) {
            LOGGER.debug("No branches selected and no default case for CONDITIONAL_FORK");
            // Still need to schedule JOIN - with empty joinOn the JOIN would need special handling
            // For now, require at least one branch or default
            throw new TerminateWorkflowException(
                    "CONDITIONAL_FORK has no branches selected and no defaultCase defined");
        }

        // 4. Schedule first task of each selected branch and collect joinOn refs
        List<String> joinOnRefs = new LinkedList<>();
        for (List<WorkflowTask> branchTasks : selectedBranches) {
            WorkflowTask firstTask = branchTasks.get(0);
            List<TaskModel> branchTaskModels =
                    taskMapperContext
                            .getDeciderService()
                            .getTasksToBeScheduled(
                                    workflowModel,
                                    firstTask,
                                    retryCount,
                                    taskMapperContext.getRetryTaskId());
            tasksToBeScheduled.addAll(branchTaskModels);
            // Last task of branch is what JOIN waits on (for single-task branch, it's the first)
            WorkflowTask lastTask = branchTasks.get(branchTasks.size() - 1);
            joinOnRefs.add(lastTask.getTaskReferenceName());
        }

        // 5. Get JOIN task from workflow def and create it with dynamic joinOn
        WorkflowTask joinWorkflowTask =
                workflowModel
                        .getWorkflowDefinition()
                        .getNextTask(workflowTask.getTaskReferenceName());

        if (joinWorkflowTask == null || !joinWorkflowTask.getType().equals(TaskType.JOIN.name())) {
            throw new TerminateWorkflowException(
                    "CONDITIONAL_FORK must be followed by a JOIN task. Check the workflow definition.");
        }

        HashMap<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", joinOnRefs);
        TaskModel joinTask = createJoinTask(workflowModel, joinWorkflowTask, joinInput);
        tasksToBeScheduled.add(joinTask);

        return tasksToBeScheduled;
    }

    private TaskModel createJoinTask(
            WorkflowModel workflowModel,
            WorkflowTask joinWorkflowTask,
            Map<String, Object> joinInput) {
        TaskModel joinTask = new TaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_JOIN);
        joinTask.setReferenceTaskName(joinWorkflowTask.getTaskReferenceName());
        joinTask.setWorkflowInstanceId(workflowModel.getWorkflowId());
        joinTask.setWorkflowType(workflowModel.getWorkflowName());
        joinTask.setCorrelationId(workflowModel.getCorrelationId());
        joinTask.setScheduledTime(System.currentTimeMillis());
        joinTask.setStartTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setTaskId(idGenerator.generate());
        joinTask.setStatus(TaskModel.Status.IN_PROGRESS);
        joinTask.setWorkflowTask(joinWorkflowTask);
        joinTask.setWorkflowPriority(workflowModel.getPriority());
        return joinTask;
    }
}