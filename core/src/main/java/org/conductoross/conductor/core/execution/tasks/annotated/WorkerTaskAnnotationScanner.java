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
 * Spring component that scans for @WorkerTask annotated methods in Spring beans and adds them to
 * the existing asyncSystemTasks collection and taskMappersByTaskType map.
 */
@Component
public class WorkerTaskAnnotationScanner implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerTaskAnnotationScanner.class);

    private final List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers;
    private final Set<WorkflowSystemTask> asyncSystemTasks;
    private final Map<String, TaskMapper> taskMappers = new HashMap<>();
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public WorkerTaskAnnotationScanner(
            List<AnnotatedSystemTaskWorker> annotatedSystemTaskWorkers,
            @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
                    Set<WorkflowSystemTask> asyncSystemTasks,
            @Lazy ParametersUtils parametersUtils,
            @Lazy MetadataDAO metadataDAO) {
        this.annotatedSystemTaskWorkers = annotatedSystemTaskWorkers;
        this.asyncSystemTasks = asyncSystemTasks;
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
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

                        // Add to existing asyncSystemTasks collection
                        asyncSystemTasks.add(task);

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
}
