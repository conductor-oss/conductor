/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashSet;
import java.util.Set;

import com.netflix.conductor.annotations.protogen.ProtoEnum;

@ProtoEnum
public enum TaskType {
    SIMPLE,
    DYNAMIC,
    FORK_JOIN,
    FORK_JOIN_DYNAMIC,
    DECISION,
    SWITCH,
    JOIN,
    DO_WHILE,
    SUB_WORKFLOW,
    EVENT,
    WAIT,
    USER_DEFINED,
    HTTP,
    LAMBDA,
    INLINE,
    EXCLUSIVE_JOIN,
    TERMINATE,
    KAFKA_PUBLISH,
    JSON_JQ_TRANSFORM,
    SET_VARIABLE;

    /**
     * TaskType constants representing each of the possible enumeration values. Motivation: to not
     * have any hardcoded/inline strings used in the code.
     */
    public static final String TASK_TYPE_DECISION = "DECISION";

    public static final String TASK_TYPE_SWITCH = "SWITCH";
    public static final String TASK_TYPE_DYNAMIC = "DYNAMIC";
    public static final String TASK_TYPE_JOIN = "JOIN";
    public static final String TASK_TYPE_DO_WHILE = "DO_WHILE";
    public static final String TASK_TYPE_FORK_JOIN_DYNAMIC = "FORK_JOIN_DYNAMIC";
    public static final String TASK_TYPE_EVENT = "EVENT";
    public static final String TASK_TYPE_WAIT = "WAIT";
    public static final String TASK_TYPE_SUB_WORKFLOW = "SUB_WORKFLOW";
    public static final String TASK_TYPE_FORK_JOIN = "FORK_JOIN";
    public static final String TASK_TYPE_SIMPLE = "SIMPLE";
    public static final String TASK_TYPE_HTTP = "HTTP";
    public static final String TASK_TYPE_LAMBDA = "LAMBDA";
    public static final String TASK_TYPE_INLINE = "INLINE";
    public static final String TASK_TYPE_EXCLUSIVE_JOIN = "EXCLUSIVE_JOIN";
    public static final String TASK_TYPE_TERMINATE = "TERMINATE";
    public static final String TASK_TYPE_KAFKA_PUBLISH = "KAFKA_PUBLISH";
    public static final String TASK_TYPE_JSON_JQ_TRANSFORM = "JSON_JQ_TRANSFORM";
    public static final String TASK_TYPE_SET_VARIABLE = "SET_VARIABLE";
    public static final String TASK_TYPE_FORK = "FORK";

    private static final Set<String> BUILT_IN_TASKS = new HashSet<>();

    static {
        BUILT_IN_TASKS.add(TASK_TYPE_DECISION);
        BUILT_IN_TASKS.add(TASK_TYPE_SWITCH);
        BUILT_IN_TASKS.add(TASK_TYPE_FORK);
        BUILT_IN_TASKS.add(TASK_TYPE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_EXCLUSIVE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_DO_WHILE);
    }

    /**
     * Converts a task type string to {@link TaskType}. For an unknown string, the value is
     * defaulted to {@link TaskType#USER_DEFINED}.
     *
     * <p>NOTE: Use {@link Enum#valueOf(Class, String)} if the default of USER_DEFINED is not
     * necessary.
     *
     * @param taskType The task type string.
     * @return The {@link TaskType} enum.
     */
    public static TaskType of(String taskType) {
        try {
            return TaskType.valueOf(taskType);
        } catch (IllegalArgumentException iae) {
            return TaskType.USER_DEFINED;
        }
    }

    public static boolean isBuiltIn(String taskType) {
        return BUILT_IN_TASKS.contains(taskType);
    }
}
