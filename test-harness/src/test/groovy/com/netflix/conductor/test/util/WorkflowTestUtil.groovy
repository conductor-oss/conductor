/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED

/**
 * This is a helper class used to initialize task definitions required by the tests when loaded up.
 * The task definitions that are loaded up in {@link WorkflowTestUtil#taskDefinitions()} method as part of the post construct of the bean.
 * This class is intended to be used in the Spock integration tests and provides helper methods to:
 * <ul>
 *     <li> Terminate all the  running Workflows</li>
 *     <li> Get the persisted task definition based on the taskName</li>
 *     <li> pollAndFailTask </li>
 *     <li> pollAndCompleteTask </li>
 *     <li> verifyPolledAndAcknowledgedTask </li>
 * </ul>
 *
 * Usage: Inject this class in any Spock based specification:
 * <code>
 *      @Inject
 *     WorkflowTestUtil workflowTestUtil
 * </code>
 */
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

    /**
     * This function registers all the taskDefinitions required to enable spock based integration testing
     */
    @PostConstruct
    void taskDefinitions() {
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

        //This taskWithResponseTimeOut is required by the integration test which exercises the response time out scenarios
        TaskDef taskWithResponseTimeOut = new TaskDef()
        taskWithResponseTimeOut.name = "task_rt"
        taskWithResponseTimeOut.timeoutSeconds = 120
        taskWithResponseTimeOut.retryCount = RETRY_COUNT
        taskWithResponseTimeOut.retryDelaySeconds = 0
        taskWithResponseTimeOut.responseTimeoutSeconds = 10

        TaskDef optionalTask = new TaskDef()
        optionalTask.setName("task_optional")
        optionalTask.setTimeoutSeconds(5)
        optionalTask.setRetryCount(1)
        optionalTask.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY)
        optionalTask.setRetryDelaySeconds(0)

        TaskDef simpleSubWorkflowTask = new TaskDef()
        simpleSubWorkflowTask.setName('simple_task_in_sub_wf')
        simpleSubWorkflowTask.setRetryCount(0)

        TaskDef subWorkflowTask = new TaskDef()
        subWorkflowTask.setName('sub_workflow_task')
        subWorkflowTask.setRetryCount(1)
        subWorkflowTask.setResponseTimeoutSeconds(5)
        subWorkflowTask.setRetryDelaySeconds(0)

        TaskDef waitTimeOutTask = new TaskDef()
        waitTimeOutTask.name = 'waitTimeout'
        waitTimeOutTask.timeoutSeconds = 2
        waitTimeOutTask.retryCount = 1
        waitTimeOutTask.timeoutPolicy = TaskDef.TimeoutPolicy.RETRY
        waitTimeOutTask.retryDelaySeconds = 10

        TaskDef userTask = new TaskDef()
        userTask.setName("user_task")
        userTask.setTimeoutSeconds(20)
        userTask.setRetryCount(1)
        userTask.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY)
        userTask.setRetryDelaySeconds(10)


        TaskDef concurrentExecutionLimitedTask = new TaskDef()
        concurrentExecutionLimitedTask.name = "test_task_with_concurrency_limit"
        concurrentExecutionLimitedTask.concurrentExecLimit = 1

        TaskDef rateLimitedTask = new TaskDef()
        rateLimitedTask.name = 'test_task_with_rateLimits'
        rateLimitedTask.rateLimitFrequencyInSeconds = 10
        rateLimitedTask.rateLimitPerFrequency = 1

        TaskDef rateLimitedSimpleTask = new TaskDef()
        rateLimitedSimpleTask.name = 'test_simple_task_with_rateLimits'
        rateLimitedSimpleTask.rateLimitFrequencyInSeconds = 10
        rateLimitedSimpleTask.rateLimitPerFrequency = 1

        TaskDef eventTaskX = new TaskDef()
        eventTaskX.name = 'eventX'
        eventTaskX.timeoutSeconds = 1

        metadataService.registerTaskDef(
                [taskWithResponseTimeOut, optionalTask, simpleSubWorkflowTask,
                 subWorkflowTask, waitTimeOutTask, userTask, eventTaskX,
                 rateLimitedTask, rateLimitedSimpleTask, concurrentExecutionLimitedTask]
        )
    }

    /**
     * This is an helper method that enables each test feature to run from a clean state
     * This method is intended to be used in the cleanup() or cleanupSpec() method of any spock specification.
     * By invoking this method all the running workflows are terminated.
     * @throws Exception When unable to terminate any running workflow
     */
    void clearWorkflows() throws Exception {
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

        new FileOutputStream(this.getClass().getResource(TEMP_FILE_PATH).getPath()).close()
    }

    /**
     * A helper method to retrieve a task definition that is persisted
     * @param taskDefName The name of the task for which the task definition is requested
     * @return an Optional of the TaskDefinition
     */
    Optional<TaskDef> getPersistedTaskDefinition(String taskDefName) {
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

    /**
     * A helper methods that registers that workflows based on the paths of the json file representing a workflow definition
     * @param workflowJsonPaths a comma separated var ags of the paths of the workflow definitions
     */
    void registerWorkflows(String... workflowJsonPaths) {
        workflowJsonPaths.collect { JsonUtils.fromJson(it, WorkflowDef.class) }
                .forEach { metadataService.updateWorkflowDef(it) }
    }

    /**
     * A helper method intended to be used in the <tt>when:</tt> block of the spock test feature
     * This method is intended to be used to poll and update the task status as failed
     * It also provides a delay to return if needed after the task has been updated to failed
     * @param taskName name of the task that needs to be polled and failed
     * @param workerId name of the worker id using which a task is polled
     * @param failureReason the reason to fail the task that will added to the task update
     * @param outputParams An optional output parameters if available will be added to the task before updating to failed
     * @param waitAtEndSeconds an optional delay before the method returns, if the value is 0 skips the delay
     * @return A Tuple of polledTask and acknowledgement of the poll
     */
    Tuple pollAndFailTask(String taskName, String workerId, String failureReason, Map<String, Object> outputParams = null, int waitAtEndSeconds = 0) {
        def polledIntegrationTask = workflowExecutionService.poll(taskName, workerId)
        def ackPolledIntegrationTask = workflowExecutionService.ackTaskReceived(polledIntegrationTask.taskId)
        polledIntegrationTask.status = Task.Status.FAILED
        polledIntegrationTask.reasonForIncompletion = failureReason
        if (outputParams) {
            outputParams.forEach { k, v ->
                polledIntegrationTask.outputData[k] = v
            }
        }
        workflowExecutionService.updateTask(polledIntegrationTask)
        return waitAtEndSecondsAndReturn(waitAtEndSeconds, polledIntegrationTask, ackPolledIntegrationTask)
    }

    /**
     * A helper method to introduce delay and convert the polledIntegrationTask and ackPolledIntegrationTask
     * into a tuple. This method is intended to be used by pollAndFailTask and pollAndCompleteTask
     * @param waitAtEndSeconds The total seconds of delay before the method returns
     * @param polledIntegrationTask  instance of polled task
     * @param ackPolledIntegrationTask a acknowledgement of a poll
     * @return A Tuple of polledTask and acknowledgement of the poll
     */
    static Tuple waitAtEndSecondsAndReturn(int waitAtEndSeconds, Task polledIntegrationTask, boolean ackPolledIntegrationTask) {
        if (waitAtEndSeconds > 0) {
            Thread.sleep(waitAtEndSeconds * 1000)
        }
        return new Tuple(polledIntegrationTask, ackPolledIntegrationTask)
    }

    /**
     * A helper method intended to be used in the <tt>when:</tt> block of the spock test feature
     * This method is intended to be used to poll and update the task status as completed
     * It also provides a delay to return if needed after the task has been updated to completed
     * @param taskName name of the task that needs to be polled and completed
     * @param workerId name of the worker id using which a task is polled
     * @param outputParams An optional output parameters if available will be added to the task before updating to completed
     * @param waitAtEndSeconds waitAtEndSeconds an optional delay before the method returns, if the value is 0 skips the delay
     * @return A Tuple of polledTask and acknowledgement of the poll
     */
    Tuple pollAndCompleteTask(String taskName, String workerId, Map<String, Object> outputParams = null, int waitAtEndSeconds = 0) {
        def polledIntegrationTask = workflowExecutionService.poll(taskName, workerId)
        if (polledIntegrationTask == null) {
            return new Tuple(null, null)
        }
        def ackPolledIntegrationTask = workflowExecutionService.ackTaskReceived(polledIntegrationTask.taskId)
        polledIntegrationTask.status = COMPLETED
        if (outputParams) {
            outputParams.forEach { k, v ->
                polledIntegrationTask.outputData[k] = v
            }
        }
        workflowExecutionService.updateTask(polledIntegrationTask)
        return waitAtEndSecondsAndReturn(waitAtEndSeconds, polledIntegrationTask, ackPolledIntegrationTask)
    }

    Tuple pollAndCompleteLargePayloadTask(String taskName, String workerId, String outputPayloadPath) {
        def polledIntegrationTask = workflowExecutionService.poll(taskName, workerId)
        def ackPolledIntegrationTask = workflowExecutionService.ackTaskReceived(polledIntegrationTask.taskId)
        polledIntegrationTask.status = COMPLETED
        polledIntegrationTask.outputData = null
        polledIntegrationTask.externalOutputPayloadStoragePath = outputPayloadPath
        polledIntegrationTask.status = COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask)
        return new Tuple(polledIntegrationTask, ackPolledIntegrationTask)
    }

    /**
     * A helper method intended to be used in the <tt>then:</tt> block of the spock test feature, ideally intended to be called after either:
     * pollAndCompleteTask function or  pollAndFailTask function
     * @param completedTaskAndAck A Tuple of polledTask and acknowledgement of the poll
     * @param expectedTaskInputParams a map of input params that are verified against the polledTask that is part of the completedTaskAndAck tuple
     */
    static void verifyPolledAndAcknowledgedTask(Tuple completedTaskAndAck, Map<String, String> expectedTaskInputParams = null) {
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

    static void verifyPolledAndAcknowledgedLargePayloadTask(Tuple completedTaskAndAck) {
        assert completedTaskAndAck[0] : "The task polled cannot be null"
        def polledIntegrationTask = completedTaskAndAck[0] as Task
        def ackPolledIntegrationTask = completedTaskAndAck[1] as boolean
        assert polledIntegrationTask
        assert ackPolledIntegrationTask
    }
}
