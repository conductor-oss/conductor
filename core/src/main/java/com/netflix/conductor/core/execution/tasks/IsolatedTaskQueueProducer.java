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
package com.netflix.conductor.core.execution.tasks;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.service.MetadataService;

import com.google.common.annotations.VisibleForTesting;

import static com.netflix.conductor.core.execution.tasks.SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER;

@Component
@ConditionalOnProperty(
        name = "conductor.system-task-workers.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IsolatedTaskQueueProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IsolatedTaskQueueProducer.class);
    private final MetadataService metadataService;
    private final Set<WorkflowSystemTask> asyncSystemTasks;
    private final SystemTaskWorker systemTaskWorker;

    private final Set<String> listeningQueues = new HashSet<>();

    public IsolatedTaskQueueProducer(
            MetadataService metadataService,
            @Qualifier(ASYNC_SYSTEM_TASKS_QUALIFIER) Set<WorkflowSystemTask> asyncSystemTasks,
            SystemTaskWorker systemTaskWorker,
            @Value("${conductor.app.isolatedSystemTaskEnabled:false}")
                    boolean isolatedSystemTaskEnabled,
            @Value("${conductor.app.isolatedSystemTaskQueuePollInterval:10s}")
                    Duration isolatedSystemTaskQueuePollIntervalSecs) {

        this.metadataService = metadataService;
        this.asyncSystemTasks = asyncSystemTasks;
        this.systemTaskWorker = systemTaskWorker;

        if (isolatedSystemTaskEnabled) {
            LOGGER.info("Listening for isolation groups");

            Executors.newSingleThreadScheduledExecutor()
                    .scheduleWithFixedDelay(
                            this::addTaskQueues,
                            1000,
                            isolatedSystemTaskQueuePollIntervalSecs.getSeconds(),
                            TimeUnit.SECONDS);
        } else {
            LOGGER.info("Isolated System Task Worker DISABLED");
        }
    }

    private Set<TaskDef> getIsolationExecutionNameSpaces() {
        Set<TaskDef> isolationExecutionNameSpaces = Collections.emptySet();
        try {
            List<TaskDef> taskDefs = metadataService.getTaskDefs();
            isolationExecutionNameSpaces =
                    taskDefs.stream()
                            .filter(
                                    taskDef ->
                                            StringUtils.isNotBlank(taskDef.getIsolationGroupId())
                                                    || StringUtils.isNotBlank(
                                                            taskDef.getExecutionNameSpace()))
                            .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Unknown exception received in getting isolation groups, sleeping and retrying",
                    e);
        }
        return isolationExecutionNameSpaces;
    }

    @VisibleForTesting
    void addTaskQueues() {
        Set<TaskDef> isolationTaskDefs = getIsolationExecutionNameSpaces();
        LOGGER.debug("Retrieved queues {}", isolationTaskDefs);

        for (TaskDef isolatedTaskDef : isolationTaskDefs) {
            for (WorkflowSystemTask systemTask : this.asyncSystemTasks) {
                String taskQueue =
                        QueueUtils.getQueueName(
                                systemTask.getTaskType(),
                                null,
                                isolatedTaskDef.getIsolationGroupId(),
                                isolatedTaskDef.getExecutionNameSpace());
                LOGGER.debug("Adding taskQueue:'{}' to system task worker coordinator", taskQueue);
                if (!listeningQueues.contains(taskQueue)) {
                    systemTaskWorker.startPolling(systemTask, taskQueue);
                    listeningQueues.add(taskQueue);
                }
            }
        }
    }
}
