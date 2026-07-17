/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.core.execution.mapper.AnnotatedSystemTaskMapper;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

/**
 * Spring component that scans for @WorkerTask annotated methods in Spring beans.
 *
 * <p>Two execution modes, selected by {@code conductor.annotated-workers.mode}:
 *
 * <ul>
 *   <li>{@code system-task} (default) — each annotated method is registered as an async system task
 *       (added to the asyncSystemTasks collection) and executed in-engine by the system-task-worker
 *       machinery. This is the historical behavior.
 *   <li>{@code poll-worker} — annotated task types are NOT registered as system tasks, so the
 *       decider queues them like any worker task, and {@link AnnotatedWorkerPollingHost} executes
 *       them through the standard poll/update path ({@code ExecutionService.poll} acks the queue
 *       message and moves the task to IN_PROGRESS before the method runs; recovery is the decider's
 *       task-def response timeout). This is the same execution model orkes-conductor uses for these
 *       task types, with in-process pollers instead of a remote workers process.
 * </ul>
 *
 * <p>In both modes a TaskMapper is registered per task type so the decider can schedule it.
 */
@Component
public class WorkerTaskAnnotationScanner implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerTaskAnnotationScanner.class);

    public static final String MODE_PROPERTY = "conductor.annotated-workers.mode";
    public static final String MODE_SYSTEM_TASK = "system-task";
    public static final String MODE_POLL_WORKER = "poll-worker";

    private final List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers;
    private final Set<WorkflowSystemTask> asyncSystemTasks;
    private final Map<String, TaskMapper> taskMappers = new HashMap<>();
    private final List<AnnotatedWorkflowSystemTask> discoveredWorkers = new ArrayList<>();
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;
    private final String mode;

    public WorkerTaskAnnotationScanner(
            List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers,
            @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
                    Set<WorkflowSystemTask> asyncSystemTasks,
            @Lazy ParametersUtils parametersUtils,
            @Lazy MetadataDAO metadataDAO,
            @Value("${" + MODE_PROPERTY + ":" + MODE_SYSTEM_TASK + "}") String mode) {
        this.annotatedSystemTaskWorkers = annotatedSystemTaskWorkers;
        this.asyncSystemTasks = asyncSystemTasks;
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
        this.mode = mode;
    }

    /**
     * Scans all Spring beans for @WorkerTask annotated methods and adds them to the
     * asyncSystemTasks collection. Also registers a TaskMapper for each annotated task type.
     */
    @Override
    public void afterPropertiesSet() {
        long startTime = System.currentTimeMillis();

        int scannedBeans = 0;
        int foundMethods = 0;

        for (Object bean : annotatedSystemTaskWorkers) {

            Class<?> beanClass = bean.getClass();
            String beanName = beanClass.getSimpleName();
            try {

                scannedBeans++;

                // Scan all public methods for @WorkerTask annotation
                for (Method method : beanClass.getMethods()) {
                    WorkerTask annotation = method.getAnnotation(WorkerTask.class);
                    if (annotation != null) {
                        String taskType = annotation.value();

                        LOGGER.info(
                                "Found @WorkerTask method: {} in bean {} with taskType={}",
                                method.getName(),
                                beanName,
                                taskType);

                        AnnotatedWorkflowSystemTask task =
                                new AnnotatedWorkflowSystemTask(taskType, method, bean, annotation);
                        discoveredWorkers.add(task);

                        if (MODE_POLL_WORKER.equals(mode)) {
                            // Not a system task: the decider queues this type like any worker
                            // task and AnnotatedWorkerPollingHost executes it via poll/update.
                            LOGGER.info(
                                    "Annotated task type {} registered for poll-worker execution",
                                    taskType);
                        } else {
                            // Add to existing asyncSystemTasks collection
                            asyncSystemTasks.add(task);
                        }

                        // Register a TaskMapper for this task type so DeciderService can find it
                        AnnotatedSystemTaskMapper mapper =
                                new AnnotatedSystemTaskMapper(
                                        taskType, parametersUtils, metadataDAO);
                        LOGGER.info("Adding task mapper {} for task {}", mapper, taskType);
                        taskMappers.put(taskType, mapper);

                        LOGGER.debug("Registered TaskMapper for annotated task type: {}", taskType);
                        foundMethods++;
                    }
                }
            } catch (Exception e) {
                // Skip beans that can't be instantiated or scanned
                LOGGER.debug(
                        "Skipping bean {} during @WorkerTask scanning: {}",
                        beanName,
                        e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        LOGGER.info(
                "Completed @WorkerTask scanning in {}ms. Scanned {} beans, found {} annotated methods",
                durationMs,
                scannedBeans,
                foundMethods);
    }

    @Qualifier("annotatedTaskSystems")
    @Bean
    public Map<String, TaskMapper> annotatedTaskSystems() {
        return taskMappers;
    }

    /** The annotated worker adapters discovered by the scan, regardless of mode. */
    public List<AnnotatedWorkflowSystemTask> getDiscoveredWorkers() {
        return List.copyOf(discoveredWorkers);
    }
}
