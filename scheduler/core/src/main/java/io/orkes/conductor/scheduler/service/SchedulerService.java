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
package io.orkes.conductor.scheduler.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.context.SecurityContextHolder;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.health.RedisMonitor;
import io.orkes.conductor.scheduler.config.SchedulerConditions;
import io.orkes.conductor.scheduler.config.SchedulerProperties;
import io.orkes.conductor.scheduler.model.CronSchedule;
import io.orkes.conductor.scheduler.model.NextScheduleResult;
import io.orkes.conductor.scheduler.model.WorkflowSchedule;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Conditional(SchedulerConditions.class)
public class SchedulerService extends LifecycleAwareComponent {

    public static final String LOCK_CLEANUP_KEY = "SCHEDULER_ARCHIVAL_RECORDS_CLEANUP";
    public final ZoneId zoneId;
    private final Lock conductorLock;

    private final AtomicInteger counter = new AtomicInteger(0);

    public static final String CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME = "conductor_system_scheduler";
    public static final String CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME =
            "conductor_system_scheduler_archival";

    private final SchedulerArchivalDAO schedulerArchivalDAO;
    protected final SchedulerDAO schedulerDAO;
    private final WorkflowService workflowService;
    protected final QueueDAO queueDAO;
    private final Optional<RedisMonitor> redisMaintenanceDAO;
    protected final SchedulerProperties properties;
    private final SchedulerTimeProvider schedulerTimeProvider;
    private final ObjectMapper objectMapper;

    protected AtomicInteger scheduleWfPollerBackoff = new AtomicInteger(0);
    protected AtomicBoolean pauseScheduler = new AtomicBoolean(false);
    protected AtomicInteger backoffFactor = new AtomicInteger(0);
    protected AtomicInteger archivalsSinceLastIndex = new AtomicInteger(0);

    /**
     * Tracks recent schedule processing throughput for dynamic jitter calculation. Increases
     * additively with each non-empty batch; decays multiplicatively on empty polls (AIMD). Capped
     * to prevent integer overflow in the jitter formula.
     */
    @VisibleForTesting final AtomicInteger burstEstimate = new AtomicInteger(0);

    private final ScheduledExecutorService sesArchival;
    private final ScheduledExecutorService sesMain;

    public SchedulerService(
            SchedulerArchivalDAO schedulerArchivalDAO,
            SchedulerDAO schedulerDAO,
            WorkflowService workflowService,
            QueueDAO queueDAO,
            SchedulerServiceExecutor schedulerServiceExecutor,
            Optional<RedisMonitor> redisMaintenanceDAO,
            SchedulerProperties properties,
            SchedulerTimeProvider schedulerTimeProvider,
            Lock lock,
            ObjectMapper objectMapper) {
        this.schedulerArchivalDAO = schedulerArchivalDAO;
        this.schedulerDAO = schedulerDAO;
        this.workflowService = workflowService;
        this.queueDAO = queueDAO;
        this.redisMaintenanceDAO = redisMaintenanceDAO;
        this.properties = properties;
        this.zoneId = ZoneId.of(properties.getSchedulerTimeZone());
        this.schedulerTimeProvider = schedulerTimeProvider;
        this.conductorLock = lock;
        this.objectMapper = objectMapper;
        this.sesArchival =
                schedulerServiceExecutor.getExecutorServiceArchivalQueuePoll(
                        properties.getArchivalThreadCount());
        this.sesMain =
                schedulerServiceExecutor.getExecutorServiceMainQueuePoll(
                        properties.getPollingThreadCount());
        this.startPolling();
    }

    // -------------------------------------------------------------------------
    // Enterprise hook methods — override in EnterpriseSchedulerService
    // -------------------------------------------------------------------------

    protected void setRequestOrgId(String orgId) {
        // no-op in OSS — enterprise overrides to set OrkesRequestContext
    }

    protected void clearRequestOrgId() {
        // no-op in OSS — enterprise overrides to clear OrkesRequestContext
    }

    protected String getCurrentUserId() {
        return "";
    }

    protected void checkExecutionPermission(WorkflowScheduleModel schedule) {
        // no-op in OSS — override in enterprise for RBAC enforcement
    }

    protected void checkScheduleLimit(WorkflowSchedule workflowSchedule) {
        // no-op in OSS — override in enterprise for quota enforcement
    }

    protected void handlePermissionViolation(SchedulerException e) {
        log.error(
                "Permission violation for schedule '{}': {}", e.getScheduleName(), e.getMessage());
    }

    protected SearchResult<WorkflowScheduleModel> doSearchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        return schedulerDAO.searchSchedules(
                workflowName, scheduleName, paused, freeText, start, size, sortOptions);
    }

    // -------------------------------------------------------------------------
    // Core scheduling logic
    // -------------------------------------------------------------------------

    private void runSchedulerMain() {
        try {
            if (checkBackoff()) return;
            if (!isRedisHealthy()) return;
            if (pauseScheduler.get()) {
                if (counter.incrementAndGet() >= 50) {
                    counter.set(0);
                }
                return;
            }
            final boolean[] handled = {false};
            this.pollAndExecute(
                    CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME,
                    input -> {
                        handled[0] = true;
                        int maxBurst = properties.getMaxScheduleJitterMs() * 10 / 3 + 1;
                        burstEstimate.updateAndGet(v -> Math.min(v + input.size(), maxBurst));
                        try {
                            handleSchedules(input);
                        } catch (Exception e) {
                            log.error("Caught exception handling schedules: " + e.getMessage(), e);
                            if (e instanceof SchedulerException) {
                                handlePermissionViolation((SchedulerException) e);
                            } else {
                                Monitors.getCounter("schedulerHandlerError").increment();
                            }
                        }
                    },
                    this.properties.getPollBatchSize());
            if (!handled[0]) {
                // Queue was empty — decay burst estimate (multiplicative decrease)
                burstEstimate.updateAndGet(v -> v / 2);
            }
        } catch (Exception e) {
            log.error(
                    "Caught exception polling "
                            + CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME
                            + " "
                            + e.getMessage(),
                    e);
            Monitors.getCounter("schedulerExecutionError").increment();
        }
    }

    private void startPolling() {
        for (int i = 0; i < properties.getPollingThreadCount(); i++) {
            sesMain.scheduleWithFixedDelay(
                    this::runSchedulerMain,
                    properties.getInitialDelayMs(),
                    properties.getPollingInterval(),
                    TimeUnit.MILLISECONDS);
        }
        log.info(
                "Started listening for task: {} in queue: {}",
                CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME,
                CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
        for (int i = 0; i < properties.getArchivalThreadCount(); i++) {
            sesArchival.scheduleWithFixedDelay(
                    () -> {
                        if (readyForMaintenance()) {
                            runArchivalTableMaintenance();
                        }
                        try {
                            this.pollAndExecute(
                                    CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME,
                                    input -> {
                                        try {
                                            handleArchival(input);
                                        } catch (Exception e) {
                                            log.error(
                                                    "Caught exception handling archival: "
                                                            + e.getMessage(),
                                                    e);
                                        }
                                    },
                                    this.properties.getArchivalPollBatchSize());
                        } catch (Exception e) {
                            log.error(
                                    "Caught exception polling "
                                            + CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME
                                            + " "
                                            + e.getMessage(),
                                    e);
                            Monitors.getCounter("schedulerArchivalError").increment();
                        }
                    },
                    5000,
                    properties.getPollingInterval(),
                    TimeUnit.MILLISECONDS);
        }
        log.info(
                "Started listening for archival records : {} in queue: {}",
                CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME,
                CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME);
    }

    private boolean readyForMaintenance() {
        return archivalsSinceLastIndex.get()
                > properties.getArchivalMaintenanceIntervalRecordCount();
    }

    private void runArchivalTableMaintenance() {
        try {
            log.debug("Running archival index maintenance");
            boolean lockAcquired =
                    conductorLock.acquireLock(
                            LOCK_CLEANUP_KEY,
                            properties.getArchivalMaintenanceLockSeconds(),
                            properties.getArchivalMaintenanceLockSeconds(),
                            TimeUnit.SECONDS);
            if (lockAcquired) {
                try {
                    log.debug(
                            "Lock acquired, performing maintenance on scheduler archival records");
                    schedulerArchivalDAO.cleanupOldRecords(
                            properties.getArchivalMaxRecords(),
                            properties.getArchivalMaxRecordThreshold());
                } finally {
                    conductorLock.releaseLock(LOCK_CLEANUP_KEY);
                }
            } else {
                log.warn(
                        "Unable to acquire lock, maintenance is potentially being carried out by another instance");
            }
        } catch (Exception e) {
            log.error("Error running index maintenance", e);
            Monitors.error(this.getClass().getSimpleName(), "runArchivalTableMaintenance");
        } finally {
            archivalsSinceLastIndex.set(0);
        }
    }

    private boolean checkBackoff() {
        int backoffValue =
                scheduleWfPollerBackoff.accumulateAndGet(
                        1,
                        (left, right) -> {
                            if (left == 0) {
                                return 0;
                            }
                            return left - right;
                        });
        if (backoffValue > 0) {
            if (counter.compareAndExchange(1000, 0) >= 1000) {
                log.trace(
                        "Back off trigger present, skipping polling for the next {} times",
                        backoffValue);
            }
            return true;
        }
        return false;
    }

    private boolean isRedisHealthy() {
        if (redisMaintenanceDAO.isPresent() && redisMaintenanceDAO.get().isMemoryCritical()) {
            log.debug(
                    "Backing off from trying workflow scheduling as redis memory is critical - {}",
                    redisMaintenanceDAO.get().getMemoryUsage());
            scheduleWfPollerBackoff.set(backoffFactor.addAndGet(5) * 10);
            return false;
        }
        scheduleWfPollerBackoff.set(0);
        backoffFactor.set(0);
        return true;
    }

    void pollAndExecute(String queueName, Consumer<List<String>> handler, int pollBatchSize) {
        if (!isRunning()) {
            if (counter.incrementAndGet() >= 50) {
                counter.set(0);
            }
            return;
        }
        List<String> messageIds = queueDAO.pop(queueName, pollBatchSize, 500);
        if (messageIds.size() > 0) {
            log.trace("Polling queue:{}, got {}", queueName, messageIds.size());
        } else {
            if (counter.incrementAndGet() >= 50) {
                log.trace("Polling queue:{}, got {} schedules", queueName, messageIds.size());
                counter.set(0);
            }
        }
        if (messageIds.size() > 0) {
            handler.accept(messageIds);
        }
    }

    private void handleArchival(List<String> messageIds) {
        for (String msgId : messageIds) {
            if (StringUtils.isBlank(msgId)) {
                continue;
            }
            String[] parts = getOrgAndPayload(msgId);
            String orgId = parts[0];
            String executionId = parts[1];
            Monitors.getTimer("scheduler_archival")
                    .record(
                            () -> {
                                try {
                                    if (orgId != null) {
                                        setRequestOrgId(orgId);
                                    }
                                    log.trace(
                                            "Schedule execution id: {} from queue: {} being sent to the archival",
                                            executionId,
                                            CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME);
                                    WorkflowScheduleExecutionModel model =
                                            schedulerDAO.readExecutionRecord(executionId);
                                    if (model != null) {
                                        try {
                                            schedulerArchivalDAO.saveExecutionRecord(model);
                                        } catch (Exception e) {
                                            log.error("Error persisting archival record", e);
                                            throw e;
                                        }
                                    }
                                    schedulerDAO.removeExecutionRecord(executionId);
                                    queueDAO.ack(
                                            CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME, msgId);
                                } catch (Exception e) {
                                    log.error("Error handling archival record", e);
                                    Monitors.error(
                                            this.getClass().getSimpleName(), "handleArchival");
                                } finally {
                                    clearRequestOrgId();
                                }
                            });
        }
        archivalsSinceLastIndex.addAndGet(messageIds.size());
    }

    private long getOffsetSeconds(ZonedDateTime currentTime, ZonedDateTime newTime) {
        long offsetSeconds = Duration.between(currentTime, newTime).getSeconds();
        long offsetMillis =
                newTime.toInstant().toEpochMilli() - currentTime.toInstant().toEpochMilli();
        long offsetDelta =
                BigDecimal.valueOf(offsetMillis % 1000)
                        .setScale(-3, RoundingMode.HALF_UP)
                        .longValue();
        if (offsetDelta != 0) {
            offsetSeconds = offsetSeconds + (offsetDelta > 0 ? 1 : -1);
        }
        log.debug(
                "Offset - {} - {} - {} - {} - {}",
                currentTime,
                newTime,
                offsetSeconds,
                offsetMillis,
                offsetDelta);
        return offsetSeconds;
    }

    private void ackAndPushMessage(String msgId, long offsetSeconds) {
        queueDAO.push(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, msgId, 0, withJitter(offsetSeconds));
    }

    /**
     * Converts offset seconds to a Duration with dynamic random jitter added. Jitter scales with
     * recent queue throughput (burst estimate) to prevent thundering-herd spikes when many
     * schedules share the same cron expression, while remaining negligible under low load.
     *
     * <p>~0.3ms per schedule in the current burst, capped at {@code
     * conductor.scheduler.maxScheduleJitterMs}. Examples: 10 schedules → ~3ms, 1000 → ~300ms, 10000
     * → capped at maxScheduleJitterMs.
     */
    private Duration withJitter(long offsetSeconds) {
        int burst = burstEstimate.get();
        int maxJitter = properties.getMaxScheduleJitterMs();
        // Use long arithmetic to prevent overflow when burst is large
        long ceiling = Math.min((long) burst * 3 / 10, maxJitter);
        long jitter = ceiling > 0 ? ThreadLocalRandom.current().nextLong(ceiling) : 0;
        return Duration.ofSeconds(offsetSeconds).plusMillis(jitter);
    }

    /**
     * Extracts an optional orgId prefix from a queue message ID. Enterprise overrides {@link
     * #getQueueMsgId} on models to produce "{orgId}:{payload}" format; OSS messages have no prefix.
     * Returns {@code [orgId, payload]} — for OSS the orgId will always be {@code null} and payload
     * equals msgId.
     */
    protected String[] getOrgAndPayload(String msgId) {
        // Enterprise org IDs are 4 chars; check for "XXXX:" prefix
        if (msgId.length() > 4 && msgId.charAt(4) == ':') {
            return new String[] {msgId.substring(0, 4), msgId.substring(5)};
        }
        return new String[] {null, msgId};
    }

    // --- Multi-cron queue message support ---

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SchedulerQueueMessage {
        @JsonIgnore String orgId;
        String name;
        String cron; // "cronExpr timezone", null for single-cron
        int id; // -1 for single-cron

        SchedulerQueueMessage(String orgId, String name, String cron, int id) {
            this.orgId = orgId;
            this.name = name;
            this.cron = cron;
            this.id = id;
        }

        @JsonIgnore
        boolean isMultiCron() {
            return cron != null;
        }
    }

    private SchedulerQueueMessage parseQueueMessage(String msgId) {
        String[] parts = getOrgAndPayload(msgId);
        String orgId = parts[0];
        String payload = parts[1];

        if (payload.startsWith("{")) {
            try {
                SchedulerQueueMessage msg =
                        objectMapper.readValue(payload, SchedulerQueueMessage.class);
                msg.setOrgId(orgId);
                return msg;
            } catch (Exception e) {
                log.warn(
                        "Failed to parse JSON queue message '{}', treating as schedule name",
                        payload,
                        e);
            }
        }
        return new SchedulerQueueMessage(orgId, payload, null, -1);
    }

    @SneakyThrows
    private String buildMultiCronPayload(String scheduleName, String cronWithTz, int index) {
        SchedulerQueueMessage msg =
                new SchedulerQueueMessage(null, scheduleName, cronWithTz, index);
        return objectMapper.writeValueAsString(msg);
    }

    private String buildMultiCronMsgId(
            WorkflowScheduleModel schedule, String cronWithTz, int index) {
        return buildMultiCronPayload(schedule.getName(), cronWithTz, index);
    }

    private List<String> buildAllMultiCronMsgIds(WorkflowScheduleModel schedule) {
        List<String> msgIds = new ArrayList<>();
        List<CronSchedule> cronSchedules = schedule.getCronSchedules();
        for (int i = 0; i < cronSchedules.size(); i++) {
            CronSchedule cs = cronSchedules.get(i);
            String cronWithTz =
                    cs.getCronExpression()
                            + " "
                            + (cs.getZoneId() != null ? cs.getZoneId() : "UTC");
            msgIds.add(buildMultiCronMsgId(schedule, cronWithTz, i));
        }
        return msgIds;
    }

    private void removeScheduleQueueMessages(WorkflowScheduleModel schedule) {
        // Always remove the single-cron message
        queueDAO.remove(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, schedule.getQueueMsgId());
        // Also remove multi-cron messages if applicable
        if (schedule.hasMultipleCronSchedules()) {
            for (String msgId : buildAllMultiCronMsgIds(schedule)) {
                queueDAO.remove(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, msgId);
            }
        }
    }

    @VisibleForTesting
    void handleSchedules(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
        }
        Monitors.getCounter("scheduler_handler_batch").increment(messageIds.size());
        for (String msgId : messageIds) {
            if (StringUtils.isBlank(msgId)) {
                continue;
            }

            Stopwatch timer = Stopwatch.createStarted();
            SchedulerQueueMessage queueMsg = parseQueueMessage(msgId);
            String scheduleName = queueMsg.name;

            try {
                if (queueMsg.orgId != null) {
                    setRequestOrgId(queueMsg.orgId);
                }
                log.debug(
                        "Schedule name: {} from queue: {} being sent to the workflow executor",
                        scheduleName,
                        CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
                WorkflowScheduleModel schedule = schedulerDAO.findScheduleByName(scheduleName);
                if (schedule == null) {
                    log.warn(
                            "Schedule {} has been deleted, removing from processing", scheduleName);
                    ackFinal(msgId);
                    continue;
                }

                if (schedule.isPaused()) {
                    log.debug(
                            "Schedule {} has been paused, removing from processing", scheduleName);
                    ackFinal(msgId);
                    continue;
                }

                checkExecutionPermission(schedule);

                // For multi-cron messages, validate the cron index is still valid
                if (queueMsg.isMultiCron()) {
                    List<CronSchedule> cronSchedules = schedule.getCronSchedules();
                    if (queueMsg.id >= cronSchedules.size()) {
                        log.warn(
                                "Cron index {} is out of bounds for schedule {} (size={}), removing stale message",
                                queueMsg.id,
                                scheduleName,
                                cronSchedules.size());
                        queueDAO.ack(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, msgId);
                        continue;
                    }
                }

                String schedulerCron;
                String executionZoneId;
                if (queueMsg.isMultiCron()) {
                    schedulerCron = queueMsg.cron;
                    CronSchedule cs = schedule.getCronSchedules().get(queueMsg.id);
                    executionZoneId = cs.getZoneId() != null ? cs.getZoneId() : "UTC";
                } else {
                    executionZoneId = schedule.getZoneId() != null ? schedule.getZoneId() : "UTC";
                    schedulerCron = schedule.getCronExpression() + " " + executionZoneId;
                }

                // "is it due" guard — same logic for single and multi-cron,
                // keyed by msgId (schedule name or JSON payload respectively)
                long scheduledTime = schedulerDAO.getNextRunTimeInEpoch(msgId);
                ZonedDateTime newRunTime =
                        getZonedDateTimeFromEpoch(scheduledTime, executionZoneId);
                ZonedDateTime currentSystemTime =
                        schedulerTimeProvider.getUtcTime(ZoneId.of(executionZoneId));

                long offsetSeconds = getOffsetSeconds(currentSystemTime, newRunTime);
                if (offsetSeconds > 1) {
                    log.warn(
                            "Schedule {} is not due for running, will move back into the queue",
                            msgId);
                    queueDAO.push(
                            CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME,
                            msgId,
                            0,
                            Math.max(offsetSeconds, 1));
                    continue;
                }

                WorkflowScheduleExecutionModel scheduledExecutionRecord =
                        new WorkflowScheduleExecutionModel(
                                UUID.randomUUID().toString(),
                                scheduleName,
                                scheduledTime,
                                Instant.now().toEpochMilli(),
                                schedule.getStartWorkflowRequest().getName(),
                                null,
                                null,
                                null,
                                schedule.getStartWorkflowRequest(),
                                WorkflowScheduleExecutionModel.State.POLLED,
                                executionZoneId);

                StartWorkflowRequest request =
                        swrFrom(schedule, scheduledExecutionRecord, schedulerCron);
                log.debug(
                        "Triggering workflow request for schedule:{}, execution id: {}",
                        schedule.getName(),
                        scheduledExecutionRecord.getExecutionId());

                SecurityContextHolder.clearContext();
                triggerWorkflowRequest(scheduledExecutionRecord, request);

                try {
                    schedulerDAO.saveExecutionRecord(scheduledExecutionRecord);
                    queueDAO.push(
                            CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME,
                            scheduledExecutionRecord.getQueueMsgId(),
                            0);
                } catch (Exception e) {
                    log.error("Error saving execution record", e);
                }

                try {
                    ZonedDateTime lastExpectedRuntime =
                            getZonedDateTimeFromEpoch(
                                    scheduledExecutionRecord.getScheduledTime(),
                                    scheduledExecutionRecord.getZoneId());
                    if (queueMsg.isMultiCron()) {
                        CronSchedule cs = schedule.getCronSchedules().get(queueMsg.id);
                        String cronExpr = cs.getCronExpression();
                        String cronZoneId = cs.getZoneId() != null ? cs.getZoneId() : "UTC";
                        computeAndSaveNextScheduleForSingleCronEntry(
                                schedule,
                                cronExpr,
                                cronZoneId,
                                queueMsg.id,
                                currentSystemTime,
                                lastExpectedRuntime);
                    } else {
                        computeAndSaveNextSchedule(
                                schedule, currentSystemTime, lastExpectedRuntime);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            } finally {
                clearRequestOrgId();
                timer.stop();
                Monitors.getTimer("scheduler_handler")
                        .record(timer.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
            }
        }
    }

    boolean computeAndSaveNextSchedule(
            WorkflowScheduleModel schedule,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRuntime) {
        if (schedule.hasMultipleCronSchedules()) {
            return computeAndSaveNextScheduleMultiCron(
                    schedule, currentSystemTime, lastExpectedRuntime);
        }

        ZonedDateTime nextRuntime =
                computeNextSchedule(schedule, currentSystemTime, lastExpectedRuntime);

        if (nextRuntime == null) {
            ackFinal(schedule.getName());
            return false;
        }

        schedulerDAO.setNextRunTimeInEpoch(
                schedule.getName(), nextRuntime.toInstant().toEpochMilli());

        long offsetSeconds = getOffsetSeconds(currentSystemTime, nextRuntime);
        if (!schedule.isPaused()) {
            ackAndPushMessage(schedule.getQueueMsgId(), Math.max(offsetSeconds, 1));
        }
        return true;
    }

    /**
     * For multi-cron schedules during create/update: push one queue message per cron expression.
     */
    private boolean computeAndSaveNextScheduleMultiCron(
            WorkflowScheduleModel schedule,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRuntime) {
        List<CronSchedule> cronSchedules = schedule.getCronSchedules();
        ZonedDateTime earliestNextTime = null;
        boolean anyScheduled = false;

        for (int i = 0; i < cronSchedules.size(); i++) {
            CronSchedule cs = cronSchedules.get(i);
            String cronExpr = cs.getCronExpression();
            String cronZoneId = cs.getZoneId() != null ? cs.getZoneId() : "UTC";

            try {
                ZonedDateTime nextTime =
                        computeNextScheduleForSingleCron(
                                schedule,
                                cronExpr,
                                cronZoneId,
                                currentSystemTime,
                                lastExpectedRuntime);
                if (nextTime != null) {
                    anyScheduled = true;
                    if (earliestNextTime == null
                            || nextTime.toInstant().isBefore(earliestNextTime.toInstant())) {
                        earliestNextTime = nextTime;
                    }
                    String cronWithTz = cronExpr + " " + cronZoneId;
                    String payload = buildMultiCronPayload(schedule.getName(), cronWithTz, i);
                    // Store per-cron next run time for the "is it due" guard
                    schedulerDAO.setNextRunTimeInEpoch(
                            payload, nextTime.toInstant().toEpochMilli());

                    if (!schedule.isPaused()) {
                        long offsetSeconds = getOffsetSeconds(currentSystemTime, nextTime);
                        String multiMsgId = buildMultiCronMsgId(schedule, cronWithTz, i);
                        queueDAO.push(
                                CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME,
                                multiMsgId,
                                0,
                                withJitter(Math.max(offsetSeconds, 1)));
                    }
                }
            } catch (Exception e) {
                log.error(
                        "Error computing next schedule for cron '{}' in schedule '{}'",
                        cronExpr,
                        schedule.getName(),
                        e);
            }
        }

        if (!anyScheduled) {
            return false;
        }

        schedulerDAO.setNextRunTimeInEpoch(
                schedule.getName(), earliestNextTime.toInstant().toEpochMilli());
        return true;
    }

    /**
     * After a multi-cron message fires: recompute and push only that specific cron's next
     * occurrence.
     */
    private boolean computeAndSaveNextScheduleForSingleCronEntry(
            WorkflowScheduleModel schedule,
            String cronExpr,
            String cronZoneId,
            int cronIndex,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRuntime) {

        ZonedDateTime nextRuntime =
                computeNextScheduleForSingleCron(
                        schedule, cronExpr, cronZoneId, currentSystemTime, lastExpectedRuntime);

        String cronWithTz = cronExpr + " " + cronZoneId;
        String multiMsgId = buildMultiCronMsgId(schedule, cronWithTz, cronIndex);

        if (nextRuntime == null) {
            ackFinal(multiMsgId);
            return false;
        }

        // Store per-cron next run time for the "is it due" guard (keyed by JSON payload)
        String payload = buildMultiCronPayload(schedule.getName(), cronWithTz, cronIndex);
        schedulerDAO.setNextRunTimeInEpoch(payload, nextRuntime.toInstant().toEpochMilli());

        // Also update the schedule-level next_run_time to the earliest across all crons (for
        // display)
        NextScheduleResult earliestResult =
                computeNextScheduleWithZone(schedule, currentSystemTime, currentSystemTime);
        if (earliestResult != null) {
            schedulerDAO.setNextRunTimeInEpoch(
                    schedule.getName(), earliestResult.getNextRunTime().toInstant().toEpochMilli());
        }

        long offsetSeconds = getOffsetSeconds(currentSystemTime, nextRuntime);
        if (!schedule.isPaused()) {
            queueDAO.push(
                    CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME,
                    multiMsgId,
                    0,
                    withJitter(Math.max(offsetSeconds, 1)));
        }
        return true;
    }

    private void ackFinal(String msgId) {
        log.warn("No more scheduled runs for this schedule - {}", msgId);
        queueDAO.ack(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, msgId);
    }

    private ZonedDateTime getZonedDateTimeFromEpoch(Long timeInEpoch, String scheduleZoneId) {
        return ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(timeInEpoch), ZoneId.of(scheduleZoneId));
    }

    public ZonedDateTime computeNextSchedule(
            WorkflowSchedule schedule,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRunTime) {
        NextScheduleResult result =
                computeNextScheduleWithZone(schedule, currentSystemTime, lastExpectedRunTime);
        return result != null ? result.getNextRunTime() : null;
    }

    public NextScheduleResult computeNextScheduleWithZone(
            WorkflowSchedule schedule,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRunTime) {
        List<CronSchedule> cronSchedules = schedule.getEffectiveCronSchedules();
        if (cronSchedules.isEmpty()) {
            log.warn("No cron schedules configured for schedule: {}", schedule.getName());
            return null;
        }

        NextScheduleResult earliestResult = null;
        Instant earliestInstant = null;

        for (CronSchedule cronSchedule : cronSchedules) {
            String cronExpr = cronSchedule.getCronExpression();
            String scheduleZoneId =
                    cronSchedule.getZoneId() != null ? cronSchedule.getZoneId() : "UTC";

            try {
                ZonedDateTime nextTime =
                        computeNextScheduleForSingleCron(
                                schedule,
                                cronExpr,
                                scheduleZoneId,
                                currentSystemTime,
                                lastExpectedRunTime);

                if (nextTime != null) {
                    Instant nextInstant = nextTime.toInstant();
                    if (earliestInstant == null || nextInstant.isBefore(earliestInstant)) {
                        earliestInstant = nextInstant;
                        earliestResult = NextScheduleResult.of(nextTime, scheduleZoneId);
                    }
                }
            } catch (Exception e) {
                log.error(
                        "Error computing next schedule for cron expression '{}' with zone '{}': {}",
                        cronExpr,
                        scheduleZoneId,
                        e.getMessage());
            }
        }

        return earliestResult;
    }

    private ZonedDateTime computeNextScheduleForSingleCron(
            WorkflowSchedule schedule,
            String cronExpressionStr,
            String cronZoneId,
            ZonedDateTime currentSystemTime,
            ZonedDateTime lastExpectedRunTime) {

        CronExpression cronExpression = CronExpression.parse(cronExpressionStr);

        ZonedDateTime currentInCronZone =
                currentSystemTime.withZoneSameInstant(ZoneId.of(cronZoneId));
        ZonedDateTime lastInCronZone =
                lastExpectedRunTime.withZoneSameInstant(ZoneId.of(cronZoneId));

        if (schedule.isRunCatchupScheduleInstances()) {
            return cronExpression.next(lastInCronZone);
        }

        if (schedule.getScheduleStartTime() != null) {
            ZonedDateTime startTime =
                    ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(schedule.getScheduleStartTime() - 500),
                            ZoneId.of(
                                    cronZoneId)); // Go back 500 ms to account for start time being
            // the exact next time
            if (currentInCronZone.isBefore(startTime)) {
                return cronExpression.next(startTime);
            }
        }

        ZonedDateTime nextRunTime =
                cronExpression.next(
                        currentInCronZone.isBefore(lastInCronZone)
                                ? lastInCronZone
                                : currentInCronZone);
        if (nextRunTime == null) {
            return null;
        }

        if (schedule.getScheduleEndTime() != null) {
            ZonedDateTime endTime =
                    ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(schedule.getScheduleEndTime()),
                            ZoneId.of(cronZoneId));
            if (nextRunTime.isAfter(endTime)) {
                return null;
            }
        }

        return nextRunTime;
    }

    private void triggerWorkflowRequest(
            WorkflowScheduleExecutionModel scheduledExecutionRecord, StartWorkflowRequest request) {
        try {
            String workflowId = workflowService.startWorkflow(request);
            scheduledExecutionRecord.setWorkflowId(workflowId);
            scheduledExecutionRecord.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        } catch (Exception e) {
            log.error("Error running workflow - {}", e.getMessage());
            scheduledExecutionRecord.setState(WorkflowScheduleExecutionModel.State.FAILED);
            String reason = e.getMessage();
            if (reason.length() > 999) {
                reason = reason.substring(0, 999);
            }
            scheduledExecutionRecord.setReason(reason);
            String stackTrace = ExceptionUtils.getStackTrace(e);
            if (stackTrace.length() > 2000) {
                stackTrace = stackTrace.substring(0, 2000);
            }
            scheduledExecutionRecord.setStackTrace(stackTrace);
        }
    }

    public Map<String, Object> requeueAllExecutionRecords() {
        List<String> recordIds = schedulerDAO.getPendingExecutionRecordIds();
        Map<String, Object> result = new HashMap<>();
        result.put("recordIds.size", recordIds.size());
        result.put("sampleIds", recordIds.stream().limit(10).collect(Collectors.toList()));
        recordIds.forEach(
                id -> {
                    try {
                        WorkflowScheduleExecutionModel record =
                                schedulerDAO.readExecutionRecord(id);
                        if (record != null) {
                            queueDAO.push(
                                    CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME,
                                    record.getQueueMsgId(),
                                    0);
                        }
                    } catch (Exception e) {
                        result.put("error." + id, e.getMessage());
                    }
                });
        return result;
    }

    private StartWorkflowRequest swrFrom(
            WorkflowScheduleModel scheduleModel,
            WorkflowScheduleExecutionModel scheduledExecutionRecord,
            String schedulerCron) {
        StartWorkflowRequest startWorkflowRequest = scheduleModel.getStartWorkflowRequest();
        StartWorkflowRequest swr = new StartWorkflowRequest();
        swr.setTaskToDomain(startWorkflowRequest.getTaskToDomain());
        swr.setCorrelationId(startWorkflowRequest.getCorrelationId());
        Map<String, Object> input = startWorkflowRequest.getInput();
        if (input == null) {
            input = new HashMap<>();
        }
        swr.setInput(new HashMap<>(input));
        swr.setName(startWorkflowRequest.getName());
        swr.setPriority(startWorkflowRequest.getPriority());
        swr.setExternalInputPayloadStoragePath(
                startWorkflowRequest.getExternalInputPayloadStoragePath());
        swr.setVersion(startWorkflowRequest.getVersion());
        swr.setWorkflowDef(startWorkflowRequest.getWorkflowDef());
        swr.setCreatedBy(scheduleModel.getCreatedBy());
        swr.getInput().put("_startedByScheduler", scheduleModel.getName());
        swr.getInput().put("_scheduledTime", scheduledExecutionRecord.getScheduledTime());
        swr.getInput().put("_executedTime", scheduledExecutionRecord.getExecutionTime());
        swr.getInput().put("_executionId", scheduledExecutionRecord.getExecutionId());
        swr.getInput().put("_schedulerCron", schedulerCron);
        swr.setIdempotencyKey(startWorkflowRequest.getIdempotencyKey());
        swr.setIdempotencyStrategy(startWorkflowRequest.getIdempotencyStrategy());
        return swr;
    }

    private String getEffectiveZoneId(WorkflowSchedule workflowSchedule) {
        if (workflowSchedule.hasMultipleCronSchedules()) {
            return "UTC";
        }
        return workflowSchedule.getZoneId() != null ? workflowSchedule.getZoneId() : "UTC";
    }

    public WorkflowSchedule createOrUpdateWorkflowSchedule(WorkflowSchedule workflowSchedule) {
        String userId = getCurrentUserId();
        checkScheduleLimit(workflowSchedule);

        Stopwatch sw = Stopwatch.createStarted();
        log.debug("Saving workflow schedule from user {} : {}", userId, workflowSchedule);
        WorkflowScheduleModel existingSchedule =
                schedulerDAO.findScheduleByName(workflowSchedule.getName());
        log.debug(
                "Schedule exists : {}: loaded in {} ms",
                existingSchedule != null,
                sw.elapsed(TimeUnit.MILLISECONDS));
        WorkflowScheduleModel updateModel = WorkflowScheduleModel.from(workflowSchedule);

        String effectiveZoneId = getEffectiveZoneId(workflowSchedule);
        ZonedDateTime now = schedulerTimeProvider.getUtcTime(ZoneId.of(effectiveZoneId));

        if (existingSchedule != null) {
            updateModel.setCreateTime(existingSchedule.getCreateTime());
            updateModel.setCreatedBy(existingSchedule.getCreatedBy());
        } else {
            updateModel.setCreateTime(now.toInstant().toEpochMilli());
            updateModel.setCreatedBy(userId);
        }
        updateModel.setUpdatedBy(userId);
        updateModel.setUpdatedTime(now.toInstant().toEpochMilli());
        // Remove old queue messages before pushing new ones (handles single→multi, multi→single
        // transitions)
        if (existingSchedule != null) {
            removeScheduleQueueMessages(existingSchedule);
        }
        log.debug("Invoking DB schedule update: {}", updateModel.getName());
        schedulerDAO.updateSchedule(updateModel);
        log.debug(
                "Completed DB schedule update: {} in {} ms",
                updateModel.getName(),
                sw.elapsed(TimeUnit.MILLISECONDS));
        computeAndSaveNextSchedule(updateModel, now, now);
        if (existingSchedule != null && updateModel.isPaused()) {
            log.debug(
                    "Existing schedule saved as paused: {}, removing queue message",
                    updateModel.getName());
            removeScheduleQueueMessages(updateModel);
        }
        return updateModel;
    }

    public WorkflowSchedule getSchedule(String name) {
        return schedulerDAO.findScheduleByName(name);
    }

    public List<WorkflowScheduleModel> getAllSchedules(String workflowName) {
        return schedulerDAO.findAllSchedules(workflowName);
    }

    public List<WorkflowScheduleModel> getAllSchedules() {
        return schedulerDAO.getAllSchedules();
    }

    public void deleteSchedule(String name) {
        WorkflowScheduleModel wsm = schedulerDAO.findScheduleByName(name);
        if (wsm == null) {
            return;
        }
        removeScheduleQueueMessages(wsm);
        schedulerDAO.deleteWorkflowSchedule(wsm.getName());
    }

    public void pauseSchedule(String name) {
        pauseSchedule(name, null);
    }

    public void pauseSchedule(String name, String pausedReason) {
        String userId = getCurrentUserId();
        WorkflowScheduleModel wsm = schedulerDAO.findScheduleByName(name);
        if (wsm == null) {
            throw new NotFoundException(String.format("Schedule '%s' not found", name));
        }
        wsm.setPaused(true);
        wsm.setUpdatedBy(userId);
        wsm.setUpdatedTime(System.currentTimeMillis());
        wsm.setPausedReason(pausedReason);
        removeScheduleQueueMessages(wsm);
        schedulerDAO.updateSchedule(wsm);
    }

    public void resumeSchedule(String name) {
        WorkflowScheduleModel wsm = schedulerDAO.findScheduleByName(name);
        if (wsm == null || !wsm.isPaused()) {
            throw new NotFoundException(String.format("Schedule '%s' not found", name));
        }
        wsm.setPaused(false);
        wsm.setPausedReason(null);
        createOrUpdateWorkflowSchedule(wsm);
    }

    public void pauseScheduler(boolean pause) {
        this.pauseScheduler.set(pause);
    }

    public SearchResult<WorkflowScheduleExecutionModel> searchScheduledExecutions(
            String query, String freeText, int start, int size, List<String> sortOptions) {
        SearchResult<String> result =
                schedulerArchivalDAO.searchScheduledExecutions(
                        query, freeText, start, size, sortOptions);
        Map<String, WorkflowScheduleExecutionModel> mapOfExecutionsById =
                schedulerArchivalDAO.getExecutionsByIds(new HashSet<>(result.getResults()));
        List<WorkflowScheduleExecutionModel> workflows =
                result.getResults().stream()
                        .map(mapOfExecutionsById::get)
                        .collect(Collectors.toList());
        int missing = result.getResults().size() - workflows.size();
        long totalHits = result.getTotalHits() - missing;
        return new SearchResult<>(totalHits, workflows);
    }

    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        return doSearchSchedules(
                workflowName, scheduleName, paused, freeText, start, size, sortOptions);
    }

    public List<Long> getListOfNextSchedules(
            String cronExpression, Long scheduleStartTime, Long scheduleEndTime, int limit) {
        try {
            CronExpression.parse(cronExpression);
        } catch (Exception e) {
            throw new RuntimeException("Invalid cron expression");
        }
        List<Long> results = new ArrayList<>();
        ZonedDateTime now = schedulerTimeProvider.getUtcTime(zoneId);
        ZonedDateTime last = now;
        WorkflowScheduleModel wsm = new WorkflowScheduleModel();
        wsm.setScheduleStartTime(scheduleStartTime);
        wsm.setScheduleEndTime(scheduleEndTime);
        wsm.setCronExpression(cronExpression);
        for (int i = 0; i < Math.min(5, limit); i++) {
            ZonedDateTime nextScheduleTime = computeNextSchedule(wsm, now, last);
            if (nextScheduleTime == null) {
                break;
            }
            results.add(nextScheduleTime.toInstant().toEpochMilli());
            last = now;
            now = nextScheduleTime;
        }
        return results;
    }

    @Override
    public void doStop() {
        this.sesArchival.shutdown();
        this.sesMain.shutdown();
    }
}
