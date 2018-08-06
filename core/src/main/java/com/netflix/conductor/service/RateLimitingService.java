package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;

/**
 * This interface is intended provide rate limiting ability to all the System Tasks
 * Provides the abstraction to register rate limiting at a {@link TaskDef} level and
 * the ability to evaluate if the {@link Task} has breached the configured rate limit
 */
public interface RateLimitingService {

    /**
     * This method needs to answer if a particular task is eligible for execution or not.
     * Intended to be evoked before execution of the task
     *
     * @param task an instance of {@link Task} which is evaluated to see if the rate limit boundary has been breached
     * @return true: if the task execution rate limit boundary has not been breached and is ok to continue with the task execution
     * false: if the task execution rate limit boundary has been breached and is not ok to continue with the task execution
     */
    boolean evaluateRateLimitBoundary(Task task);

    /**
     * This method is an abstraction to save the {@link TaskDef#rateLimitPerSecond} in case if it is greater than 0
     * Once the rateLimit configuration is saved, the configuration will be applied across all the Workflows that use this TaskDef
     *
     * @param taskDef: An instance of {@link TaskDef} which has the {@link TaskDef#rateLimitPerSecond} which will be used to configure the rate limit rules.
     */
    void updateRateLimitRules(TaskDef taskDef);
}
