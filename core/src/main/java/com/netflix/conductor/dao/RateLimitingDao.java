package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;

/**
 * An abstraction to enable different Rate Limiting implementations
 */
public interface RateLimitingDao {

    /**
     * Checks if the Task is rate limited or not based on the {@link Task#getRateLimitPerFrequency()} and {@link Task#getRateLimitFrequencyInSeconds()}
     * @param task: which needs to be evaluated whether it is rateLimited or not
     * @return true: If the {@link Task} is rateLimited
     * 		false: If the {@link Task} is not rateLimited
     */
    boolean exceedsRateLimitPerFrequency(Task task, TaskDef taskDef);
}
