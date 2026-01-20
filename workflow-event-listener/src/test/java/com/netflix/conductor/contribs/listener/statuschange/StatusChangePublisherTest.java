/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.contribs.listener.statuschange;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.contribs.listener.RestClientManager;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Unit tests for StatusChangePublisher to verify configurable workflow status publishing. */
public class StatusChangePublisherTest {

    private WorkflowModel workflow;
    private RestClientManager restClientManager;
    private ExecutionDAOFacade executionDAOFacade;

    @Before
    public void setUp() {
        workflow = new WorkflowModel();
        WorkflowDef def = new WorkflowDef();
        def.setName("test_workflow");
        def.setVersion(1);
        workflow.setWorkflowDefinition(def);
        workflow.setWorkflowId(UUID.randomUUID().toString());
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        restClientManager = Mockito.mock(RestClientManager.class);
        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
    }

    @Test
    public void testOnWorkflowStarted_WithRunningInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RUNNING", "COMPLETED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowStarted(workflow);

        // Give the consumer thread time to process
        TimeUnit.MILLISECONDS.sleep(100);

        // Verify that the workflow was enqueued (would be published)
        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowStarted_WithoutRunningInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("COMPLETED", "TERMINATED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowStarted(workflow);

        // Give time to ensure nothing is processed
        TimeUnit.MILLISECONDS.sleep(100);

        // Verify that no notification was posted
        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }

    @Test
    public void testOnWorkflowCompleted_WithCompletedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RUNNING", "COMPLETED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        publisher.onWorkflowCompleted(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowCompleted_WithoutCompletedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RUNNING", "TERMINATED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        publisher.onWorkflowCompleted(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }

    @Test
    public void testOnWorkflowTerminated_WithTerminatedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("COMPLETED", "TERMINATED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        publisher.onWorkflowTerminated(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowPaused_WithPausedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("PAUSED", "RESUMED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.PAUSED);
        publisher.onWorkflowPaused(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowResumed_WithResumedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("PAUSED", "RESUMED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.RUNNING);
        publisher.onWorkflowResumed(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowRestarted_WithRestartedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RESTARTED", "RETRIED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowRestarted(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowRetried_WithRetriedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RESTARTED", "RETRIED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowRetried(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowRerun_WithReranInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RERAN", "FINALIZED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowRerun(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowFinalized_WithFinalizedInSubscribedList() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("RERAN", "FINALIZED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        publisher.onWorkflowFinalized(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testWithNullSubscribedList() throws Exception {
        StatusChangePublisher publisher =
                new StatusChangePublisher(restClientManager, executionDAOFacade, null);

        // Try all workflow status methods
        publisher.onWorkflowStarted(workflow);
        publisher.onWorkflowCompleted(workflow);
        publisher.onWorkflowTerminated(workflow);
        publisher.onWorkflowPaused(workflow);
        publisher.onWorkflowResumed(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        // Verify that no notifications were posted when list is null
        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }

    @Test
    public void testWithEmptySubscribedList() throws Exception {
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, Collections.emptyList());

        // Try all workflow status methods
        publisher.onWorkflowStarted(workflow);
        publisher.onWorkflowCompleted(workflow);
        publisher.onWorkflowTerminated(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        // Verify that no notifications were posted when list is empty
        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }

    @Test
    public void testMultipleStatusesInSubscribedList() throws Exception {
        List<String> subscribedStatuses =
                Arrays.asList(
                        "RUNNING",
                        "COMPLETED",
                        "TERMINATED",
                        "PAUSED",
                        "RESUMED",
                        "RESTARTED",
                        "RETRIED",
                        "RERAN",
                        "FINALIZED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        // Test multiple status changes
        publisher.onWorkflowStarted(workflow);
        TimeUnit.MILLISECONDS.sleep(50);

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        publisher.onWorkflowCompleted(workflow);
        TimeUnit.MILLISECONDS.sleep(50);

        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        publisher.onWorkflowTerminated(workflow);
        TimeUnit.MILLISECONDS.sleep(50);

        // Verify that all three notifications were posted
        verify(restClientManager, timeout(1000).atLeast(3))
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowCompletedIfEnabled() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("COMPLETED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        publisher.onWorkflowCompletedIfEnabled(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testOnWorkflowTerminatedIfEnabled() throws Exception {
        List<String> subscribedStatuses = Arrays.asList("TERMINATED");
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        publisher.onWorkflowTerminatedIfEnabled(workflow);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.WORKFLOW),
                        anyString(),
                        eq(workflow.getWorkflowId()),
                        any());
    }

    @Test
    public void testCaseSensitivityOfStatusNames() throws Exception {
        // Test that status names must match exactly (case-sensitive)
        List<String> subscribedStatuses = Arrays.asList("running", "completed"); // lowercase
        StatusChangePublisher publisher =
                new StatusChangePublisher(
                        restClientManager, executionDAOFacade, subscribedStatuses);

        publisher.onWorkflowStarted(workflow); // checks for "RUNNING" (uppercase)

        TimeUnit.MILLISECONDS.sleep(100);

        // Should not publish because "running" != "RUNNING"
        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }
}
