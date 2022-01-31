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
package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.model.TaskModel;

/** An abstraction to enable different Rate Limiting implementations */
public interface RateLimitingDAO {

    /**
     * Checks if the Task is rate limited or not based on the {@link
     * TaskModel#getRateLimitPerFrequency()} and {@link TaskModel#getRateLimitFrequencyInSeconds()}
     *
     * @param task: which needs to be evaluated whether it is rateLimited or not
     * @return true: If the {@link TaskModel} is rateLimited false: If the {@link TaskModel} is not
     *     rateLimited
     */
    boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef);
}
