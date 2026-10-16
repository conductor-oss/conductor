/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.decision;

import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.mapper.HTTPTaskMapper;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;

/**
 * Schedules {@link DecisionTask} the way {@link HTTPTaskMapper} schedules an HTTP task. Without it,
 * an AI_DECISION task with no registered task definition, such as one forked by a {@code
 * FORK_JOIN_DYNAMIC}, falls back to the user-defined mapper, which fails the workflow.
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class AIDecisionTaskMapper extends HTTPTaskMapper {

    public AIDecisionTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        super(parametersUtils, metadataDAO);
    }

    @Override
    public String getTaskType() {
        return DecisionTask.TASK_TYPE;
    }
}
