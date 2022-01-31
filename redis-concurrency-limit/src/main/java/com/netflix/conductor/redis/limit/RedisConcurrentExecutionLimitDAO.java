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
package com.netflix.conductor.redis.limit;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.limit.config.RedisConcurrentExecutionLimitProperties;

@Trace
@Component
@ConditionalOnProperty(
        value = "conductor.redis-concurrent-execution-limit.enabled",
        havingValue = "true")
public class RedisConcurrentExecutionLimitDAO implements ConcurrentExecutionLimitDAO {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisConcurrentExecutionLimitDAO.class);
    private static final String CLASS_NAME = RedisConcurrentExecutionLimitDAO.class.getSimpleName();

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConcurrentExecutionLimitProperties properties;

    public RedisConcurrentExecutionLimitDAO(
            StringRedisTemplate stringRedisTemplate,
            RedisConcurrentExecutionLimitProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    /**
     * Adds the {@link TaskModel} identifier to a Redis Set for the {@link TaskDef}'s name.
     *
     * @param task The {@link TaskModel} object.
     */
    @Override
    public void addTaskToLimit(TaskModel task) {
        try {
            Monitors.recordDaoRequests(
                    CLASS_NAME, "addTaskToLimit", task.getTaskType(), task.getWorkflowType());
            String taskId = task.getTaskId();
            String taskDefName = task.getTaskDefName();
            String keyName = createKeyName(taskDefName);

            stringRedisTemplate.opsForSet().add(keyName, taskId);

            LOGGER.debug("Added taskId: {} to key: {}", taskId, keyName);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "addTaskToLimit");
            String errorMsg =
                    String.format(
                            "Error updating taskDefLimit for task - %s:%s in workflow: %s",
                            task.getTaskDefName(), task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    /**
     * Remove the {@link TaskModel} identifier from the Redis Set for the {@link TaskDef}'s name.
     *
     * @param task The {@link TaskModel} object.
     */
    @Override
    public void removeTaskFromLimit(TaskModel task) {
        try {
            Monitors.recordDaoRequests(
                    CLASS_NAME, "removeTaskFromLimit", task.getTaskType(), task.getWorkflowType());
            String taskId = task.getTaskId();
            String taskDefName = task.getTaskDefName();

            String keyName = createKeyName(taskDefName);

            stringRedisTemplate.opsForSet().remove(keyName, taskId);

            LOGGER.debug("Removed taskId: {} from key: {}", taskId, keyName);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeTaskFromLimit");
            String errorMsg =
                    String.format(
                            "Error updating taskDefLimit for task - %s:%s in workflow: %s",
                            task.getTaskDefName(), task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    /**
     * Checks if the {@link TaskModel} identifier is in the Redis Set and size of the set is more
     * than the {@link TaskDef#concurrencyLimit()}.
     *
     * @param task The {@link TaskModel} object.
     * @return true if the task id is not in the set and size of the set is more than the {@link
     *     TaskDef#concurrencyLimit()}.
     */
    @Override
    public boolean exceedsLimit(TaskModel task) {
        Optional<TaskDef> taskDefinition = task.getTaskDefinition();
        if (taskDefinition.isEmpty()) {
            return false;
        }
        int limit = taskDefinition.get().concurrencyLimit();
        if (limit <= 0) {
            return false;
        }

        try {
            Monitors.recordDaoRequests(
                    CLASS_NAME, "exceedsLimit", task.getTaskType(), task.getWorkflowType());
            String taskId = task.getTaskId();
            String taskDefName = task.getTaskDefName();
            String keyName = createKeyName(taskDefName);

            boolean isMember =
                    ObjectUtils.defaultIfNull(
                            stringRedisTemplate.opsForSet().isMember(keyName, taskId), false);
            long size =
                    ObjectUtils.defaultIfNull(stringRedisTemplate.opsForSet().size(keyName), -1L);

            LOGGER.debug(
                    "Task: {} is {} of {}, size: {} and limit: {}",
                    taskId,
                    isMember ? "a member" : "not a member",
                    keyName,
                    size,
                    limit);

            return !isMember && size >= limit;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "exceedsLimit");
            String errorMsg =
                    String.format(
                            "Failed to get in progress limit - %s:%s in workflow :%s",
                            task.getTaskDefName(), task.getTaskId(), task.getWorkflowInstanceId());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg);
        }
    }

    private String createKeyName(String taskDefName) {
        StringBuilder builder = new StringBuilder();
        String namespace = properties.getNamespace();

        if (StringUtils.isNotBlank(namespace)) {
            builder.append(namespace).append(':');
        }

        return builder.append(taskDefName).toString();
    }
}
