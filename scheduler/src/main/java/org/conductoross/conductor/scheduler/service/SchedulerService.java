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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.scheduler.config.SchedulerProperties;
import org.conductoross.conductor.scheduler.dao.SchedulerDAO;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
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
        WorkflowSchedule schedule = getSchedule(name);
        schedule.setPaused(true);
        schedule.setUpdatedTime(System.currentTimeMillis());
        schedulerDAO.updateSchedule(schedule);
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
                if (isDue(schedule, now)) {
                    handleSchedule(schedule, now);
                    processed++;
                }
            }

            // Prune stale POLLED records
            cleanupStalePollRecords();
        } catch (Exception e) {
            log.error("Error during scheduler polling cycle", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isDue(WorkflowSchedule schedule, long now) {
        if (schedule.isPaused()) {
            return false;
        }
        if (schedule.getScheduleStartTime() != null && now < schedule.getScheduleStartTime()) {
            return false;
        }
        if (schedule.getScheduleEndTime() != null && now > schedule.getScheduleEndTime()) {
            return false;
        }
        Long nextRun = schedulerDAO.getNextRunTimeInEpoch(schedule.getName());
        return nextRun >= 0 && now >= nextRun;
    }

    private void handleSchedule(WorkflowSchedule schedule, long now) {
        String executionId = UUID.randomUUID().toString();

        // Fetch the slot we are firing for before advancing the pointer.
        long scheduledTime = schedulerDAO.getNextRunTimeInEpoch(schedule.getName());

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
        // If nextRun is null (no future slot within the schedule's end window), push the
        // pointer past the end time so isDue() won't re-fire the last slot while waiting
        // for now to overtake scheduleEndTime.
        if (nextRun != null) {
            schedulerDAO.setNextRunTimeInEpoch(schedule.getName(), nextRun);
        } else if (schedule.getScheduleEndTime() != null) {
            schedulerDAO.setNextRunTimeInEpoch(
                    schedule.getName(), schedule.getScheduleEndTime() + 1);
        }

        // Trigger the workflow — optionally with a random jitter delay to spread concurrent
        // dispatch across a small time window and reduce DB/thread-pool contention.
        if (properties.getJitterMaxMs() > 0) {
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
            StartWorkflowRequest req = schedule.getStartWorkflowRequest();

            long dispatchTime = injectSchedulerContext(req, scheduledTime);

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
     * Injects scheduler context into the workflow input so workflows can use the scheduled time for
     * date-range calculations, idempotency keys, audit trails, etc. Preserves any existing input
     * keys. These keys match Orkes Conductor's scheduler for convergence compatibility.
     *
     * @param req the workflow request to modify
     * @param scheduledTime the exact cron slot epoch millis
     * @return the actual dispatch time (now)
     */
    private long injectSchedulerContext(StartWorkflowRequest req, long scheduledTime) {
        java.util.Map<String, Object> input = new java.util.HashMap<>();
        if (req.getInput() != null) {
            input.putAll(req.getInput());
        }
        input.put("scheduledTime", scheduledTime);
        long dispatchTime = System.currentTimeMillis();
        input.put("executionTime", dispatchTime);
        req.setInput(input);
        return dispatchTime;
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

        // Respect scheduleStartTime
        if (schedule.getScheduleStartTime() != null
                && nextMillis < schedule.getScheduleStartTime()) {
            ZonedDateTime startFrom =
                    ZonedDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(schedule.getScheduleStartTime()), zone);
            next = cron.next(startFrom);
            if (next == null) {
                return null;
            }
            nextMillis = next.toInstant().toEpochMilli();
        }

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
        List<String> pendingIds = schedulerDAO.getPendingExecutionRecordIds();
        if (pendingIds.isEmpty()) {
            return;
        }
        long staleThreshold = System.currentTimeMillis() - STALE_POLLED_THRESHOLD_MS;
        for (String id : pendingIds) {
            WorkflowScheduleExecution record = schedulerDAO.readExecutionRecord(id);
            if (record != null
                    && record.getExecutionTime() != null
                    && record.getExecutionTime() < staleThreshold) {
                log.warn(
                        "Transitioning stale POLLED execution {} for schedule '{}' to FAILED",
                        id,
                        record.getScheduleName());
                record.setState(WorkflowScheduleExecution.ExecutionState.FAILED);
                record.setReason("Stale POLLED record — server may have crashed mid-execution");
                schedulerDAO.saveExecutionRecord(record);
            }
        }
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
        if (schedule.getName() == null || schedule.getName().isBlank()) {
            throw new IllegalArgumentException("Schedule name is required");
        }
        if (schedule.getCronExpression() == null || schedule.getCronExpression().isBlank()) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        try {
            CronExpression.parse(schedule.getCronExpression());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid cron expression '"
                            + schedule.getCronExpression()
                            + "': "
                            + e.getMessage());
        }
        if (schedule.getStartWorkflowRequest() == null) {
            throw new IllegalArgumentException("startWorkflowRequest is required");
        }
        String zoneId = schedule.getZoneId();
        if (zoneId != null && !zoneId.isBlank()) {
            try {
                ZoneId.of(zoneId);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid zoneId '" + zoneId + "': " + e.getMessage());
            }
        }
        if (schedule.getScheduleStartTime() != null
                && schedule.getScheduleEndTime() != null
                && schedule.getScheduleEndTime() <= schedule.getScheduleStartTime()) {
            throw new IllegalArgumentException("scheduleEndTime must be after scheduleStartTime");
        }
    }
}
