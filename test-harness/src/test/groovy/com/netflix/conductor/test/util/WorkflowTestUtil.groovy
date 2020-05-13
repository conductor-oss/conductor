package com.netflix.conductor.test.util

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.WorkflowContext
import com.netflix.conductor.core.execution.ApplicationException
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.tests.utils.JsonUtils
import org.apache.commons.lang.StringUtils

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowTestUtil {

    private final MetadataService metadataService
    private final ExecutionService workflowExecutionService
    private final WorkflowExecutor workflowExecutor
    private final QueueDAO queueDAO
    private static final int RETRY_COUNT = 1
    private static final String TEMP_FILE_PATH = "/input.json"

    @Inject
    WorkflowTestUtil(MetadataService metadataService, ExecutionService workflowExecutionService,
                     WorkflowExecutor workflowExecutor, QueueDAO queueDAO) {
        this.metadataService = metadataService
        this.workflowExecutionService = workflowExecutionService
        this.workflowExecutor = workflowExecutor
        this.queueDAO = queueDAO
    }

    @PostConstruct
    def taskDefinitions() {
        WorkflowContext.set(new WorkflowContext("integration_app"))

        (0..20).collect { "integration_task_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect { new TaskDef(it, it, 1, 120) }
                .forEach { metadataService.registerTaskDef([it]) }

        (0..4).collect { "integration_task_0_RT_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect { new TaskDef(it, it, 0, 120) }
                .forEach { metadataService.registerTaskDef([it]) }

        metadataService.registerTaskDef([new TaskDef('short_time_out', 'short_time_out', 1, 5)])

        //This task is required by the integration test which exercises the response time out scenarios
        TaskDef task = new TaskDef()
        task.name = "task_rt"
        task.timeoutSeconds = 120
        task.retryCount = RETRY_COUNT
        task.retryDelaySeconds = 0
        task.responseTimeoutSeconds = 10
        metadataService.registerTaskDef([task])
    }

    def void clearWorkflows() throws Exception {
        List<String> workflowsWithVersion = metadataService.getWorkflowDefs()
                .collect { workflowDef -> workflowDef.getName() + ":" + workflowDef.getVersion() }
        for (String workflowWithVersion : workflowsWithVersion) {
            String workflowName = StringUtils.substringBefore(workflowWithVersion, ":")
            int version = Integer.parseInt(StringUtils.substringAfter(workflowWithVersion, ":"))
            List<String> running = workflowExecutionService.getRunningWorkflows(workflowName, version)
            for (String workflowId : running) {
                Workflow workflow = workflowExecutor.getWorkflow(workflowId, false)
                if (!workflow.getStatus().isTerminal()) {
                    workflowExecutor.terminateWorkflow(workflowId, "cleanup")
                }
            }
        }

        queueDAO.queuesDetail().keySet()
                .forEach { queueDAO.flush(it) }

        new FileOutputStream(this.getClass().getResource(TEMP_FILE_PATH).getPath()).close();
    }

    def Optional<TaskDef> getPersistedTaskDefinition(String taskDefName) {
        try {
            return Optional.of(metadataService.getTaskDef(taskDefName))
        } catch (ApplicationException applicationException) {
            if (applicationException.code == ApplicationException.Code.NOT_FOUND) {
                return Optional.empty()
            } else {
                throw applicationException
            }
        }
    }

    def registerWorkflows(String... workflowJsonPaths) {
        workflowJsonPaths.collect { JsonUtils.fromJson(it, WorkflowDef.class) }
                .forEach { metadataService.updateWorkflowDef(it) }
    }


    def Tuple pollAndFailTask(String taskName, String workerId, String failureReason, int waitAtEndSeconds) {
        def polledIntegrationTask = workflowExecutionService.poll(taskName, workerId)
        def ackPolledIntegrationTask = workflowExecutionService.ackTaskReceived(polledIntegrationTask.taskId)
        polledIntegrationTask.status = Task.Status.FAILED
        polledIntegrationTask.reasonForIncompletion = failureReason
        workflowExecutionService.updateTask(polledIntegrationTask)
        return waitAtEndSecondsAndReturn(waitAtEndSeconds, polledIntegrationTask, ackPolledIntegrationTask)
    }

    static def Tuple waitAtEndSecondsAndReturn(int waitAtEndSeconds, Task polledIntegrationTask, boolean ackPolledIntegrationTask) {
        if (waitAtEndSeconds > 0) {
            Thread.sleep(waitAtEndSeconds * 1000)
        }
        return new Tuple(polledIntegrationTask, ackPolledIntegrationTask)
    }

    def Tuple pollAndCompleteTask(String taskName, String workerId, Map<String, String> outputParams, int waitAtEndSeconds) {
        def polledIntegrationTask = workflowExecutionService.poll(taskName, workerId)
        def ackPolledIntegrationTask = workflowExecutionService.ackTaskReceived(polledIntegrationTask.taskId)
        polledIntegrationTask.status = Task.Status.COMPLETED
        if (outputParams) {
            outputParams.forEach { k, v ->
                polledIntegrationTask.outputData[k] = v
            }
        }
        workflowExecutionService.updateTask(polledIntegrationTask)
        return waitAtEndSecondsAndReturn(waitAtEndSeconds, polledIntegrationTask, ackPolledIntegrationTask)
    }

    static def void verifyPolledAndAcknowledgedTask(Map<String, String> expectedTaskInputParams, Tuple completedTaskAndAck) {
        assert completedTaskAndAck[0] : "The task polled cannot be null"
        def polledIntegrationTask = completedTaskAndAck[0] as Task
        def ackPolledIntegrationTask = completedTaskAndAck[1] as boolean
        assert polledIntegrationTask
        assert ackPolledIntegrationTask
        if (expectedTaskInputParams) {
            expectedTaskInputParams.forEach {
                k, v ->
                    assert polledIntegrationTask.inputData.containsKey(k)
                    assert polledIntegrationTask.inputData[k] == v
            }
        }
    }

}
