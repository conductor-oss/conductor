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
package com.netflix.conductor.core.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.service.ExecutionService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Component
@ConditionalOnProperty(name = "conductor.system-task-workers.enabled", havingValue = "true", matchIfMissing = true)
public class SystemTaskWorkerCoordinator extends LifecycleAwareComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTaskWorkerCoordinator.class);

    private SystemTaskExecutor systemTaskExecutor;
    private final ConductorProperties properties;

    private final long pollInterval;
    private final String executionNameSpace;

    static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Set<String> listeningTaskQueues = new HashSet<>();
    public static Map<String, WorkflowSystemTask> taskNameWorkflowTaskMapping = new ConcurrentHashMap<>();

    private static final String CLASS_NAME = SystemTaskWorkerCoordinator.class.getName();

    private final List<WorkflowSystemTask> workflowSystemTasks;
    private final QueueDAO queueDAO;
    private final WorkflowExecutor workflowExecutor;
    private final ExecutionService executionService;

    public SystemTaskWorkerCoordinator(QueueDAO queueDAO, WorkflowExecutor workflowExecutor,
        ConductorProperties properties,
        ExecutionService executionService,
        List<WorkflowSystemTask> workflowSystemTasks) {
        this.properties = properties;
        this.workflowSystemTasks = workflowSystemTasks;
        this.executionNameSpace = properties.getSystemTaskWorkerExecutionNamespace();
        this.pollInterval = properties.getSystemTaskWorkerPollInterval().toMillis();
        this.queueDAO = queueDAO;
        this.workflowExecutor = workflowExecutor;
        this.executionService = executionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSystemTaskExecutor() {
        int threadCount = properties.getSystemTaskWorkerThreadCount();
        if (threadCount <= 0) {
            throw new IllegalStateException("Cannot set system task worker thread count to <=0. To disable system "
                + "task workers, set conductor.system-task-workers.enabled=false.");
        }
        this.workflowSystemTasks.forEach(this::add);
        this.systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);
        new Thread(this::listen).start();
        LOGGER.info("System Task Worker Coordinator initialized with poll interval: {}", pollInterval);
    }

    private void add(WorkflowSystemTask systemTask) {
        LOGGER.info("Adding the queue for system task: {}", systemTask.getTaskType());
        taskNameWorkflowTaskMapping.put(systemTask.getTaskType(), systemTask);
        queue.add(systemTask.getTaskType());
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
        if (!isRunning()) {
            LOGGER.debug("Component stopped. Not polling for system task in queue : {}", queueName);
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
