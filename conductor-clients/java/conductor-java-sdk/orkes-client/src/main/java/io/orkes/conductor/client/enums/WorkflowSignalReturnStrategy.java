package io.orkes.conductor.client.enums;

public enum WorkflowSignalReturnStrategy {
    TARGET_WORKFLOW,  // Default
    BLOCKING_WORKFLOW,
    BLOCKING_TASK,
    BLOCKING_TASK_INPUT
}