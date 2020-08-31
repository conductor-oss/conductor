/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.service.ExecutionService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Singleton
public class SystemTaskWorkerCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);

    private SystemTaskExecutor systemTaskExecutor;
    private final Configuration config;

    private final int pollInterval;
    private final String executionNameSpace;

    static BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static Set<String> listeningTaskQueues = new HashSet<>();
    public static Map<String, WorkflowSystemTask> taskNameWorkflowTaskMapping = new ConcurrentHashMap<>();

    private static final String CLASS_NAME = SystemTaskWorkerCoordinator.class.getName();

    @Inject
    public SystemTaskWorkerCoordinator(QueueDAO queueDAO, WorkflowExecutor workflowExecutor, Configuration config, ExecutionService executionService) {
        this.config = config;

        this.executionNameSpace = config.getSystemTaskWorkerExecutionNamespace();
        this.pollInterval = config.getSystemTaskWorkerPollInterval();
        int threadCount = config.getSystemTaskWorkerThreadCount();
        if (threadCount > 0) {
            this.systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, config, executionService);
            new Thread(this::listen).start();
            LOGGER.info("System Task Worker Coordinator initialized with poll interval: {}", pollInterval);
        } else {
            LOGGER.info("System Task Worker DISABLED");
        }
    }

    static synchronized void add(WorkflowSystemTask systemTask) {
        LOGGER.info("Adding the queue for system task: {}", systemTask.getName());
        taskNameWorkflowTaskMapping.put(systemTask.getName(), systemTask);
        queue.add(systemTask.getName());
    }

    private void listen() {
        try {
            for (; ; ) {
                String workflowSystemTaskQueueName = queue.poll(60, TimeUnit.SECONDS);
                if (workflowSystemTaskQueueName != null && !listeningTaskQueues.contains(workflowSystemTaskQueueName)
                    && shouldListen(workflowSystemTaskQueueName)) {
                    listen(workflowSystemTaskQueueName);
                    listeningTaskQueues.add(workflowSystemTaskQueueName);
                }
            }
        } catch (InterruptedException ie) {
            Monitors.error(CLASS_NAME, "listen");
            LOGGER.warn("Error listening for workflow system tasks", ie);
        }
    }

    private void listen(String queueName) {
        Executors.newSingleThreadScheduledExecutor()
            .scheduleWithFixedDelay(() -> pollAndExecute(queueName), 1000, pollInterval, TimeUnit.MILLISECONDS);
        LOGGER.info("Started listening for queue: {}", queueName);
    }

    private void pollAndExecute(String queueName) {
        if (config.disableAsyncWorkers()) {
            LOGGER.warn("System Task Worker is DISABLED.  Not polling for system task in queue : {}", queueName);
            return;
        }
        systemTaskExecutor.pollAndExecute(queueName);
    }

    @VisibleForTesting
    boolean isFromCoordinatorExecutionNameSpace(String queueName) {
        String queueExecutionNameSpace = QueueUtils.getExecutionNameSpace(queueName);
        return StringUtils.equals(queueExecutionNameSpace, executionNameSpace);
    }

    private boolean shouldListen(String workflowSystemTaskQueueName) {
        return isFromCoordinatorExecutionNameSpace(workflowSystemTaskQueueName)
            && isAsyncSystemTask(workflowSystemTaskQueueName);
    }

    @VisibleForTesting
    boolean isAsyncSystemTask(String queue) {
        String taskType = QueueUtils.getTaskType(queue);
        if (StringUtils.isNotBlank(taskType)) {
            WorkflowSystemTask task = taskNameWorkflowTaskMapping.get(taskType);
            return Objects.nonNull(task) && task.isAsync();
        }
        return false;
    }
}	
