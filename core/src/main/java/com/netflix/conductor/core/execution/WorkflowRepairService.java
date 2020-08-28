package com.netflix.conductor.core.execution;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * A helper service that tries to keep ExecutionDAO and QueueDAO in sync, based on the
 * task or workflow state.
 */
public class WorkflowRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowRepairService.class);

    private final ExecutionDAO executionDAO;
    private final QueueDAO queueDAO;

    @Inject
    public WorkflowRepairService(
            ExecutionDAO executionDAO,
            QueueDAO queueDAO
    ) {
        this.executionDAO = executionDAO;
        this.queueDAO = queueDAO;
    }

    public void verifyAndRepairWorkflow(String workflowId, boolean includeTasks) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, includeTasks);
        verifyAndRepairWorkflow(workflow);
        if (includeTasks) {
            workflow.getTasks().forEach(task -> verifyAndRepairTask(task));
        }
    }

    public void verifyAndRepairWorkflowTasks(String workflowId) {
        Workflow workflow = executionDAO.getWorkflow(workflowId, true);
        workflow.getTasks().forEach(task -> verifyAndRepairTask(task));
    }

    private boolean verifyAndRepairWorkflow(Workflow workflow) {
        if (workflow.getStatus().equals(Workflow.WorkflowStatus.RUNNING)) {
            String queueName = WorkflowExecutor.DECIDER_QUEUE;
            if (!queueDAO.containsMessage(queueName, workflow.getWorkflowId())) {
                queueDAO.push(queueName, workflow.getWorkflowId(), 30);
                Monitors.recordQueueMessageRepushFromRepairService(queueName);
                return true;
            }
        }
        return false;
    }

    /**
     * Verify if ExecutionDAO and QueueDAO agree for the provided task.
     * @param task
     * @return
     */
    private boolean verifyAndRepairTask(Task task) {
        if (task.getStatus().equals(Task.Status.SCHEDULED)) {
            // Ensure QueueDAO contains this taskId
            // TODO handle exception in these queue operations here?
            // TODO how to keep track of these exceptions?
            if (!queueDAO.containsMessage(task.getTaskDefName(), task.getTaskId())) {
                queueDAO.push(task.getTaskDefName(), task.getTaskId(), task.getCallbackAfterSeconds());
                Monitors.recordQueueMessageRepushFromRepairService(task.getTaskDefName());
                return true;
            }
        }
        return false;
    }
}
