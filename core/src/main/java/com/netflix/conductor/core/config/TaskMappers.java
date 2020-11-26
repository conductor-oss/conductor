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
package com.netflix.conductor.core.config;

import com.netflix.conductor.core.execution.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TaskMappers implements BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskMappers.class);
    private final Map<String, TaskMapper> taskMappers = new HashMap<>();

    @SuppressWarnings("NullableProblems")
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TaskMapper) {
            final TaskMapper taskMapper = (TaskMapper) bean;
            Optional.ofNullable(taskMapper.getTaskType())
                .ifPresent(taskType -> {
                    LOGGER.info("Adding Task Mapper bean: {} for taskType: {} to taskMappers", beanName, taskType);
                    taskMappers.put(taskType, taskMapper);
                });
        }
        return bean;
    }

    public Map<String, TaskMapper> getTaskMappers() {
        return taskMappers;
    }
}
