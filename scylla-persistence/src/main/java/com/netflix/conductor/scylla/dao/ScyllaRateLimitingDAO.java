package com.netflix.conductor.scylla.dao;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.model.TaskModel;

@Trace
public class ScyllaRateLimitingDAO implements RateLimitingDAO {
    @Override
    public boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef) {
        return false;
    }
}
