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
package org.conductoross.conductor.scheduler.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.service.WorkflowService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Core scheduling service.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>CRUD operations on {@link WorkflowSchedule} objects
 *   <li>Calculating next-run times from 6-field cron expressions with timezone support
 *   <li>Polling for due schedules and triggering workflow executions via {@link WorkflowService}
 *   <li>Tracking execution state (POLLED → EXECUTED / FAILED)
 *   <li>Enforcing pause/resume, schedule bounds, and catchup mode
 *   <li>Pruning old execution history records
 * </ul>
 */
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    /** Execution records stuck in POLLED state longer than this are considered stale. */
    private static final long STALE_POLLED_THRESHOLD_MS = 5 * 60 * 1000L;

    /**
     * When a bounded schedule reaches its end time with no future slots, advance the pointer past
     * the end time by this offset to prevent repeated firing.
     */
    private static final long POINTER_OFFSET_PAST_END = 1L;

    private final SchedulerDAO schedulerDAO;
    private final WorkflowService workflowService;
    private final SchedulerProperties properties;

    private ScheduledExecutorService pollingExecutor;
    private ScheduledExecutorService jitterExecutor;

    public SchedulerService(
            SchedulerDAO schedulerDAO,
            WorkflowService workflowService,
            SchedulerProperties properties) {
        this.schedulerDAO = schedulerDAO;
        this.workflowService = workflowService;
        this.properties = properties;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Scheduler is disabled via conductor.scheduler.enabled=false");
            return;
        }
        validateProperties();
        initExecutors();
        pollingExecutor.scheduleWithFixedDelay(
                this::pollAndExecuteSchedules,
                0,
                properties.getPollingInterval(),
                TimeUnit.MILLISECONDS);
        if (properties.getJitterMaxMs() > 0) {
            log.info(
                    "Scheduler started with polling interval {}ms, jitter max {}ms",
                    properties.getPollingInterval(),
                    properties.getJitterMaxMs());
        } else {
            log.info(
                    "Scheduler started with polling interval {}ms",
                    properties.getPollingInterval());
        }
    }

    /** Package-visible for tests: creates executors without starting the polling loop. */
    void initExecutors() {
        pollingExecutor = Executors.newScheduledThreadPool(properties.getPollingThreadCount());
        if (properties.getJitterMaxMs() > 0) {
            jitterExecutor = Executors.newScheduledThreadPool(properties.getPollBatchSize());
        }
    }

    @PreDestroy
    public void stop() {
        shutdownExecutor(pollingExecutor);
        shutdownExecutor(jitterExecutor);
    }

    private void shutdownExecutor(ScheduledExecutorService executor) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    /** Creates or updates a schedule. Calculates the initial next-run time. */
    public WorkflowSchedule saveSchedule(WorkflowSchedule schedule) {
        validate(schedule);

        long now = System.currentTimeMillis();
        if (schedule.getCreateTime() == null) {
            schedule.setCreateTime(now);
        }
        schedule.setUpdatedTime(now);

        // Calculate and cache the next run time
        Long nextRun = computeNextRunTime(schedule, now);
        schedule.setNextRunTime(nextRun);

        schedulerDAO.updateSchedule(schedule);
        return schedule;
    }

    public WorkflowSchedule getSchedule(String name) {
        WorkflowSchedule schedule = schedulerDAO.findScheduleByName(name);
        if (schedule == null) {
            throw new NotFoundException("Schedule not found: " + name);
        }
        return schedule;
    }

    public List<WorkflowSchedule> getAllSchedules() {
        return schedulerDAO.getAllSchedules();
    }

    public List<WorkflowSchedule> getSchedulesForWorkflow(String workflowName) {
        return schedulerDAO.findAllSchedules(workflowName);
    }

    public void deleteSchedule(String name) {
        getSchedule(name); // throws NotFoundException if absent
        schedulerDAO.deleteWorkflowSchedule(name);
    }

    public void pauseSchedule(String name) {
        pauseSchedule(name, null);
    }

    public void pauseSchedule(String name, String reason) {
        WorkflowSchedule schedule = getSchedule(name);
        schedule.setPaused(true);
        schedule.setPausedReason(reason);
        schedule.setUpdatedTime(System.currentTimeMillis());
        schedulerDAO.updateSchedule(schedule);
    }

    public void resumeSchedule(String name) {
        WorkflowSchedule schedule = getSchedule(name);
        schedule.setPaused(false);
        schedule.setPausedReason(null);
        schedule.setUpdatedTime(System.currentTimeMillis());

        Long nextRun;
        if (schedule.isRunCatchupScheduleInstances()) {
            // Leave the stale nextRunTime intact — the poll loop will fire once per cycle
            // for each missed slot until it catches up to the current time.
            nextRun = schedulerDAO.getNextRunTimeInEpoch(name);
        } else {
            // Skip all missed slots and jump to the next future execution time.
            nextRun = computeNextRunTime(schedule, System.currentTimeMillis());
        }
        schedule.setNextRunTime(nextRun);
        schedulerDAO.updateSchedule(schedule);
    }

    // -------------------------------------------------------------------------
    // Execution history
    // -------------------------------------------------------------------------

    public List<WorkflowScheduleExecution> getExecutionHistory(String name, int limit) {
        return schedulerDAO.getExecutionRecords(name, limit);
    }

    /**
     * Cross-schedule execution search with in-memory filtering, sorting, and pagination.
     *
     * <p>Supports the query syntax used by the UI: {@code scheduleName IN (n1,n2) AND status IN
     * (EXECUTED) AND startTime>X AND startTime<X AND executionId='id'}.
     *
     * @param start zero-based page offset
     * @param size page size
     * @param sort field and direction, e.g. {@code startTime:DESC}
     * @param query structured filter string (may be null/blank for no filter)
     * @param freeText free-text search term ({@code *} means match all)
     * @return search result with {@code results} and {@code totalHits}
     */
    public SearchResult<WorkflowScheduleExecution> searchExecutions(
            int start, int size, String sort, String query, String freeText) {
        List<WorkflowScheduleExecution> all = schedulerDAO.getAllExecutionRecords(1000);

        if (query != null && !query.isBlank()) {
            all = applyQueryFilter(all, query);
        }
        if (freeText != null && !freeText.isBlank() && !"*".equals(freeText)) {
            all = applyFreeTextFilter(all, freeText);
        }

        applySort(all, sort);

        int totalHits = all.size();
        int fromIdx = Math.min(start, all.size());
        int toIdx = Math.min(start + size, all.size());
        return new SearchResult<>(totalHits, all.subList(fromIdx, toIdx));
    }

    /**
     * Schedule definitions search with in-memory filtering, sorting, and pagination.
     *
     * @param workflowName optional workflow name filter (exact match)
     * @param scheduleName optional schedule name filter (substring match)
     * @param paused optional paused-state filter
     * @param freeText free-text search on name ({@code *} means match all)
     * @param start zero-based page offset
     * @param size page size
     * @param sortOptions list of sort options, e.g. {@code ["name:ASC", "createTime:DESC"]}
     * @return search result with {@code results} and {@code totalHits}
     */
    public SearchResult<WorkflowSchedule> searchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        List<WorkflowSchedule> all =
                (workflowName != null && !workflowName.isBlank())
                        ? schedulerDAO.findAllSchedules(workflowName)
                        : schedulerDAO.getAllSchedules();

        // freeText name filter (used as schedule name prefix/substring by the UI)
        if (freeText != null && !freeText.isBlank() && !"*".equals(freeText)) {
            String lower = freeText.toLowerCase();
            all =
                    all.stream()
                            .filter(
                                    s ->
                                            s.getName() != null
                                                    && s.getName().toLowerCase().contains(lower))
                            .collect(Collectors.toList());
        }

        // explicit scheduleName filter (also substring)
        if (scheduleName != null && !scheduleName.isBlank()) {
            String lower = scheduleName.toLowerCase();
            all =
                    all.stream()
                            .filter(
                                    s ->
                                            s.getName() != null
                                                    && s.getName().toLowerCase().contains(lower))
                            .collect(Collectors.toList());
        }

        // paused filter
        if (paused != null) {
            boolean filterPaused = paused;
            all =
                    all.stream()
                            .filter(s -> s.isPaused() == filterPaused)
                            .collect(Collectors.toList());
        }

        // sort — apply the first sort option only
        if (sortOptions != null && !sortOptions.isEmpty()) {
            applyScheduleSort(all, sortOptions.get(0));
        } else {
            // default: newest first
            all.sort(
                    Comparator.comparingLong(
                                    (WorkflowSchedule s) ->
                                            s.getCreateTime() != null ? s.getCreateTime() : 0L)
                            .reversed());
        }

        int totalHits = all.size();
        int fromIdx = Math.min(start, all.size());
        int toIdx = Math.min(start + size, all.size());
        return new SearchResult<>(totalHits, all.subList(fromIdx, toIdx));
    }

    /**
     * Returns the next {@code limit} scheduled execution times (epoch millis) for a raw cron
     * expression, honouring optional start/end time bounds.
     *
     * @param cronExpression Spring 6-field cron expression
     * @param scheduleStartTime optional lower bound (epoch millis); ignored if null
     * @param scheduleEndTime optional upper bound (epoch millis); ignored if null
     * @param limit maximum number of times to return
     */
    public List<Long> getListOfNextSchedules(
            String cronExpression, Long scheduleStartTime, Long scheduleEndTime, int limit) {
        CronExpression cron = parseCron(cronExpression);
        ZonedDateTime cursor =
                scheduleStartTime != null
                        ? ZonedDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(scheduleStartTime), ZoneId.of("UTC"))
                        : ZonedDateTime.now(ZoneId.of("UTC"));

        List<Long> times = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ZonedDateTime next = cron.next(cursor);
            if (next == null) {
                break;
            }
            long epochMillis = next.toInstant().toEpochMilli();
            if (scheduleEndTime != null && epochMillis > scheduleEndTime) {
                break;
            }
            times.add(epochMillis);
            cursor = next;
        }
        return times;
    }

    private List<WorkflowScheduleExecution> applyQueryFilter(
            List<WorkflowScheduleExecution> records, String query) {
        return records.stream().filter(r -> matchesQuery(r, query)).collect(Collectors.toList());
    }

    private boolean matchesQuery(WorkflowScheduleExecution r, String query) {
        for (String clause : query.split("(?i)\\s+AND\\s+")) {
            clause = clause.trim();
            if (clause.isBlank()) {
                continue;
            }
            Matcher m;

            m = Pattern.compile("(?i)scheduleName\\s+IN\\s+\\(([^)]+)\\)").matcher(clause);
            if (m.find()) {
                Set<String> names =
                        Arrays.stream(m.group(1).split(","))
                                .map(String::trim)
                                .collect(Collectors.toSet());
                if (r.getScheduleName() == null || !names.contains(r.getScheduleName())) {
                    return false;
                }
                continue;
            }

            m = Pattern.compile("(?i)status\\s+IN\\s+\\(([^)]+)\\)").matcher(clause);
            if (m.find()) {
                Set<String> states =
                        Arrays.stream(m.group(1).split(","))
                                .map(String::trim)
                                .collect(Collectors.toSet());
                if (r.getState() == null || !states.contains(r.getState().name())) {
                    return false;
                }
                continue;
            }

            m = Pattern.compile("(?i)workflowType\\s+IN\\s+\\(([^)]+)\\)").matcher(clause);
            if (m.find()) {
                Set<String> types =
                        Arrays.stream(m.group(1).split(","))
                                .map(String::trim)
                                .collect(Collectors.toSet());
                if (r.getWorkflowName() == null || !types.contains(r.getWorkflowName())) {
                    return false;
                }
                continue;
            }

            m = Pattern.compile("(?i)startTime\\s*>\\s*(\\d+)").matcher(clause);
            if (m.find()) {
                long threshold = Long.parseLong(m.group(1));
                if (r.getExecutionTime() == null || r.getExecutionTime() <= threshold) {
                    return false;
                }
                continue;
            }

            m = Pattern.compile("(?i)startTime\\s*<\\s*(\\d+)").matcher(clause);
            if (m.find()) {
                long threshold = Long.parseLong(m.group(1));
                if (r.getExecutionTime() == null || r.getExecutionTime() >= threshold) {
                    return false;
                }
                continue;
            }

            m = Pattern.compile("(?i)executionId\\s*=\\s*'([^']+)'").matcher(clause);
            if (m.find()) {
                String id = m.group(1);
                if (!id.equals(r.getExecutionId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<WorkflowScheduleExecution> applyFreeTextFilter(
            List<WorkflowScheduleExecution> records, String freeText) {
        String lower = freeText.toLowerCase();
        return records.stream()
                .filter(
                        r ->
                                (r.getScheduleName() != null
                                                && r.getScheduleName()
                                                        .toLowerCase()
                                                        .contains(lower))
                                        || (r.getWorkflowId() != null
                                                && r.getWorkflowId().toLowerCase().contains(lower))
                                        || (r.getExecutionId() != null
                                                && r.getExecutionId()
                                                        .toLowerCase()
                                                        .contains(lower)))
                .collect(Collectors.toList());
    }

    private void applyScheduleSort(List<WorkflowSchedule> schedules, String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String[] parts = sort.split(":");
        String field = parts[0].trim().toLowerCase();
        boolean desc = parts.length < 2 || "desc".equalsIgnoreCase(parts[1].trim());

        Comparator<WorkflowSchedule> cmp;
        switch (field) {
            case "name":
                cmp =
                        Comparator.comparing(
                                s -> s.getName() != null ? s.getName() : "",
                                String.CASE_INSENSITIVE_ORDER);
                break;
            case "updatetime":
            case "updatedtime":
                cmp =
                        Comparator.comparingLong(
                                s -> s.getUpdatedTime() != null ? s.getUpdatedTime() : 0L);
                break;
            default: // createTime
                cmp =
                        Comparator.comparingLong(
                                s -> s.getCreateTime() != null ? s.getCreateTime() : 0L);
        }
        if (desc) {
            cmp = cmp.reversed();
        }
        schedules.sort(cmp);
    }

    private void applySort(List<WorkflowScheduleExecution> records, String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String[] parts = sort.split(":");
        String field = parts[0].trim().toLowerCase();
        boolean desc = parts.length < 2 || "desc".equalsIgnoreCase(parts[1].trim());

        Comparator<WorkflowScheduleExecution> cmp;
        switch (field) {
            case "scheduledtime":
                cmp =
                        Comparator.comparingLong(
                                r -> r.getScheduledTime() != null ? r.getScheduledTime() : 0L);
                break;
            default: // startTime, executionTime, or anything else
                cmp =
                        Comparator.comparingLong(
                                r -> r.getExecutionTime() != null ? r.getExecutionTime() : 0L);
        }
        if (desc) {
            cmp = cmp.reversed();
        }
        records.sort(cmp);
    }

    // -------------------------------------------------------------------------
    // Next-execution-time preview
    // -------------------------------------------------------------------------

    /**
     * Returns the next {@code count} scheduled execution times (epoch millis) for a schedule,
     * starting from now. Does not modify the schedule.
     */
    public List<Long> getNextExecutionTimes(String name, int count) {
        WorkflowSchedule schedule = getSchedule(name);
        List<Long> times = new ArrayList<>();
        ZonedDateTime cursor = ZonedDateTime.now(resolveZone(schedule));
        CronExpression cron = parseCron(schedule.getCronExpression());

        for (int i = 0; i < count; i++) {
            ZonedDateTime next = cron.next(cursor);
            if (next == null) {
                break;
            }
            long epochMillis = next.toInstant().toEpochMilli();
            if (schedule.getScheduleEndTime() != null
                    && epochMillis > schedule.getScheduleEndTime()) {
                break;
            }
            times.add(epochMillis);
            cursor = next;
        }
        return times;
    }

    // -------------------------------------------------------------------------
    // Core polling loop (package-visible for testing)
    // -------------------------------------------------------------------------

    void pollAndExecuteSchedules() {
        try {
            long now = System.currentTimeMillis();
            List<WorkflowSchedule> allSchedules = schedulerDAO.getAllSchedules();

            int processed = 0;
            for (WorkflowSchedule schedule : allSchedules) {
                if (processed >= properties.getPollBatchSize()) {
                    break;
                }
                Long scheduledTime = getNextRunIfDue(schedule, now);
                if (scheduledTime != null) {
                    handleSchedule(schedule, now, scheduledTime);
                    processed++;
                }
            }
        } catch (Exception e) {
            log.error("Error during scheduler polling cycle", e);
        }

        // Stale record cleanup in its own try/catch so a cleanup failure doesn't suppress
        // diagnostics about the dispatch loop and vice versa.
        try {
            cleanupStalePollRecords();
        } catch (Exception e) {
            log.error("Error during stale poll record cleanup", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the cached next-run time for the schedule if it is currently due, or {@code null} if
     * the schedule should not fire right now. Combines the due-check and the DAO read into a single
     * call so the caller doesn't need to fetch the value a second time.
     */
    private Long getNextRunIfDue(WorkflowSchedule schedule, long now) {
        if (schedule.isPaused()) {
            return null;
        }
        if (schedule.getScheduleStartTime() != null && now < schedule.getScheduleStartTime()) {
            return null;
        }
        if (schedule.getScheduleEndTime() != null && now > schedule.getScheduleEndTime()) {
            return null;
        }
        long nextRun = schedulerDAO.getNextRunTimeInEpoch(schedule.getName());
        return (nextRun >= 0 && now >= nextRun) ? nextRun : null;
    }

    private void handleSchedule(WorkflowSchedule schedule, long now, long scheduledTime) {
        String executionId = UUID.randomUUID().toString();

        // In catchup mode, advance to the next slot after the one we just fired for
        // (i.e. step through missed slots one per poll cycle). In normal mode, jump
        // to the next future slot so we don't re-fire stale times.
        Long nextRun;
        if (schedule.isRunCatchupScheduleInstances() && scheduledTime > 0) {
            nextRun = computeNextRunTime(schedule, scheduledTime);
        } else {
            nextRun = computeNextRunTime(schedule, now);
        }

        // Record POLLED state
        WorkflowScheduleExecution execution =
                createExecutionRecord(executionId, schedule, scheduledTime, now);
        schedulerDAO.saveExecutionRecord(execution);

        // Advance the next-run pointer immediately to prevent duplicate fires.
        advanceSchedulePointer(schedule, nextRun);

        // Trigger the workflow — optionally with a random jitter delay to spread concurrent
        // dispatch across a small time window and reduce DB/thread-pool contention.
        // Guard on jitterExecutor (not properties) so the two checks are structurally tied:
        // jitterExecutor is non-null iff jitter was enabled when initExecutors() ran.
        if (jitterExecutor != null) {
            long jitterMs =
                    ThreadLocalRandom.current().nextLong(0, properties.getJitterMaxMs() + 1);
            jitterExecutor.schedule(
                    () -> dispatchWorkflow(schedule, execution, scheduledTime),
                    jitterMs,
                    TimeUnit.MILLISECONDS);
        } else {
            dispatchWorkflow(schedule, execution, scheduledTime);
        }
    }

    private void dispatchWorkflow(
            WorkflowSchedule schedule, WorkflowScheduleExecution execution, long scheduledTime) {
        try {
            long dispatchTime = System.currentTimeMillis();
            // Build a per-dispatch copy of the StartWorkflowRequest with scheduler context
            // injected into the input. Never mutate the shared request on the schedule object —
            // the same instance may be reused across concurrent jitter lambdas or future caching.
            StartWorkflowRequest req =
                    buildDispatchRequest(
                            schedule.getStartWorkflowRequest(), scheduledTime, dispatchTime);
            req.setEvent("scheduler:" + schedule.getName());

            String workflowId = workflowService.startWorkflow(req);

            execution.setExecutionTime(dispatchTime);
            execution.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            execution.setWorkflowId(workflowId);
            log.debug("Schedule '{}' triggered workflow {}", schedule.getName(), workflowId);
        } catch (Exception e) {
            execution.setState(WorkflowScheduleExecution.ExecutionState.FAILED);
            execution.setReason(e.getMessage());
            log.error(
                    "Schedule '{}' failed to start workflow: {}",
                    schedule.getName(),
                    e.getMessage());
        } finally {
            schedulerDAO.saveExecutionRecord(execution);
            pruneExecutionHistory(schedule.getName());
        }
    }

    /**
     * Creates a per-dispatch copy of the given {@link StartWorkflowRequest} with scheduler context
     * keys injected into the input map. The original request is never mutated.
     *
     * @param original the template request from the schedule definition
     * @param scheduledTime the exact cron slot epoch millis
     * @param dispatchTime the actual wall-clock dispatch time (epoch millis)
     * @return a new request with augmented input; all other fields copied from the original
     */
    private StartWorkflowRequest buildDispatchRequest(
            StartWorkflowRequest original, long scheduledTime, long dispatchTime) {
        Map<String, Object> input = new HashMap<>();
        if (original.getInput() != null) {
            input.putAll(original.getInput());
        }
        // These keys match Orkes Conductor's scheduler for convergence compatibility.
        input.put("scheduledTime", scheduledTime);
        input.put("executionTime", dispatchTime);

        StartWorkflowRequest copy = new StartWorkflowRequest();
        copy.setName(original.getName());
        copy.setVersion(original.getVersion());
        copy.setCorrelationId(original.getCorrelationId());
        copy.setInput(input);
        copy.setTaskToDomain(original.getTaskToDomain());
        copy.setWorkflowDef(original.getWorkflowDef());
        copy.setExternalInputPayloadStoragePath(original.getExternalInputPayloadStoragePath());
        copy.setPriority(original.getPriority());
        copy.setCreatedBy(original.getCreatedBy());
        copy.setIdempotencyKey(original.getIdempotencyKey());
        copy.setIdempotencyStrategy(original.getIdempotencyStrategy());
        return copy;
    }

    /**
     * Advances the next-run pointer to prevent duplicate fires. If nextRun is null (no future slot
     * within the schedule's end window), pushes the pointer past the end time so isDue() won't
     * re-fire the last slot.
     *
     * @param schedule the schedule to update
     * @param nextRun the computed next run time, or null if no future slot exists
     */
    private void advanceSchedulePointer(WorkflowSchedule schedule, Long nextRun) {
        if (nextRun != null) {
            schedulerDAO.setNextRunTimeInEpoch(schedule.getName(), nextRun);
        } else if (schedule.getScheduleEndTime() != null) {
            schedulerDAO.setNextRunTimeInEpoch(
                    schedule.getName(), schedule.getScheduleEndTime() + POINTER_OFFSET_PAST_END);
        }
    }

    /**
     * Creates an execution record in POLLED state with the given parameters.
     *
     * @param executionId unique ID for this execution attempt
     * @param schedule the schedule being fired
     * @param scheduledTime the exact cron slot epoch millis
     * @param executionTime the current time when polling occurred
     * @return initialized execution record in POLLED state
     */
    private WorkflowScheduleExecution createExecutionRecord(
            String executionId, WorkflowSchedule schedule, long scheduledTime, long executionTime) {
        WorkflowScheduleExecution execution = new WorkflowScheduleExecution();
        execution.setExecutionId(executionId);
        execution.setScheduleName(schedule.getName());
        execution.setScheduledTime(scheduledTime);
        execution.setExecutionTime(executionTime);
        execution.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        execution.setZoneId(schedule.getZoneId());
        return execution;
    }

    /**
     * Applies the scheduleStartTime constraint by recomputing the next occurrence from the start
     * time if the computed next slot falls before the schedule's start window.
     *
     * @param cron the parsed cron expression
     * @param zone the schedule's timezone
     * @param schedule the schedule being evaluated
     * @param nextMillis the initially computed next run time
     * @return adjusted next run time respecting start boundary, or null if no valid slot exists
     */
    private Long applyStartTimeConstraint(
            CronExpression cron, ZoneId zone, WorkflowSchedule schedule, long nextMillis) {
        if (schedule.getScheduleStartTime() != null
                && nextMillis < schedule.getScheduleStartTime()) {
            ZonedDateTime startFrom =
                    ZonedDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(schedule.getScheduleStartTime()), zone);
            ZonedDateTime adjusted = cron.next(startFrom);
            if (adjusted == null) {
                return null;
            }
            return adjusted.toInstant().toEpochMilli();
        }
        return nextMillis;
    }

    /**
     * Computes the next run epoch millis for a schedule starting from {@code afterEpochMillis}.
     * Handles catchup mode: if catchup is disabled, skips to the first future run. Returns {@code
     * null} if no future run exists within the schedule's end time.
     */
    Long computeNextRunTime(WorkflowSchedule schedule, long afterEpochMillis) {
        CronExpression cron;
        try {
            cron = parseCron(schedule.getCronExpression());
        } catch (Exception e) {
            log.warn(
                    "Invalid cron expression '{}' for schedule '{}'",
                    schedule.getCronExpression(),
                    schedule.getName());
            return null;
        }

        ZoneId zone = resolveZone(schedule);
        ZonedDateTime from =
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(afterEpochMillis), zone);

        ZonedDateTime next = cron.next(from);
        if (next == null) {
            return null;
        }

        long nextMillis = next.toInstant().toEpochMilli();

        // Apply schedule time boundaries
        Long adjustedMillis = applyStartTimeConstraint(cron, zone, schedule, nextMillis);
        if (adjustedMillis == null) {
            return null;
        }
        nextMillis = adjustedMillis;

        // Respect scheduleEndTime
        if (schedule.getScheduleEndTime() != null && nextMillis > schedule.getScheduleEndTime()) {
            return null;
        }

        return nextMillis;
    }

    private void pruneExecutionHistory(String scheduleName) {
        int threshold = properties.getArchivalMaxRecordThreshold();
        int keep = properties.getArchivalMaxRecords();
        // Fetch one more than the threshold to cheaply detect when pruning is needed.
        List<WorkflowScheduleExecution> recent =
                schedulerDAO.getExecutionRecords(scheduleName, threshold + 1);
        if (recent.size() > threshold) {
            // Records are returned newest-first; remove everything beyond the keep limit.
            for (WorkflowScheduleExecution old : recent.subList(keep, recent.size())) {
                schedulerDAO.removeExecutionRecord(old.getExecutionId());
            }
        }
    }

    private void cleanupStalePollRecords() {
        // Fetch all POLLED records in a single query to avoid N+1 round trips.
        List<WorkflowScheduleExecution> pending = schedulerDAO.getPendingExecutionRecords();
        if (pending.isEmpty()) {
            return;
        }
        long staleThreshold = System.currentTimeMillis() - STALE_POLLED_THRESHOLD_MS;
        for (WorkflowScheduleExecution record : pending) {
            if (isRecordStale(record, staleThreshold)) {
                transitionStaleRecordToFailed(record);
            }
        }
    }

    /**
     * Checks if an execution record is stale (stuck in POLLED state past the threshold).
     *
     * @param record the execution record to check
     * @param staleThreshold epoch millis threshold for staleness
     * @return true if the record should be marked as failed
     */
    private boolean isRecordStale(WorkflowScheduleExecution record, long staleThreshold) {
        return record != null
                && record.getExecutionTime() != null
                && record.getExecutionTime() < staleThreshold;
    }

    /**
     * Transitions a stale POLLED record to FAILED state and persists it.
     *
     * @param record the stale execution record
     */
    private void transitionStaleRecordToFailed(WorkflowScheduleExecution record) {
        log.warn(
                "Transitioning stale POLLED execution {} for schedule '{}' to FAILED",
                record.getExecutionId(),
                record.getScheduleName());
        record.setState(WorkflowScheduleExecution.ExecutionState.FAILED);
        record.setReason("Stale POLLED record — server may have crashed mid-execution");
        schedulerDAO.saveExecutionRecord(record);
    }

    private ZoneId resolveZone(WorkflowSchedule schedule) {
        String zoneId = schedule.getZoneId();
        if (zoneId == null || zoneId.isBlank()) {
            zoneId = properties.getSchedulerTimeZone();
        }
        try {
            return ZoneId.of(zoneId);
        } catch (Exception e) {
            log.warn(
                    "Invalid zoneId '{}' for schedule '{}', falling back to UTC",
                    zoneId,
                    schedule.getName());
            return ZoneId.of("UTC");
        }
    }

    private CronExpression parseCron(String expression) {
        return CronExpression.parse(expression);
    }

    private void validate(WorkflowSchedule schedule) {
        validateName(schedule.getName());
        validateCronExpression(schedule.getCronExpression());
        validateWorkflowRequest(schedule.getStartWorkflowRequest());
        validateZoneId(schedule.getZoneId());
        validateTimeBounds(schedule.getScheduleStartTime(), schedule.getScheduleEndTime());
    }

    private void validateProperties() {
        if (properties.getArchivalMaxRecords() >= properties.getArchivalMaxRecordThreshold()) {
            throw new IllegalArgumentException(
                    "conductor.scheduler.archivalMaxRecords ("
                            + properties.getArchivalMaxRecords()
                            + ") must be less than archivalMaxRecordThreshold ("
                            + properties.getArchivalMaxRecordThreshold()
                            + ")");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Schedule name is required");
        }
    }

    private void validateCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        try {
            CronExpression.parse(cronExpression);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid cron expression '" + cronExpression + "': " + e.getMessage());
        }
    }

    private void validateWorkflowRequest(StartWorkflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("startWorkflowRequest is required");
        }
    }

    private void validateZoneId(String zoneId) {
        if (zoneId != null && !zoneId.isBlank()) {
            try {
                ZoneId.of(zoneId);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid zoneId '" + zoneId + "': " + e.getMessage());
            }
        }
    }

    private void validateTimeBounds(Long startTime, Long endTime) {
        if (startTime != null && endTime != null && endTime <= startTime) {
            throw new IllegalArgumentException("scheduleEndTime must be after scheduleStartTime");
        }
    }
}
