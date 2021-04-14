/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.reconciliation;

import static com.netflix.conductor.core.execution.WorkflowExecutor.DECIDER_QUEUE;

import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically polls all running workflows in the system and evaluates them for timeouts and/or maintain consistency.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Component
@ConditionalOnProperty(name = "conductor.workflow-reconciler.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowReconciler extends LifecycleAwareComponent {

    private final WorkflowSweeper workflowSweeper;
    private final QueueDAO queueDAO;
    private final int sweeperThreadCount;

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowReconciler.class);

    public WorkflowReconciler(WorkflowSweeper workflowSweeper, QueueDAO queueDAO, ConductorProperties properties) {
        this.workflowSweeper = workflowSweeper;
        this.queueDAO = queueDAO;
        this.sweeperThreadCount = properties.getSweeperThreadCount();
        LOGGER.info("WorkflowReconciler initialized with {} sweeper threads", properties.getSweeperThreadCount());
    }

    @Scheduled(fixedDelayString = "${conductor.sweep-frequency.millis:500}", initialDelayString = "${conductor.sweep-frequency.millis:500}")
    public void pollAndSweep() {
        try {
            if (!isRunning()) {
                LOGGER.debug("Component stopped, skip workflow sweep");
            } else {
                List<String> workflowIds = queueDAO.pop(DECIDER_QUEUE, sweeperThreadCount, 2000);
                if (workflowIds != null) {
                    // wait for all workflow ids to be "swept"
                    CompletableFuture.allOf(workflowIds
                        .stream()
                        .map(workflowSweeper::sweepAsync)
                        .toArray(CompletableFuture[]::new))
                        .get();
                    LOGGER.debug("Sweeper processed {} from the decider queue", String.join(",", workflowIds));
                }
                //NOTE: Disabling the sweeper implicitly disables this metric.
                recordQueueDepth();
            }
        } catch (Exception e) {
            Monitors.error(WorkflowReconciler.class.getSimpleName(), "poll");
            LOGGER.error("Error when polling for workflows", e);
            if (e instanceof InterruptedException) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            }
        }
    }

    private void recordQueueDepth() {
        int currentQueueSize = queueDAO.getSize(DECIDER_QUEUE);
        Monitors.recordGauge(DECIDER_QUEUE, currentQueueSize);
    }
}
