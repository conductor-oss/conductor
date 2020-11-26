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
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.service.MetadataService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class IsolatedTaskQueueProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IsolatedTaskQueueProducer.class);
    private final MetadataService metadataService;

    public IsolatedTaskQueueProducer(MetadataService metadataService,
        @Value("${workflow.isolated.system.task.enable:false}") boolean enableIsolatedSystemTask,
        @Value("${workflow.isolated.system.task.poll.time.secs:10}") int isolatedSystemTaskPollInterval) {

        this.metadataService = metadataService;

        if (enableIsolatedSystemTask) {
            LOGGER.info("Listening for isolation groups");

            Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::addTaskQueues, 1000,
                isolatedSystemTaskPollInterval, TimeUnit.SECONDS);
        } else {
            LOGGER.info("Isolated System Task Worker DISABLED");
        }
    }

    private Set<TaskDef> getIsolationExecutionNameSpaces() {
        Set<TaskDef> isolationExecutionNameSpaces = Collections.emptySet();
        try {
            List<TaskDef> taskDefs = metadataService.getTaskDefs();
            isolationExecutionNameSpaces = taskDefs.stream()
                .filter(taskDef -> StringUtils.isNotBlank(taskDef.getIsolationGroupId())
                    || StringUtils.isNotBlank(taskDef.getExecutionNameSpace()))
                .collect(Collectors.toSet());
        } catch (RuntimeException unknownException) {
            LOGGER.error("Unknown exception received in getting isolation groups, sleeping and retrying",
                unknownException);
        }
        return isolationExecutionNameSpaces;
    }

    @VisibleForTesting
    void addTaskQueues() {
        Set<TaskDef> isolationTaskDefs = getIsolationExecutionNameSpaces();
        LOGGER.debug("Retrieved queues {}", isolationTaskDefs);
        Set<String> taskTypes = SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.keySet();

        for (TaskDef isolatedTaskDef : isolationTaskDefs) {
            for (String taskType : taskTypes) {
                String taskQueue = QueueUtils.getQueueName(taskType, null,
                    isolatedTaskDef.getIsolationGroupId(), isolatedTaskDef.getExecutionNameSpace());
                LOGGER.debug("Adding taskQueue:'{}' to system task worker coordinator", taskQueue);
                SystemTaskWorkerCoordinator.queue.add(taskQueue);
            }
        }
    }
}
