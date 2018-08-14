package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;

/**
 * A Dummy/No-op implementation of the {@link RateLimitingService}
 */
public class DummyRateLimitingService implements RateLimitingService{

    @Override
    public boolean evaluateRateLimitBoundary(Task task) {
        return true;
    }

    @Override
    public void updateRateLimitRules(TaskDef taskDef) {

    }
}
