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
package com.netflix.conductor.core.reconciliation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.metrics.Monitors;

/**
 * Periodically removes terminal workflows (and their tasks) that are older than the configured
 * retention window.
 *
 * <p>The batch is intentionally thin: it pages over candidate ids using {@link
 * IndexDAO#searchArchivableWorkflows(String, long)} and delegates the actual removal to {@link
 * ExecutionDAOFacade#removeWorkflow(String, boolean)}, which already coordinates the execution
 * store and the search index in the right order.
 *
 * <p>Distributed safety is provided by the pluggable {@link Lock} abstraction so only one instance
 * runs per scheduling tick — failed acquisitions back off immediately rather than queueing. The
 * batch implements {@link LifecycleAwareComponent} so an in-flight run aborts cooperatively when
 * the Spring context begins to stop.
 *
 * <p>Rolling indices such as {@code conductor_task_log_*}, {@code conductor_event_*} and {@code
 * conductor_message_*} are out of scope; manage their retention through index-level lifecycle
 * policies (e.g. OpenSearch ISM) instead.
 *
 * <p>The component is disabled unless {@code conductor.workflow-history-cleanup.enabled=true}.
 */
@Component
@EnableConfigurationProperties(WorkflowHistoryCleanupProperties.class)
@ConditionalOnProperty(name = "conductor.workflow-history-cleanup.enabled", havingValue = "true")
public class WorkflowHistoryCleaner extends LifecycleAwareComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowHistoryCleaner.class);

    private final ExecutionDAOFacade executionDAOFacade;
    private final IndexDAO indexDAO;
    private final Lock lock;
    private final WorkflowHistoryCleanupProperties properties;
    private final String workflowIndexName;

    public WorkflowHistoryCleaner(
            ExecutionDAOFacade executionDAOFacade,
            IndexDAO indexDAO,
            Lock lock,
            WorkflowHistoryCleanupProperties properties,
            Environment environment) {
        this.executionDAOFacade = executionDAOFacade;
        this.indexDAO = indexDAO;
        this.lock = lock;
        this.properties = properties;
        this.workflowIndexName = resolveWorkflowIndexName(properties, environment);
        LOGGER.info(
                "WorkflowHistoryCleaner initialized. retentionDays={}, catchUpDays={}, cron='{} ({})', indexName={}",
                properties.getRetentionDays(),
                properties.getCatchUpDays(),
                properties.getCron(),
                properties.getZone(),
                workflowIndexName);
    }

    @Scheduled(
            cron = "${conductor.workflow-history-cleanup.cron:0 0 * * * *}",
            zone = "${conductor.workflow-history-cleanup.zone:UTC}")
    public void cleanup() {
        // The trigger can still fire just after the ApplicationContext signals stop(); bail out
        // before touching the lock so a shutting-down node never extends its workload.
        if (!isRunning()) {
            LOGGER.debug("Component stopped, skip workflow history cleanup");
            return;
        }
        // Even if every instance fires at the same time, a single winner is enough. Losers exit
        // immediately rather than queueing on the lock.
        if (!tryLock()) {
            LOGGER.info("Another instance holds the cleanup lock; skipping this run.");
            return;
        }
        long started = System.currentTimeMillis();
        int totalDeleted = 0;
        int totalFailed = 0;
        try {
            int retentionDays = properties.getRetentionDays();
            int catchUpDays = Math.max(1, properties.getCatchUpDays());

            for (int offset = 0; offset < catchUpDays; offset++) {
                if (!isRunning()) {
                    LOGGER.info(
                            "Component stopping mid-run; aborting after processing {} day(s)",
                            offset);
                    break;
                }
                long ttlDays = (long) retentionDays + offset;
                DayResult r = cleanupOneDay(ttlDays);
                totalDeleted += r.deleted;
                totalFailed += r.failed;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Cleanup interrupted.");
        } catch (Exception e) {
            LOGGER.error("Unexpected error during workflow history cleanup", e);
            Monitors.error(WorkflowHistoryCleaner.class.getSimpleName(), "cleanup");
        } finally {
            try {
                lock.releaseLock(properties.getLockId());
            } catch (Exception e) {
                LOGGER.warn("Failed to release cleanup lock", e);
            }
            LOGGER.info(
                    "Workflow history cleanup finished in {}ms. deleted={}, failed={}",
                    System.currentTimeMillis() - started,
                    totalDeleted,
                    totalFailed);
        }
    }

    private DayResult cleanupOneDay(long ttlDays) throws InterruptedException {
        DayResult result = new DayResult();
        RecentIdCache processed = new RecentIdCache(properties.getProcessedCacheSize());
        int noProgress = 0;
        long batchPauseMillis = properties.getBatchPause().toMillis();
        long refreshWaitMillis = properties.getIndexRefreshWait().toMillis();
        int maxIterations = Math.max(1, properties.getMaxIterationsPerDay());

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if (!isRunning()) {
                break;
            }
            List<String> candidates =
                    indexDAO.searchArchivableWorkflows(workflowIndexName, ttlDays);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }

            List<String> toProcess = new ArrayList<>(candidates.size());
            for (String id : candidates) {
                if (!processed.contains(id)) {
                    toProcess.add(id);
                }
            }

            if (toProcess.isEmpty()) {
                // Same ids came back — the asynchronous index delete has not landed yet. Sleep
                // once and retry, but bail after a few consecutive no-progress passes to avoid
                // burning the iteration budget on a stuck cluster.
                noProgress++;
                if (noProgress >= 3) {
                    LOGGER.debug(
                            "Stopping day ttlDays={} after {} no-progress iterations",
                            ttlDays,
                            noProgress);
                    break;
                }
                Thread.sleep(refreshWaitMillis);
                continue;
            }
            noProgress = 0;

            for (String id : toProcess) {
                if (!isRunning()) {
                    break;
                }
                try {
                    executionDAOFacade.removeWorkflow(id, false);
                    result.deleted++;
                } catch (Exception e) {
                    result.failed++;
                    LOGGER.warn(
                            "Failed to remove workflow {} during history cleanup: {}",
                            id,
                            e.getMessage());
                    Monitors.error(WorkflowHistoryCleaner.class.getSimpleName(), "removeWorkflow");
                }
                // Mark even failures as processed so a transient error does not pin the batch.
                processed.add(id);
            }

            if (batchPauseMillis > 0) {
                Thread.sleep(batchPauseMillis);
            }
        }

        if (result.deleted > 0 || result.failed > 0) {
            LOGGER.info(
                    "Cleanup day ttlDays={} -> deleted={}, failed={}",
                    ttlDays,
                    result.deleted,
                    result.failed);
        }
        return result;
    }

    /**
     * Non-blocking lock acquisition: {@code timeToTry=0} so the call returns immediately on
     * contention. Lock implementations that ignore {@code leaseTime} (for example, those backed by
     * Redis registries with a fixed TTL) still get the configured value passed for clarity.
     */
    private boolean tryLock() {
        try {
            boolean acquired =
                    lock.acquireLock(
                            properties.getLockId(),
                            0L,
                            properties.getLockLeaseTime().toMillis(),
                            TimeUnit.MILLISECONDS);
            if (!acquired) {
                Monitors.recordAcquireLockUnsuccessful();
            }
            return acquired;
        } catch (Exception e) {
            Monitors.recordAcquireLockFailure(e.getClass().getName());
            LOGGER.warn("Failed to acquire cleanup lock", e);
            return false;
        }
    }

    private static String resolveWorkflowIndexName(
            WorkflowHistoryCleanupProperties props, Environment env) {
        if (StringUtils.isNotBlank(props.getWorkflowIndexName())) {
            return props.getWorkflowIndexName();
        }
        String prefix =
                env.getProperty("conductor.elasticsearch.index-prefix", String.class, "conductor");
        return prefix + "_workflow";
    }

    private static final class DayResult {
        int deleted;
        int failed;
    }

    /**
     * Fixed-size LRU keyed by workflow id. Insertion-order eviction keeps memory bounded even when
     * a backlog of millions of workflows needs to drain.
     */
    private static final class RecentIdCache {

        private final LinkedHashMap<String, Boolean> map;

        RecentIdCache(int maxSize) {
            final int capacity = Math.max(1, maxSize);
            this.map =
                    new LinkedHashMap<>(Math.min(16, capacity), 0.75f, false) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                            return size() > capacity;
                        }
                    };
        }

        boolean contains(String id) {
            return map.containsKey(id);
        }

        void add(String id) {
            map.put(id, Boolean.TRUE);
        }
    }
}
