package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.ProtoEnum;

import java.util.HashSet;
import java.util.Set;

@ProtoEnum
public enum TaskType {
    SIMPLE, DYNAMIC, FORK_JOIN, FORK_JOIN_DYNAMIC, DECISION, JOIN, SUB_WORKFLOW, EVENT, WAIT, USER_DEFINED;

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
