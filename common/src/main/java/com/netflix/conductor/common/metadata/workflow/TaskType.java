package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.ProtoEnum;

import java.util.HashSet;
import java.util.Set;

@ProtoEnum
public enum TaskType {

    SIMPLE, DYNAMIC, FORK_JOIN, FORK_JOIN_DYNAMIC, DECISION, JOIN, SUB_WORKFLOW, EVENT, WAIT, USER_DEFINED;

    /**
     * TaskType constants representing each of the possible enumeration values.
     * Motivation: to not have any hardcoded/inline strings used in the code.
     * Example of use: CoreModule
     */
    public static final String TASK_TYPE_DECISION = "DECISION";
    public static final String TASK_TYPE_DYNAMIC = "DYNAMIC";
    public static final String TASK_TYPE_JOIN = "JOIN";
    public static final String TASK_TYPE_FORK_JOIN_DYNAMIC = "FORK_JOIN_DYNAMIC";
    public static final String TASK_TYPE_EVENT = "EVENT";
    public static final String TASK_TYPE_WAIT = "WAIT";
    public static final String TASK_TYPE_SUB_WORKFLOW = "SUB_WORKFLOW";
    public static final String TASK_TYPE_FORK_JOIN = "FORK_JOIN";
    public static final String TASK_TYPE_USER_DEFINED = "USER_DEFINED";
    public static final String TASK_TYPE_SIMPLE = "SIMPLE";

    private static Set<String> systemTasks = new HashSet<>();
    static {
        systemTasks.add(TaskType.SIMPLE.name());
        systemTasks.add(TaskType.DYNAMIC.name());
        systemTasks.add(TaskType.FORK_JOIN.name());
        systemTasks.add(TaskType.FORK_JOIN_DYNAMIC.name());
        systemTasks.add(TaskType.DECISION.name());
        systemTasks.add(TaskType.JOIN.name());
        systemTasks.add(TaskType.SUB_WORKFLOW.name());
        systemTasks.add(TaskType.EVENT.name());
        systemTasks.add(TaskType.WAIT.name());
        //Do NOT add USER_DEFINED here...
    }

    public static boolean isSystemTask(String name) {
        return systemTasks.contains(name);
    }
}
