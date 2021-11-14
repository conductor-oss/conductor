/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.redis.limit;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(value = "conductor.redis-concurrent-execution-limit.enabled", havingValue = "true")
public class RedisConcurrentExecutionLimitDAO implements ConcurrentExecutionLimitDAO {

    private static final Logger log = LoggerFactory.getLogger(RedisConcurrentExecutionLimitDAO.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ConductorProperties properties;

    public RedisConcurrentExecutionLimitDAO(StringRedisTemplate stringRedisTemplate, ConductorProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void addTaskToLimit(Task task) {
        String taskId = task.getTaskId();
        String taskDefName = task.getTaskDefName();

        String keyName = createKeyName(taskDefName);

        stringRedisTemplate.opsForSet().add(keyName, taskId);

        log.debug("Added taskId: {} to key: {}", taskId, keyName);
    }

    @Override
    public void removeTaskFromLimit(Task task) {
        String taskId = task.getTaskId();
        String taskDefName = task.getTaskDefName();

        String keyName = createKeyName(taskDefName);

        stringRedisTemplate.opsForSet().remove(keyName, taskId);

        log.debug("Removed taskId: {} from key: {}", taskId, keyName);
    }

    @Override
    public boolean exceedsLimit(Task task) {
        Optional<TaskDef> taskDefinition = task.getTaskDefinition();
        if (taskDefinition.isEmpty()) {
            return false;
        }
        int limit = taskDefinition.get().concurrencyLimit();
        if (limit <= 0) {
            return false;
        }

        String taskId = task.getTaskId();
        String taskDefName = task.getTaskDefName();

        String keyName = createKeyName(taskDefName);

        boolean isMember = ObjectUtils.defaultIfNull(stringRedisTemplate.opsForSet().isMember(keyName, taskId), false);
        long size = ObjectUtils.defaultIfNull(stringRedisTemplate.opsForSet().size(keyName), -1L);

        log.debug("Task: {} is {}, size: {} and limit: {}", taskId, isMember ? "member": "non-member", size, limit);

        return !isMember && size >= limit;
    }

    private String createKeyName(String taskDefName) {
        StringBuilder builder = new StringBuilder();
        String appId = properties.getAppId();
        String stackName = properties.getStack();

        if (StringUtils.isNotBlank(appId)) {
            builder.append(appId).append('.');
        }

        if (StringUtils.isNotBlank(stackName)) {
            builder.append(stackName).append('.');
        }

        return builder.append(taskDefName).toString();
    }
}
