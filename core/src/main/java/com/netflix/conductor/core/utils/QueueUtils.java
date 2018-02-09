/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.utils;

import com.netflix.conductor.common.metadata.tasks.Task;

/**
 *
 * @author visingh
 *
 */
public class QueueUtils {

    public static final String DOMAIN_SEPARATOR = ":";

    public static String getQueueName(Task task) {
        return getQueueName(task.getTaskType(), task.getDomain());
    }

    public static String getQueueName(String taskType, String domain) {
        String queueName = null;
        if (domain == null) {
            queueName = taskType;
        } else {
            queueName = domain + DOMAIN_SEPARATOR + taskType;
        }
        return queueName;
    }

    public static String getQueueNameWithoutDomain(String queueName) {
        return queueName.substring(queueName.indexOf(DOMAIN_SEPARATOR) + 1);
    }

}
