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
package com.netflix.conductor.common.metadata.tasks;

import com.github.vmg.protogen.annotations.ProtoEnum;

import java.util.HashSet;
import java.util.Set;

@ProtoEnum
public enum TaskType {

    SIMPLE(true),
    DYNAMIC(true),
    FORK_JOIN(true),
    FORK_JOIN_DYNAMIC(true),
    DECISION(true),
    JOIN(true),
    DO_WHILE(true),
    SUB_WORKFLOW(true),
    EVENT(true),
    WAIT(true),
    USER_DEFINED(false),
    HTTP(true),
    LAMBDA(true),
    EXCLUSIVE_JOIN(true),
    TERMINATE(true),
    KAFKA_PUBLISH(true),
    JSON_JQ_TRANSFORM(true),
    SET_VARIABLE(true);

    /**
     * TaskType constants representing each of the possible enumeration values. Motivation: to not have any
     * hardcoded/inline strings used in the code. Example of use: CoreModule
     */
    public static final String TASK_TYPE_DECISION = "DECISION";
    public static final String TASK_TYPE_DYNAMIC = "DYNAMIC";
    public static final String TASK_TYPE_JOIN = "JOIN";
    public static final String TASK_TYPE_DO_WHILE = "DO_WHILE";
    public static final String TASK_TYPE_FORK_JOIN_DYNAMIC = "FORK_JOIN_DYNAMIC";
    public static final String TASK_TYPE_EVENT = "EVENT";
    public static final String TASK_TYPE_WAIT = "WAIT";
    public static final String TASK_TYPE_SUB_WORKFLOW = "SUB_WORKFLOW";
    public static final String TASK_TYPE_FORK_JOIN = "FORK_JOIN";
    public static final String TASK_TYPE_USER_DEFINED = "USER_DEFINED";
    public static final String TASK_TYPE_SIMPLE = "SIMPLE";
    public static final String TASK_TYPE_HTTP = "HTTP";
    public static final String TASK_TYPE_LAMBDA = "LAMBDA";
    public static final String TASK_TYPE_EXCLUSIVE_JOIN = "EXCLUSIVE_JOIN";
    public static final String TASK_TYPE_TERMINATE = "TERMINATE";
    public static final String TASK_TYPE_KAFKA_PUBLISH = "KAFKA_PUBLISH";
    public static final String TASK_TYPE_JSON_JQ_TRANSFORM = "JSON_JQ_TRANSFORM";
    public static final String TASK_TYPE_SET_VARIABLE = "SET_VARIABLE";
    public static final String TASK_TYPE_FORK = "FORK";

    private static final Set<String> BUILT_IN_TASKS = new HashSet<>();

    static {
        BUILT_IN_TASKS.add(TASK_TYPE_DECISION);
        BUILT_IN_TASKS.add(TASK_TYPE_FORK);
        BUILT_IN_TASKS.add(TASK_TYPE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_EXCLUSIVE_JOIN);
        BUILT_IN_TASKS.add(TASK_TYPE_DO_WHILE);
    }

    private final boolean isSystemTask;

    TaskType(boolean isSystemTask) {
        this.isSystemTask = isSystemTask;
    }

    /*
     * TODO: Update code to use only enums rather than Strings.
     * This method is only used as a helper until the transition is done.
     */
    public static boolean isSystemTask(String name) {
        try {
            TaskType taskType = TaskType.valueOf(name);
            return taskType.isSystemTask;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }

    public static boolean isBuiltIn(String taskType) {
        return BUILT_IN_TASKS.contains(taskType);
    }
}
