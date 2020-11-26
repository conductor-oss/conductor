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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class TaskMapperConfiguration {

    @Bean
    public TaskMappers taskMappers() {
        return new TaskMappers();
    }

    @Bean
    @Qualifier("taskProcessorsMap")
    public Map<String, TaskMapper> getTaskMappers(TaskMappers taskMappers) {
        return taskMappers.getTaskMappers();
    }
}
