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
package com.netflix.conductor.test.integration.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

/**
 * Integration test for the scheduler module. Boots an embedded Conductor server with SQLite
 * persistence (no Docker needed), creates a schedule that fires every second, and verifies workflow
 * executions are triggered on time.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = SchedulerTestApp.class)
@TestPropertySource(locations = "classpath:application-scheduler-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SchedulerIntegrationTest {

    @LocalServerPort private int port;

    private RestTemplate rest;
    private String baseUrl;

    private static final String SCHEDULE_NAME = "scheduler-integ-test";
    private static final String MULTI_CRON_SCHEDULE_NAME = "scheduler-integ-test-multicron";
    private static final String WORKFLOW_NAME = "scheduler_integ_test_wf";

    @Before
    public void setUp() {
        rest = new RestTemplate();
        baseUrl = "http://localhost:" + port;

        // Clean up any leftover schedules from previous runs
        try {
            rest.delete(baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME);
        } catch (Exception ignored) {
        }
        try {
            rest.delete(baseUrl + "/api/scheduler/schedules/" + MULTI_CRON_SCHEDULE_NAME);
        } catch (Exception ignored) {
        }

        // Register a simple NOOP workflow (PUT updates if it already exists)
        String workflowDef =
                "[{"
                        + "\"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "\"version\": 1,"
                        + "\"schemaVersion\": 2,"
                        + "\"tasks\": [{\"name\": \"noop\", \"taskReferenceName\": \"noop_ref\", \"type\": \"NOOP\"}]"
                        + "}]";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                baseUrl + "/api/metadata/workflow",
                HttpMethod.PUT,
                new HttpEntity<>(workflowDef, headers),
                Void.class);
    }

    @After
    public void tearDown() {
        // Clean up: delete schedules if they exist
        try {
            rest.delete(baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME);
        } catch (Exception ignored) {
        }
        try {
            rest.delete(baseUrl + "/api/scheduler/schedules/" + MULTI_CRON_SCHEDULE_NAME);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testSchedulerCreatesWorkflowExecutions() {
        // Create a schedule that fires every second
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"* * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": false,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> createResp =
                rest.exchange(
                        baseUrl + "/api/scheduler/schedules",
                        HttpMethod.POST,
                        new HttpEntity<>(scheduleJson, headers),
                        Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        assertEquals(SCHEDULE_NAME, createResp.getBody().get("name"));

        // Wait for at least 3 workflow executions (scheduler has a startup delay)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            int count = countWorkflowExecutions();
                            assertTrue("Expected at least 3 executions, got " + count, count >= 3);
                        });
    }

    @Test
    public void testPauseStopsScheduleFromFiring() {
        // Create a schedule and let it fire a few times
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"* * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": false,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                baseUrl + "/api/scheduler/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleJson, headers),
                Map.class);

        // Wait for at least 3 executions (scheduler has a startup delay)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(countWorkflowExecutions() >= 3));

        // Pause the schedule
        rest.exchange(
                baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME + "/pause",
                HttpMethod.PUT,
                null,
                Void.class);

        // Verify schedule is paused
        ResponseEntity<Map> schedule =
                rest.getForEntity(baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME, Map.class);
        assertTrue("Schedule should be paused", (Boolean) schedule.getBody().get("paused"));

        // Record current count, wait, and verify count didn't increase significantly
        int countAtPause = countWorkflowExecutions();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int countAfterPause = countWorkflowExecutions();

        // Allow at most 1 extra execution (could have been in-flight when we paused)
        assertTrue(
                "Paused schedule should not fire more workflows. Before: "
                        + countAtPause
                        + " After: "
                        + countAfterPause,
                countAfterPause <= countAtPause + 1);
    }

    @Test
    public void testResumeRestartsScheduleFiring() {
        // Create a paused schedule
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"* * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": true,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                baseUrl + "/api/scheduler/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleJson, headers),
                Map.class);

        // Wait a couple seconds — should not fire
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int countWhilePaused = countWorkflowExecutions();
        assertEquals("No workflows should start while paused", 0, countWhilePaused);

        // Resume the schedule
        rest.exchange(
                baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME + "/resume",
                HttpMethod.PUT,
                null,
                Void.class);

        // Wait for executions
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                assertTrue(
                                        "Resumed schedule should fire workflows",
                                        countWorkflowExecutions() >= 3));
    }

    @Test
    public void testDeleteRemovesSchedule() {
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"* * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": false,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                baseUrl + "/api/scheduler/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleJson, headers),
                Map.class);

        // Verify schedule exists
        ResponseEntity<List> schedulesList =
                rest.getForEntity(baseUrl + "/api/scheduler/schedules", List.class);
        assertEquals(1, schedulesList.getBody().size());

        // Delete it
        rest.delete(baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME);

        // Verify it's gone
        schedulesList = rest.getForEntity(baseUrl + "/api/scheduler/schedules", List.class);
        assertEquals(0, schedulesList.getBody().size());
    }

    @Test
    public void testSearchSchedules() {
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"0 * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": false,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                baseUrl + "/api/scheduler/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleJson, headers),
                Map.class);

        // Search by workflow name
        ResponseEntity<Map> searchResult =
                rest.getForEntity(
                        baseUrl + "/api/scheduler/schedules/search?workflowName=" + WORKFLOW_NAME,
                        Map.class);
        assertEquals(HttpStatus.OK, searchResult.getStatusCode());
        assertNotNull(searchResult.getBody());
        assertEquals(1, ((Number) searchResult.getBody().get("totalHits")).intValue());
    }

    @Test
    public void testNextFewSchedules() {
        ResponseEntity<List> result =
                rest.getForEntity(
                        baseUrl
                                + "/api/scheduler/nextFewSchedules?cronExpression="
                                + URLEncoder.encode("* * * * * *", StandardCharsets.UTF_8)
                                + "&limit=5",
                        List.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(5, result.getBody().size());
    }

    @Test
    public void testStaggeredMultiCronEachCronFiresIndependently() {
        // Create 3 cron expressions, each firing at a specific second ~4 seconds apart.
        // This proves each cron in the cronSchedules array fires independently.
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        int sec1 = (now.getSecond() + 4) % 60;
        int sec2 = (now.getSecond() + 8) % 60;
        int sec3 = (now.getSecond() + 12) % 60;

        String cron1 = sec1 + " * * * * *"; // fires once/minute at sec1
        String cron2 = sec2 + " * * * * *"; // fires once/minute at sec2
        String cron3 = sec3 + " * * * * *"; // fires once/minute at sec3

        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + MULTI_CRON_SCHEDULE_NAME
                        + "\","
                        + "\"cronSchedules\": ["
                        + "  {\"cronExpression\": \""
                        + cron1
                        + "\", \"zoneId\": \"UTC\"},"
                        + "  {\"cronExpression\": \""
                        + cron2
                        + "\", \"zoneId\": \"UTC\"},"
                        + "  {\"cronExpression\": \""
                        + cron3
                        + "\", \"zoneId\": \"UTC\"}"
                        + "],"
                        + "\"paused\": false,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> createResp =
                rest.exchange(
                        baseUrl + "/api/scheduler/schedules",
                        HttpMethod.POST,
                        new HttpEntity<>(scheduleJson, headers),
                        Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        assertEquals(MULTI_CRON_SCHEDULE_NAME, createResp.getBody().get("name"));

        // Verify cronSchedules is returned with 3 entries
        List<?> cronSchedules = (List<?>) createResp.getBody().get("cronSchedules");
        assertNotNull("cronSchedules should be in response", cronSchedules);
        assertEquals("Should have 3 cron schedules", 3, cronSchedules.size());

        // Verify queueMsgId is NOT in the response (internal field)
        assertFalse(
                "queueMsgId should not be in API response",
                createResp.getBody().containsKey("queueMsgId"));

        // Wait for all 3 staggered crons to fire (each ~4s apart, so ~13s total)
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            int count = countExecutions(MULTI_CRON_SCHEDULE_NAME);
                            assertTrue(
                                    "Expected at least 3 executions (one per cron), got " + count,
                                    count >= 3);
                        });
    }

    @Test
    public void testResponseDoesNotContainQueueMsgId() {
        // Create a simple schedule
        String scheduleJson =
                "{"
                        + "\"name\": \""
                        + SCHEDULE_NAME
                        + "\","
                        + "\"cronExpression\": \"0 * * * * *\","
                        + "\"zoneId\": \"UTC\","
                        + "\"paused\": true,"
                        + "\"startWorkflowRequest\": {"
                        + "  \"name\": \""
                        + WORKFLOW_NAME
                        + "\","
                        + "  \"version\": 1"
                        + "}"
                        + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Check POST response
        ResponseEntity<Map> createResp =
                rest.exchange(
                        baseUrl + "/api/scheduler/schedules",
                        HttpMethod.POST,
                        new HttpEntity<>(scheduleJson, headers),
                        Map.class);
        assertFalse(
                "POST response should not contain queueMsgId",
                createResp.getBody().containsKey("queueMsgId"));

        // Check GET response
        ResponseEntity<Map> getResp =
                rest.getForEntity(baseUrl + "/api/scheduler/schedules/" + SCHEDULE_NAME, Map.class);
        assertFalse(
                "GET response should not contain queueMsgId",
                getResp.getBody().containsKey("queueMsgId"));

        // Check list response
        ResponseEntity<List> listResp =
                rest.getForEntity(baseUrl + "/api/scheduler/schedules", List.class);
        for (Object item : listResp.getBody()) {
            Map<?, ?> schedule = (Map<?, ?>) item;
            assertFalse(
                    "List response items should not contain queueMsgId",
                    schedule.containsKey("queueMsgId"));
        }
    }

    private int countWorkflowExecutions() {
        return countExecutions(SCHEDULE_NAME);
    }

    private int countExecutions(String scheduleName) {
        try {
            ResponseEntity<Map> response =
                    rest.getForEntity(
                            baseUrl
                                    + "/api/scheduler/search/executions?freeText="
                                    + URLEncoder.encode(scheduleName, StandardCharsets.UTF_8)
                                    + "&size=1000",
                            Map.class);
            if (response.getBody() != null && response.getBody().containsKey("totalHits")) {
                return ((Number) response.getBody().get("totalHits")).intValue();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
