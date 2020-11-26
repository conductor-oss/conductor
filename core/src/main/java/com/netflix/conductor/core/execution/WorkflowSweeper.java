/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.execution;

import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class WorkflowSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSweeper.class);

    private ExecutorService executorService;
    private final ConductorProperties properties;
    private final QueueDAO queueDAO;
    private final int executorThreadPoolSize;

    private static final String CLASS_NAME = WorkflowSweeper.class.getSimpleName();

    @Autowired
    public WorkflowSweeper(WorkflowExecutor workflowExecutor, WorkflowRepairService workflowRepairService,
        ConductorProperties properties, QueueDAO queueDAO) {
        this.properties = properties;
        this.queueDAO = queueDAO;
        this.executorThreadPoolSize = properties.getSweeperThreadCount();
        if (this.executorThreadPoolSize > 0) {
            this.executorService = Executors.newFixedThreadPool(executorThreadPoolSize);
            init(workflowExecutor, workflowRepairService);
            LOGGER.info("Workflow Sweeper Initialized");
        } else {
            LOGGER.warn("Workflow sweeper is DISABLED");
        }
    }

    public void init(WorkflowExecutor workflowExecutor, WorkflowRepairService workflowRepairService) {
        ScheduledExecutorService deciderPool = Executors.newScheduledThreadPool(1);
        deciderPool.scheduleWithFixedDelay(() -> {
            try {
                boolean disable = properties.disableSweep();
                if (disable) {
                    LOGGER.info("Workflow sweep is disabled.");
                    return;
                }
                List<String> workflowIds = queueDAO
                    .pop(WorkflowExecutor.DECIDER_QUEUE, 2 * executorThreadPoolSize, 2000);
                int currentQueueSize = queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE);
                LOGGER.debug("Sweeper's current deciderqueue size: {}.", currentQueueSize);
                int retrievedWorkflows = (workflowIds != null) ? workflowIds.size() : 0;
                LOGGER.debug("Sweeper retrieved {} workflows from the decider queue.", retrievedWorkflows);

                sweep(workflowIds, workflowExecutor, workflowRepairService);
            } catch (Exception e) {
                Monitors.error(CLASS_NAME, "sweep");
                LOGGER.error("Error when sweeping workflow", e);
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void sweep(List<String> workflowIds, WorkflowExecutor workflowExecutor,
        WorkflowRepairService workflowRepairService) throws Exception {

        List<Future<?>> futures = new LinkedList<>();
        for (String workflowId : workflowIds) {
            Future<?> future = executorService.submit(() -> {
                try {

                    WorkflowContext workflowContext = new WorkflowContext(properties.getAppId());
                    WorkflowContext.set(workflowContext);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Running sweeper for workflow {}", workflowId);
                    }

                    if (properties.isWorkflowRepairServiceEnabled()) {
                        // Verify and repair tasks in the workflow.
                        workflowRepairService.verifyAndRepairWorkflowTasks(workflowId);
                    }

                    boolean done = workflowExecutor.decide(workflowId);
                    if (!done) {
                        queueDAO.setUnackTimeout(WorkflowExecutor.DECIDER_QUEUE, workflowId,
                            properties.getSweepFrequency() * 1000);
                    } else {
                        queueDAO.remove(WorkflowExecutor.DECIDER_QUEUE, workflowId);
                    }

                } catch (ApplicationException e) {
                    if (e.getCode().equals(ApplicationException.Code.NOT_FOUND)) {
                        LOGGER.error("Workflow NOT found for id: " + workflowId, e);
                        queueDAO.remove(WorkflowExecutor.DECIDER_QUEUE, workflowId);
                    }

                } catch (Exception e) {
                    queueDAO
                        .setUnackTimeout(WorkflowExecutor.DECIDER_QUEUE, workflowId,
                            properties.getSweepFrequency() * 1000);
                    Monitors.error(CLASS_NAME, "sweep");
                    LOGGER.error("Error running sweep for " + workflowId, e);
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            future.get();
        }
    }
}
