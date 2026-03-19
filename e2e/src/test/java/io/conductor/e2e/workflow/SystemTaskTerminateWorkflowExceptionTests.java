package io.conductor.e2e.workflow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that when a system task throws TerminateWorkflowException from execute(),
 * the workflow is actually terminated (FAILED) instead of getting stuck in RUNNING state.
 *
 * Regression test for the bug where TerminateWorkflowException escaped the system task
 * execution loop in OrkesWorkflowExecutor.decide() uncaught, causing the sweeper to
 * swallow it and leave the workflow stuck indefinitely.
 */
public class SystemTaskTerminateWorkflowExceptionTests {

    static WorkflowClient workflowClient;
    static MetadataClient metadataClient;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
    }

    /**
     * A JOIN task with an unknown evaluatorType throws TerminateWorkflowException from execute().
     * The workflow must be terminated (FAILED), not stuck in RUNNING.
     */
    @Test
    public void testJoinWithInvalidEvaluatorTypeTerminatesWorkflow() {
        String workflowName = "TWE_JOIN_" + RandomStringUtils.randomAlphanumeric(6).toUpperCase();

        WorkflowTask inlineTask = new WorkflowTask();
        inlineTask.setName("inline");
        inlineTask.setTaskReferenceName("inline_ref");
        inlineTask.setWorkflowTaskType(TaskType.INLINE);
        inlineTask.setInputParameters(Map.of(
                "evaluatorType", "graaljs",
                "expression", "(function () { return $.value1 + $.value2; })();",
                "value1", 1,
                "value2", 2
        ));

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setName("fork");
        forkTask.setTaskReferenceName("fork_ref");
        forkTask.setWorkflowTaskType(TaskType.FORK_JOIN);
        forkTask.setForkTasks(List.of(List.of(inlineTask)));
        forkTask.setJoinOn(List.of());

        WorkflowTask joinTask = new WorkflowTask();
        joinTask.setName("join");
        joinTask.setTaskReferenceName("join_ref");
        joinTask.setWorkflowTaskType(TaskType.JOIN);
        joinTask.setJoinOn(List.of());
        // Invalid evaluator type — OrkesJoin.execute() will throw TerminateWorkflowException
        joinTask.setEvaluatorType("doesnt_exist");
        joinTask.setExpression("xxx");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setVersion(1);
        workflowDef.setSchemaVersion(2);
        workflowDef.setTimeoutSeconds(0);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflowDef.setTasks(List.of(forkTask, joinTask));

        metadataClient.registerWorkflowDef(workflowDef);

        try {
            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(workflowName);
            request.setVersion(1);
            String workflowId = workflowClient.startWorkflow(request);
            assertNotNull(workflowId);

            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                Workflow workflow = workflowClient.getWorkflow(workflowId, false);
                assertEquals(
                        Workflow.WorkflowStatus.FAILED.name(),
                        workflow.getStatus().name(),
                        "Workflow should be FAILED, not stuck in RUNNING. " +
                        "TerminateWorkflowException from JOIN task must terminate the workflow."
                );
            });
        } finally {
            try { metadataClient.unregisterWorkflowDef(workflowName, 1); } catch (Exception ignored) {}
        }
    }
}
