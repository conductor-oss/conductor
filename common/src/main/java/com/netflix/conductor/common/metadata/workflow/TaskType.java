package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.ProtoEnum;

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
     * TaskType constants representing each of the possible enumeration values.
     * Motivation: to not have any hardcoded/inline strings used in the code.
     * Example of use: CoreModule
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
    public static final String TASK_TYPE_LAMBDA= "LAMBDA";
    public static final String TASK_TYPE_EXCLUSIVE_JOIN = "EXCLUSIVE_JOIN";
    public static final String TASK_TYPE_TERMINATE = "TERMINATE";
    public static final String TASK_TYPE_KAFKA_PUBLISH = "KAFKA_PUBLISH";
    public static final String TASK_TYPE_JSON_JQ_TRANSFORM = "JSON_JQ_TRANSFORM";
    public static final String TASK_TYPE_SET_VARIABLE = "SET_VARIABLE";

    private boolean isSystemTask;

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
}
