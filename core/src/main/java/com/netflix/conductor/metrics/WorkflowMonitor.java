/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.MetadataService;

import static com.netflix.conductor.core.execution.tasks.SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER;

@Component
@ConditionalOnProperty(
        name = "conductor.workflow-monitor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WorkflowMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowMonitor.class);

    private final MetadataService metadataService;
    private final QueueDAO queueDAO;
    private final ExecutionDAOFacade executionDAOFacade;
    private final int metadataRefreshInterval;
    private final Set<WorkflowSystemTask> asyncSystemTasks;

    private List<TaskDef> taskDefs;
    private List<WorkflowDef> workflowDefs;
    private int refreshCounter = 0;

    public WorkflowMonitor(
            MetadataService metadataService,
            QueueDAO queueDAO,
            ExecutionDAOFacade executionDAOFacade,
            @Value("${conductor.workflow-monitor.metadata-refresh-interval:10}")
                    int metadataRefreshInterval,
            @Qualifier(ASYNC_SYSTEM_TASKS_QUALIFIER) Set<WorkflowSystemTask> asyncSystemTasks) {
        this.metadataService = metadataService;
        this.queueDAO = queueDAO;
        this.executionDAOFacade = executionDAOFacade;
        this.metadataRefreshInterval = metadataRefreshInterval;
        this.asyncSystemTasks = asyncSystemTasks;
        LOGGER.info("{} initialized.", WorkflowMonitor.class.getSimpleName());
    }

    @Scheduled(
            initialDelayString = "${conductor.workflow-monitor.stats.initial-delay:120000}",
            fixedDelayString = "${conductor.workflow-monitor.stats.delay:60000}")
    public void reportMetrics() {
        try {
            if (refreshCounter <= 0) {
                workflowDefs = metadataService.getWorkflowDefs();
                taskDefs = new ArrayList<>(metadataService.getTaskDefs());
                refreshCounter = metadataRefreshInterval;
            }

            workflowDefs.forEach(
                    workflowDef -> {
                        String name = workflowDef.getName();
                        String version = String.valueOf(workflowDef.getVersion());
                        String ownerApp = workflowDef.getOwnerApp();
                        long count = executionDAOFacade.getPendingWorkflowCount(name);
                        Monitors.recordRunningWorkflows(count, name, version, ownerApp);
                    });

            taskDefs.forEach(
                    taskDef -> {
                        long size = queueDAO.getSize(taskDef.getName());
                        long inProgressCount =
                                executionDAOFacade.getInProgressTaskCount(taskDef.getName());
                        Monitors.recordQueueDepth(taskDef.getName(), size, taskDef.getOwnerApp());
                        if (taskDef.concurrencyLimit() > 0) {
                            Monitors.recordTaskInProgress(
                                    taskDef.getName(), inProgressCount, taskDef.getOwnerApp());
                        }
                    });

            asyncSystemTasks.forEach(
                    workflowSystemTask -> {
                        long size = queueDAO.getSize(workflowSystemTask.getTaskType());
                        long inProgressCount =
                                executionDAOFacade.getInProgressTaskCount(
                                        workflowSystemTask.getTaskType());
                        Monitors.recordQueueDepth(workflowSystemTask.getTaskType(), size, "system");
                        Monitors.recordTaskInProgress(
                                workflowSystemTask.getTaskType(), inProgressCount, "system");
                    });

            refreshCounter--;
        } catch (Exception e) {
            LOGGER.error("Error while publishing scheduled metrics", e);
        }
    }
}
