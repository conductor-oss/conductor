/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PlannerContextFetchTask} as a Conductor system task bean. The bean name must
 * match {@code PlannerContextFetchTask.TASK_TYPE} so Conductor's {@code SystemTaskRegistry} can
 * look it up by task type.
 */
@Configuration
public class PlannerContextFetchTaskConfig {

    @Bean(PlannerContextFetchTask.TASK_TYPE)
    public PlannerContextFetchTask plannerContextFetchTask() {
        return new PlannerContextFetchTask();
    }
}
