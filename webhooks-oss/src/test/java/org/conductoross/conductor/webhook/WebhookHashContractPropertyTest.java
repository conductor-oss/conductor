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
package org.conductoross.conductor.webhook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.conductoross.conductor.webhook.dao.memory.InMemoryWebhookTaskService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style contract test for the two hash sites that must agree:
 *
 * <ul>
 *   <li>{@link InMemoryWebhookTaskService} computes the <b>registration</b> hash when a {@code
 *       WAIT_FOR_WEBHOOK} task is enqueued.
 *   <li>{@link WebhookHashingService#computeJsonHash} computes the <b>inbound</b> hash when an
 *       event arrives, extracting values from the JSON body via JSONPath.
 * </ul>
 *
 * <p>If these two sites ever disagree on canonicalization, inbound events silently fail to match
 * registered tasks. The example-based tests in {@code InMemoryWebhookTaskServiceTest} pin one input
 * shape; this test generates random shapes per run.
 *
 * <p>Seed is {@link System#nanoTime()} per run; on failure the seed is printed via {@link
 * SeedReporter} so the case is reproducible by setting {@code -Dwebhook.hash.seed=<n>}.
 */
@ExtendWith(WebhookHashContractPropertyTest.SeedReporter.class)
class WebhookHashContractPropertyTest {

    private static final ObjectMapper OM = new ObjectMapperProvider().getObjectMapper();

    private static final long SEED = Long.getLong("webhook.hash.seed", System.nanoTime());

    /** One trial per repetition; 100 reps gives broad coverage without slowing the suite. */
    @RepeatedTest(100)
    void inboundHash_equals_registrationHash_whenBodyMatches() throws Exception {
        Random rng = new Random(SEED ^ Thread.currentThread().getId() ^ System.nanoTime());

        Trial trial = randomTrial(rng);

        InMemoryWebhookTaskService taskService = new InMemoryWebhookTaskService();
        TaskModel task = newTask(trial);
        taskService.put(task, trial.workflowVersion);

        String body = OM.writeValueAsString(trial.body);
        String prefix =
                trial.workflowName + ";" + trial.workflowVersion + ";" + trial.taskReferenceName;

        String inboundHash =
                new WebhookHashingService()
                        .computeJsonHash(
                                new StringBuilder(prefix),
                                new LinkedHashMap<>(trial.matches),
                                body,
                                Map.of());

        assertThat(inboundHash)
                .as("inbound hash should not be null when body satisfies matches; seed=%d", SEED)
                .isNotNull();
        assertThat(taskService.get(inboundHash))
                .as("inbound hash should locate the registered task; seed=%d trial=%s", SEED, trial)
                .containsExactly(task.getTaskId());
    }

    @RepeatedTest(100)
    void inboundHash_isNull_whenBodyDoesNotMatch() throws Exception {
        Random rng = new Random(SEED ^ 0xDEAD ^ Thread.currentThread().getId() ^ System.nanoTime());

        Trial trial = randomTrial(rng);

        // Mutate one match key's body value so it no longer equals the configured value.
        String keyToBreak = trial.matches.keySet().iterator().next();
        String field = jsonPathTopField(keyToBreak);
        Map<String, Object> brokenBody = new LinkedHashMap<>(trial.body);
        brokenBody.put(field, trial.matches.get(keyToBreak) + "-mutated");

        String body = OM.writeValueAsString(brokenBody);
        String prefix =
                trial.workflowName + ";" + trial.workflowVersion + ";" + trial.taskReferenceName;

        String inboundHash =
                new WebhookHashingService()
                        .computeJsonHash(
                                new StringBuilder(prefix),
                                new LinkedHashMap<>(trial.matches),
                                body,
                                Map.of());

        assertThat(inboundHash)
                .as(
                        "inbound hash should be null when body fails match; seed=%d trial=%s",
                        SEED, trial)
                .isNull();
    }

    private static Trial randomTrial(Random rng) {
        String wf = "wf_" + token(rng, 6);
        int version = 1 + rng.nextInt(9);
        String ref = "ref_" + token(rng, 6);

        int n = 1 + rng.nextInt(4);
        // LinkedHashMap so iteration order is random (not sorted) — the production code is what
        // sorts. This catches accidental reliance on input ordering.
        Map<String, Object> matches = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        List<String> fieldsUsed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String field;
            do {
                field = "f_" + token(rng, 4);
            } while (fieldsUsed.contains(field));
            fieldsUsed.add(field);

            String value = "v_" + token(rng, 6);
            matches.put("$." + field, value);
            body.put(field, value);
        }
        return new Trial(wf, version, ref, matches, body);
    }

    private static String token(Random rng, int len) {
        StringBuilder sb = new StringBuilder(len);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String jsonPathTopField(String path) {
        // Strip "$." prefix; we only generate single-level paths.
        return path.startsWith("$.") ? path.substring(2) : path;
    }

    private static TaskModel newTask(Trial trial) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-" + trial.workflowName);
        task.setWorkflowType(trial.workflowName);
        task.setReferenceTaskName(trial.taskReferenceName);
        task.setInputData(Map.of("matches", trial.matches));
        return task;
    }

    private record Trial(
            String workflowName,
            int workflowVersion,
            String taskReferenceName,
            Map<String, Object> matches,
            Map<String, Object> body) {}

    /** Surfaces the seed on failure so a failing run can be reproduced via -Dwebhook.hash.seed. */
    static class SeedReporter implements TestWatcher {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.err.println(
                    "WebhookHashContractPropertyTest failed; reproduce with -Dwebhook.hash.seed="
                            + SEED);
        }
    }
}
