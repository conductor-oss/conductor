/*
 * Copyright 2022 Orkes, Inc.
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
package io.conductor.e2e.workflow;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StateChangeEvent;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.DoWhile;
import com.netflix.conductor.sdk.workflow.def.tasks.ForkJoin;
import com.netflix.conductor.sdk.workflow.def.tasks.Http;
import com.netflix.conductor.sdk.workflow.def.tasks.SetVariable;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.def.tasks.SubWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.def.tasks.Task;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.netflix.conductor.common.metadata.tasks.Task.Status.CANCELED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.COMPLETED;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.IN_PROGRESS;
import static com.netflix.conductor.common.metadata.tasks.Task.Status.SCHEDULED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KitchensinkEndToEndTest {

    private static final String URL = "https://orkes-api-tester.orkesconductor.com/api";

    private static final String TASK_STATE_CHANGE_EVENT = "task_state_change_event_" + UUID.randomUUID();
    private static final String WF_STATE_CHANGE_EVENT = "workflow_state_change_event_" + UUID.randomUUID();

    private static WorkflowClient workflowClient;
    private static MetadataClient metadataClient;
    private static TaskClient taskClient;
    private static WorkflowExecutor workflowExecutor;

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;
        taskClient = ApiUtil.TASK_CLIENT;
        workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);
    }

    @SneakyThrows
    @Test
    public void testKitchenSink1() {
        verify(true);
    }

    @SneakyThrows
    @Test
    public void testKitchenSink2() {
        verify(false);
    }

    private void verify(boolean failWorkflow) {

        WorkflowDef workflowDef = createWorkflow(workflowExecutor, failWorkflow);
        for (WorkflowTask task : workflowDef.collectTasks()) {
            addOnStateChange(task);
        }
        workflowDef.setWorkflowStatusListenerEnabled(true);
        workflowDef.setWorkflowStatusListenerSink("conductor:" + WF_STATE_CHANGE_EVENT);

        WorkflowDef subWorkflowDef = createSubWorkflow(workflowExecutor, false).toWorkflowDef();

        metadataClient.updateWorkflowDefs(List.of(subWorkflowDef));
        metadataClient.updateWorkflowDefs(List.of(workflowDef));
        System.out.println("Registered workflows for test case failWorkflow: " + failWorkflow);

        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(workflowDef.getName());
        request.setInput(Map.of("fail", failWorkflow));
        String workflowId = workflowClient.startWorkflow(request);

        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        long startMs = System.currentTimeMillis();
        while (!workflow.getStatus().isTerminal()) {
            long duration = System.currentTimeMillis() - startMs;
            if (duration > 40_000) {
                break;
            }
            workflow.getTasks()
                .stream()
                .filter(task -> task.getStatus().equals(SCHEDULED) && task.getWorkflowTask().getType().equals("SIMPLE"))
                .forEach(t -> {
                    completeTask(workflowId, t.getReferenceTaskName(), t.getInputData());
                });
            try { Thread.sleep(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            workflow = workflowClient.getWorkflow(workflowId, true);
            workflow.getTasks().stream()
                .filter(t -> t.getReferenceTaskName().equals("wait_forever_ref") && t.getStatus().equals(IN_PROGRESS))
                .forEach(wait -> {
                    try {
                        taskClient.updateTaskSync(workflowId, "wait_forever_ref", TaskResult.Status.COMPLETED, Map.of());
                    } catch (ConductorClientException e) {
                        if (e.getStatus() != 404) {
                            throw e;
                        }
                    }
                });
        }

        await().pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                var wf = workflowClient.getWorkflow(workflowId, false);
                var tasks = wf.getTasks().stream().map(t -> t.getReferenceTaskName() + ":" + t.getStatus()).toList();
                assertTrue(wf.getStatus().isTerminal(), "workflow status is " + wf.getStatus() + " and tasks are: " + tasks);
            });

        workflow = workflowClient.getWorkflow(workflowId, true);
        verifyWorkflowEventTasks(workflow.getStatus());
        verifyTaskStatusChange(workflow);
    }

    @SuppressWarnings({"row", "unchecked"})
    private void verifyWorkflowEventTasks(Workflow.WorkflowStatus workflowStatus) {
        boolean apiOrchestrationEnabled = Boolean.parseBoolean(System.getenv("API_ORCHESTRATION_ENABLED"));
        Map<String, Integer> workflowStatusToEventCountMap = new HashMap<>();

        Map<String, List<String>> waitTaskToEventMap = new HashMap<>();
        Map<String, List<String>> simpleTaskToEventMap = new HashMap<>();
        Map<String, List<String>> polledTaskToEventMap = new HashMap<>();
        Map<String, List<String>> unknownTaskTypes = new HashMap<>();
        Map<String, String> refNameToStatus = new HashMap<>();
        System.out.println("Polling for workflow state in queue: " + WF_STATE_CHANGE_EVENT);
        System.out.println("Polling for task state in queue: " + TASK_STATE_CHANGE_EVENT);

        Clock clock = Clock.systemDefaultZone();
        Instant fiftySeconds = clock.instant().plusSeconds(50);
        int consecutiveNullPolls = 0;
        int maxConsecutiveNulls = 10;

        while (clock.instant().isBefore(fiftySeconds)) {
            var task = taskClient.pollTask(WF_STATE_CHANGE_EVENT, "test", null);
            if (task == null) {
                consecutiveNullPolls++;
                if (workflowStatusToEventCountMap.size() >= 2 && consecutiveNullPolls >= maxConsecutiveNulls) {
                    System.out.println("Captured all expected events. Stopping event polling.");
                    break;
                }
                try { Thread.sleep(100L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                continue;
            }
            consecutiveNullPolls = 0;
            String eventType = (String) task.getInputData().get("eventType");
            if (eventType.equalsIgnoreCase("workflow")) {
                String status = ((Map<String, String>) task.getInputData().get("workflow")).get("status");
                System.out.println("Workflow Status: " + status);
                Integer count = workflowStatusToEventCountMap.getOrDefault(status, 0);
                count++;
                workflowStatusToEventCountMap.put(status, count);
            } else {
                Map<String, Object> taskEvent = (Map<String, Object>) task.getInputData().get("task");
                String status = (String) taskEvent.get("status");
                String referenceName = (String) taskEvent.get("referenceTaskName");
                String taskType = (String) taskEvent.get("taskType");
                refNameToStatus.put(referenceName, status);

                if (taskType.equals("HTTP")) {
                    if (apiOrchestrationEnabled) {
                        List<String> events = simpleTaskToEventMap.getOrDefault(referenceName, new ArrayList<>());
                        events.add(status);
                        simpleTaskToEventMap.put(referenceName, events);
                    } else {
                        List<String> events = polledTaskToEventMap.getOrDefault(referenceName, new ArrayList<>());
                        events.add(status);
                        polledTaskToEventMap.put(referenceName, events);
                    }
                } else if (taskType.equals("simple") || taskType.equals("fail_or_not_fail")) {
                    List<String> events = simpleTaskToEventMap.getOrDefault(referenceName, new ArrayList<>());
                    events.add(status);
                    simpleTaskToEventMap.put(referenceName, events);
                } else if (taskType.equals("WAIT")) {
                    List<String> events = waitTaskToEventMap.getOrDefault(referenceName, new ArrayList<>());
                    events.add(status);
                    waitTaskToEventMap.put(referenceName, events);
                } else {
                    List<String> events = unknownTaskTypes.getOrDefault(referenceName, new ArrayList<>());
                    events.add(status);
                    unknownTaskTypes.put(referenceName, events);
                }
            }
        }

        System.out.println("workflowStatusToEventCountMap: " + workflowStatusToEventCountMap);
        System.out.println("polledTaskToEventMap: " + polledTaskToEventMap);
        System.out.println("simpleTaskToEventMap: " + simpleTaskToEventMap);
        System.out.println("waitTaskToEventMap: " + waitTaskToEventMap);

        assertTrue(workflowStatusToEventCountMap.size() >= 1,
                "Expected at least 1 workflow status event, found: " + workflowStatusToEventCountMap);

        if (workflowStatusToEventCountMap.size() < 2) {
            System.err.println("WARNING: Did not capture both RUNNING and terminal workflow status events.");
        }
        assertEquals(2, workflowStatusToEventCountMap.size(),
                "Expected workflow status events for RUNNING and " + workflowStatus +
                ". Found: " + workflowStatusToEventCountMap);
        assertEquals(1, workflowStatusToEventCountMap.get(workflowStatus.toString()),
                "Expected exactly 1 event for terminal status " + workflowStatus +
                ". Found: " + workflowStatusToEventCountMap);

        for (Map.Entry<String, List<String>> e : polledTaskToEventMap.entrySet()) {
            String taskRefName = e.getKey();
            List<String> statusList = e.getValue();
            assertEquals(3, statusList.size(), "Found " + taskRefName + "@" + statusList);
            assertEquals(Set.of(SCHEDULED.toString(), IN_PROGRESS.toString(), COMPLETED.toString()), new HashSet<>(statusList),
                "status mismatch for " + taskRefName);
        }

        for (Map.Entry<String, List<String>> e : waitTaskToEventMap.entrySet()) {
            String taskRefName = e.getKey();
            List<String> statusList = e.getValue();
            boolean isCancelled = refNameToStatus.get(taskRefName).equals(CANCELED.toString());
            if (isCancelled) {
                assertEquals(2, statusList.size(), "status mismatch for " + taskRefName + "@" + statusList);
                assertEquals(Set.of(IN_PROGRESS.toString(), CANCELED.toString()), new HashSet<>(statusList),
                    "status mismatch for " + taskRefName);
            } else {
                assertEquals(2, statusList.size(), "missing status for " + taskRefName + ", status: " + statusList);
                assertEquals(Set.of(IN_PROGRESS.toString(), COMPLETED.toString()), new HashSet<>(statusList),
                    "status mismatch for " + taskRefName);
            }
        }

        for (Map.Entry<String, List<String>> e : simpleTaskToEventMap.entrySet()) {
            String taskRefName = e.getKey();
            List<String> statusList = e.getValue();
            assertEquals(2, statusList.size());
            if (taskRefName.equals("fail_or_not_fail_ref") && !workflowStatus.isSuccessful()) {
                assertEquals(Set.of(SCHEDULED.toString(), CANCELED.toString()), new HashSet<>(statusList),
                    "status mismatch for " + taskRefName);
            } else {
                assertEquals(Set.of(SCHEDULED.toString(), COMPLETED.toString()), new HashSet<>(statusList),
                    "status mismatch for " + taskRefName);
            }
        }
    }

    @SuppressWarnings({"row", "unchecked"})
    private void verifyTaskStatusChange(Workflow workflow) {
        boolean apiOrchestrationEnabled = Boolean.parseBoolean(System.getenv("API_ORCHESTRATION_ENABLED"));
        int emptyPollsAllowed = 50;
        Map<String, List<String>> referenceNameToStatusMap = new HashMap<>();

        while (emptyPollsAllowed > 0) {
            var task = taskClient.pollTask(TASK_STATE_CHANGE_EVENT, "test", null);
            if (task == null) {
                try { Thread.sleep(100L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                emptyPollsAllowed--;
                continue;
            }
            emptyPollsAllowed = 50;
            String referenceName = task.getReferenceTaskName().substring(0, task.getReferenceTaskName().indexOf("_on"));
            int indx = task.getReferenceTaskName().indexOf("_on");
            String event = task.getReferenceTaskName().substring(indx + 1, task.getReferenceTaskName().indexOf("_", indx + 1));
            List<String> events = referenceNameToStatusMap.getOrDefault(referenceName, new ArrayList<>());
            events.add(event);
            referenceNameToStatusMap.put(referenceName, events);
        }

        System.out.println("Task Status Updates, apiOrchestrationEnabled? " + apiOrchestrationEnabled);
        System.out.println(referenceNameToStatusMap);
        System.out.println("---");
        for (Map.Entry<String, List<String>> e : referenceNameToStatusMap.entrySet()) {
            String taskRefName = e.getKey();
            List<String> statusList = e.getValue();
            boolean isSuccessful = workflow.getTaskByRefName(taskRefName).getStatus().isSuccessful();
            if ((taskRefName.contains("http") && !apiOrchestrationEnabled)) {
                assertEquals(List.of("onScheduled", "onStart", "onSuccess"), statusList, "status mismatch for " + taskRefName);
            } else if (taskRefName.contains("sub_wf_fail_ref") || taskRefName.contains("sub_wf_loop_ref")) {
                Set<String> expectedSuccess = Set.of("onScheduled", "onStart", "onSuccess");
                Set<String> expectedFailed = Set.of("onScheduled", "onStart", "onFailed");
                if (isSuccessful) {
                    assertEquals(expectedSuccess, new HashSet<>(statusList), "status mismatch for " + taskRefName);
                } else {
                    assertEquals(expectedFailed, new HashSet<>(statusList), "status mismatch for " + taskRefName);
                }
            } else if (taskRefName.contains("wait")) {
                assertEquals(2, statusList.size());
                if (isSuccessful) {
                    assertEquals(Set.of("onStart", "onSuccess"), new HashSet<>(statusList), "status mismatch for " + taskRefName);
                } else {
                    assertEquals(Set.of("onStart", "onCancelled"), new HashSet<>(statusList), "status mismatch for " + taskRefName);
                }
            } else {
                if (isSuccessful) {
                    assertEquals(2, statusList.size(), "status mismatch for " + taskRefName + "@" + statusList);
                    assertEquals(Set.of("onScheduled", "onSuccess"), new HashSet<>(statusList), "status mismatch for " + taskRefName);
                } else {
                    assertEquals(2, statusList.size(), "status mismatch for " + taskRefName + "@" + statusList);
                    assertEquals(Set.of("onScheduled", "onCancelled"), new HashSet<>(statusList), "status mismatch for " + taskRefName);
                }
            }
        }
    }

    private void completeTask(String workflowId, String refName, Map<String, Object> input) {
        System.out.println("Completing " + refName);
        int value = new Random().nextInt(5, 100);
        boolean fail = Boolean.parseBoolean("" + input.getOrDefault("fail", false));
        TaskResult.Status status = TaskResult.Status.COMPLETED;
        if (fail) {
            try { Thread.sleep(500L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = TaskResult.Status.FAILED_WITH_TERMINAL_ERROR;
        }
        try {
            taskClient.updateTaskSync(workflowId, refName, status, Map.of("result", value, "updatedBY", "completeTaskMethod"));
        } catch (ConductorClientException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
    }

    private static WorkflowDef createWorkflow(WorkflowExecutor workflowExecutor, boolean fail) {
        ConductorWorkflow<?> workflow = new ConductorWorkflow<>(workflowExecutor);
        workflow.setName("ks_e2e");
        workflow.setDescription("kitchensink_e2e");
        workflow.setVersion(1);

        workflow.add(new Http("http_ref_1").url(URL));
        workflow.add(new SetVariable("set_var_ref").input(Map.of("name", "Orkes1", "id", 1)));

        Http http2 = new Http("http_ref_fork_1").url(URL);
        Http http3 = new Http("http_ref_fork_2").url(URL);
        var forkedTasks = new Task[2][2];
        forkedTasks[0] = new Task[]{http2};
        forkedTasks[1] = new Task[]{http3};
        workflow.add(new ForkJoin("fork", forkedTasks).joinOn("http_ref_fork_1", "http_ref_fork_2"));

        workflow.add(new SimpleTask("simple", "simple_ref"));
        Switch sw = new Switch("switch", "(function () {\n" +
            "    if ($.result > 10) {\n" +
            "      return \"more_than_10\";      \n" +
            "    }\n" +
            "    return \"less_than_10\";\n" +
            "  }())", true);
        sw.input("result", "${simple_ref.output.result}");
        sw.switchCase("more_than_10", new Http("http_ref_more_than_10").url(URL));
        sw.switchCase("less_than_10", new Http("http_ref_less_than_10").url(URL));
        workflow.add(sw);

        var loopTasks = new Task[]{
            new SimpleTask("simple", "simple_ref_loop").input("iteration", "${simple_ref_loop.iteration}"),
            new SetVariable("set_var_ref_loop").input(Map.of("name", "Orkes2", "id", 2)),
            new SubWorkflow("sub_wf_loop_ref", "ks_e2e_sub", 1)
        };
        DoWhile loop = new DoWhile("while_ref", 1, loopTasks);
        workflow.add(loop);

        workflow.add(new Wait("wait_ref", Duration.ofSeconds(1)));
        workflow.add(new Wait("wait_forever_ref"));

        var forkedTasks2 = new Task[3][];
        var wait = new Wait("wait_can_be_cancelled", Duration.ofSeconds(2));
        forkedTasks2[0] = new Task[]{wait};
        forkedTasks2[1] = new Task[]{new SimpleTask("fail_or_not_fail", "fail_or_not_fail_ref").input("fail", "${workflow.input.fail}")};
        forkedTasks2[2] = new Task[]{new SubWorkflow("sub_wf_fail_ref", createSubWorkflow(workflowExecutor, fail))};

        workflow.add(new ForkJoin("fork2", forkedTasks2).joinOn("wait_can_be_cancelled", "fail_or_not_fail_ref"));
        return workflow.toWorkflowDef();
    }

    @SuppressWarnings("rawtypes")
    private static ConductorWorkflow createSubWorkflow(WorkflowExecutor workflowExecutor, boolean fail) {
        ConductorWorkflow<?> workflow = new ConductorWorkflow<>(workflowExecutor);
        workflow.setName("ks_e2e_sub");
        workflow.setDescription("kitchensink e2e sub workflow");
        workflow.setVersion(1);

        workflow.add(new Wait("sub_wf_wait_ref", Duration.ofMillis(500)));
        workflow.add(new Http("http_ref_1").url(URL + (fail ? "/xxx" : "")));
        return workflow;
    }

    private void addOnStateChange(WorkflowTask wfTask) {
        if (!wfTask.getType().equals("HTTP") && !wfTask.getType().equals("SIMPLE")
                && !wfTask.getType().equals("WAIT") && !wfTask.getType().equals("SUB_WORKFLOW")) {
            return;
        }
        Set<String> stateNames = Set.of("onScheduled", "onStart", "onSuccess", "onFailed", "onCancelled");
        StateChangeEvent event = new StateChangeEvent();
        event.setType(TASK_STATE_CHANGE_EVENT);
        event.setPayload(Map.of(
            "status", "${" + wfTask.getTaskReferenceName() + ".status}",
            "referenceName", "${" + wfTask.getTaskReferenceName() + ".referenceTaskName}",
            "xyz", "${" + wfTask.getTaskReferenceName() + ".referenceTaskName}"
        ));
        Map<String, List<StateChangeEvent>> eventMaps = new HashMap<>();
        stateNames.forEach(stateName -> eventMaps.put(stateName, List.of(event)));
        wfTask.setOnStateChange(eventMaps);
    }
}
