/*
 * Copyright 2024 Conductor Authors.
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
