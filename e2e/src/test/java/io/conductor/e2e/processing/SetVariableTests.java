/*
 * Copyright 2026 Conductor Authors.
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
package io.conductor.e2e.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.common.model.WorkflowRun;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SetVariableTests {
    static WorkflowClient workflowClient;
    static MetadataClient metadataClient;
    static TaskClient taskClient;

    private static final String WORKFLOW_NAME = "set_variable_test";

    @BeforeAll
    public static void init() {
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        metadataClient = ApiUtil.METADATA_CLIENT;

        WorkflowTask setVariableTask = new WorkflowTask();
        setVariableTask.setTaskReferenceName("set_var_ref");
        setVariableTask.setWorkflowTaskType(TaskType.SET_VARIABLE);
        setVariableTask.setInputParameters(Map.of("vars", "${workflow.input.vars}"));
        setVariableTask.setName("set_variable");

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(WORKFLOW_NAME);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.getTasks().add(setVariableTask);

        metadataClient.updateWorkflowDefs(List.of(workflowDef));
    }

    @SneakyThrows
    @Test
    public void testAllFast() {
        ExecutorService es = Executors.newFixedThreadPool(20);
        Map<String, String> expectedValues = new ConcurrentHashMap<>();
        List<CompletableFuture<WorkflowRun>> futureRuns = new ArrayList<>();

        final int TOTAL_TO_CREATE = 2_000;
        final int LOG_EVERY_N = 250;

        // i think this is why there is 50k+ workflows in the e2e-aws cluster
        for (int i = 0; i < TOTAL_TO_CREATE; i++) {
            final int loopCounter = i;
            var future =
                    CompletableFuture.supplyAsync(
                            () -> {
                                StartWorkflowRequest startWorkflowRequest =
                                        new StartWorkflowRequest();
                                startWorkflowRequest.setName(WORKFLOW_NAME);
                                startWorkflowRequest.setVersion(1);
                                String uuid = UUID.randomUUID().toString();
                                startWorkflowRequest.setInput(Map.of("vars", uuid));
                                try {
                                    var cf =
                                            workflowClient.executeWorkflow(
                                                    startWorkflowRequest, null);
                                    var wf = cf.orTimeout(10, TimeUnit.SECONDS).join();
                                    expectedValues.put(wf.getWorkflowId(), uuid);
                                    if ((loopCounter + 1) % LOG_EVERY_N == 0) {
                                        log.info("Started {} workflows", loopCounter + 1);
                                    }
                                    return wf;
                                } catch (Exception e) {
                                    // fail the test if this happens
                                    throw new CompletionException(e);
                                }
                            },
                            es);
            futureRuns.add(future);
        }

        CompletableFuture.allOf(futureRuns.toArray(new CompletableFuture[futureRuns.size()]))
                .join();

        boolean hasFailures = false;
        var gotResults = 0;
        for (var cf : futureRuns) {
            gotResults++;
            if ((gotResults + 1) % LOG_EVERY_N == 0) {
                log.info("Completed {} workflows", gotResults + 1);
            }
            var workflowRun = cf.join();
            assertTrue(workflowRun.getStatus() == Workflow.WorkflowStatus.COMPLETED);

            String found = (String) workflowRun.getVariables().get("vars");
            var workflowId = workflowRun.getWorkflowId();
            String expected = expectedValues.get(workflowId);
            if (!expected.equals(found)) {
                System.out.println("Workflow " + workflowId + " has mismatched values");
                System.out.println("Expected " + expected);
                System.out.println("Found: " + found);
                System.out.println();
                hasFailures = true;
            }
        }

        assertFalse(hasFailures);
    }

    // this version runs much faster than before but still uses the async start and collection.
    @SneakyThrows
    @Test
    public void testAll() {
        ExecutorService es = Executors.newFixedThreadPool(20);
        List<Future<String>> futures = new ArrayList<>();
        Map<String, String> expectedValues = new ConcurrentHashMap<>();

        final int TOTAL_TO_CREATE = 2_000;
        final int LOG_EVERY_N = 250;

        for (int i = 0; i < TOTAL_TO_CREATE; i++) {
            final int loopCounter = i;
            Future<String> future =
                    es.submit(
                            () -> {
                                StartWorkflowRequest startWorkflowRequest =
                                        new StartWorkflowRequest();
                                startWorkflowRequest.setName(WORKFLOW_NAME);
                                startWorkflowRequest.setVersion(1);
                                String uuid = UUID.randomUUID().toString();
                                startWorkflowRequest.setInput(Map.of("vars", uuid));
                                String workflowId =
                                        workflowClient.startWorkflow(startWorkflowRequest);
                                if ((loopCounter + 1) % LOG_EVERY_N == 0) {
                                    log.info("Started {} workflows", loopCounter + 1);
                                }
                                expectedValues.put(workflowId, uuid);
                                return workflowId;
                            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }
        log.info("all requests sent to server - sleep briefly before collecting results");

        // Give 5 second to complete any pending workflows
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2) Collect results concurrently (still using HTTP API)
        AtomicBoolean hasFailures = new AtomicBoolean(false);
        AtomicInteger collected = new AtomicInteger(0);
        ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(es);

        for (String workflowId : expectedValues.keySet()) {
            ecs.submit(
                    () -> {
                        Workflow wf =
                                workflowClient.getWorkflow(workflowId, /*includeTasks*/ false);

                        int c = collected.incrementAndGet();
                        if (c % LOG_EVERY_N == 0) {
                            log.info("Collected {}", c);
                        }
                        assertTrue(wf.getStatus() == Workflow.WorkflowStatus.COMPLETED);

                        // Use execution variables, not definition defaults
                        String found = (String) wf.getVariables().get("vars");
                        String exp = expectedValues.get(workflowId);
                        if (!exp.equals(found)) {
                            System.out.println("Workflow " + workflowId + " has mismatched values");
                            System.out.println("Expected " + exp);
                            System.out.println("Found: " + found);
                            System.out.println();
                            hasFailures.set(true);
                        }
                        return null;
                    });
        }

        // 3) Wait for all collectors to finish
        for (int i = 0; i < TOTAL_TO_CREATE; i++) {
            ecs.take().get();
        }

        es.shutdownNow();
    }
}
