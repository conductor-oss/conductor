/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;

/**
 * An abstraction to enable different Rate Limiting implementations
 */
public interface RateLimitingDAO {

    /**
     * Checks if the Task is rate limited or not based on the {@link Task#getRateLimitPerFrequency()} and {@link Task#getRateLimitFrequencyInSeconds()}
     * @param task: which needs to be evaluated whether it is rateLimited or not
     * @return true: If the {@link Task} is rateLimited
     * 		false: If the {@link Task} is not rateLimited
     */
    boolean exceedsRateLimitPerFrequency(Task task, TaskDef taskDef);
}
