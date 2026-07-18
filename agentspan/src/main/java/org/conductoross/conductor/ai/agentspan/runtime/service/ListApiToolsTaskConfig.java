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

import org.conductoross.conductor.ai.http.OutboundTargetPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers {@link ListApiToolsTask} as a Conductor system task bean. */
@Configuration
public class ListApiToolsTaskConfig {

    @Bean(ListApiToolsTask.TASK_TYPE)
    public ListApiToolsTask listApiToolsTask(OutboundTargetPolicy outboundTargetPolicy) {
        return new ListApiToolsTask(outboundTargetPolicy);
    }
}
