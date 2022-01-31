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

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.utils.QueueUtils;

import com.google.common.annotations.VisibleForTesting;

import static com.netflix.conductor.core.execution.tasks.SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER;

@Component
@ConditionalOnProperty(
        name = "conductor.system-task-workers.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SystemTaskWorkerCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);

    private final SystemTaskWorker systemTaskWorker;
    private final String executionNameSpace;
    private final Set<WorkflowSystemTask> asyncSystemTasks;

    public SystemTaskWorkerCoordinator(
            SystemTaskWorker systemTaskWorker,
            ConductorProperties properties,
            @Qualifier(ASYNC_SYSTEM_TASKS_QUALIFIER) Set<WorkflowSystemTask> asyncSystemTasks) {
        this.systemTaskWorker = systemTaskWorker;
        this.asyncSystemTasks = asyncSystemTasks;
        this.executionNameSpace = properties.getSystemTaskWorkerExecutionNamespace();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSystemTaskExecutor() {
        this.asyncSystemTasks.stream()
                .filter(this::isFromCoordinatorExecutionNameSpace)
                .forEach(this.systemTaskWorker::startPolling);
        LOGGER.info(
                "{} initialized with {} async tasks",
                SystemTaskWorkerCoordinator.class.getSimpleName(),
                this.asyncSystemTasks.size());
    }

    @VisibleForTesting
    boolean isFromCoordinatorExecutionNameSpace(WorkflowSystemTask systemTask) {
        String queueExecutionNameSpace = QueueUtils.getExecutionNameSpace(systemTask.getTaskType());
        return StringUtils.equals(queueExecutionNameSpace, executionNameSpace);
    }
}
