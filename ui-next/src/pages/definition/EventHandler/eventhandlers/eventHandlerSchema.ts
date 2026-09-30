import {
  CompleteActionType,
  ConductorEvent,
  FailActionType,
  StartAgentAction,
  StartWorkflowAction,
  TerminateWorkflowAction,
  UpdateWorkFlowVariableType,
} from "types/Events";

// v2
export const NEW_EVENT_HANDLER_TEMPLATE: Partial<ConductorEvent> = {
  name: "",
  description: "",
  // The built-in `conductor` queue: on by default in OSS and the same shape
  // in Orkes, unlike kafka/sqs whose URI differs between the two (topic vs
  // integration:topic). An EVENT task with sink "conductor" publishes here.
  event: "conductor:sample_workflow:sample_task_ref",
  evaluatorType: "javascript",
  condition: "true",
  actions: [
    {
      action: "complete_task",
      expandInlineJSON: false,
      complete_task: {
        workflowId: "${workflowId}",
        taskRefName: "${taskReferenceName}",
      },
    },
  ],
};

// TODO: Add schema definition for event handler

export const COMPLETE_TASK_ACTION: CompleteActionType = {
  action: "complete_task",
  expandInlineJSON: false,
  complete_task: {
    workflowId: "${workflowId}",
    taskRefName: "${taskReferenceName}",
  },
};

export const FAIL_TASK_ACTION: FailActionType = {
  action: "fail_task",
  expandInlineJSON: false,
  fail_task: {
    workflowId: "${workflowId}",
    taskRefName: "${taskReferenceName}",
  },
};

export const UPDATE_VARIABLES_ACTION: UpdateWorkFlowVariableType = {
  action: "update_workflow_variables",
  expandInlineJSON: false,
  update_workflow_variables: {
    workflowId: "${targetWorkflowId}",
  },
};

export const START_WORKFLOW_ACTION: StartWorkflowAction = {
  action: "start_workflow",
  start_workflow: {
    name: "sample_wf",
    version: "",
    correlationId: "",
    idempotencyKey: "",
  },
  expandInlineJSON: false,
};

export const TERMINATE_WORKFLOW_ACTION: TerminateWorkflowAction = {
  action: "terminate_workflow",
  expandInlineJSON: false,
  terminate_workflow: {
    workflowId: "",
    terminationReason: "",
  },
};

export const START_AGENT_ACTION: StartAgentAction = {
  action: "start_agent",
  expandInlineJSON: false,
  start_agent: {
    name: "",
    version: "",
    prompt: "",
    sessionId: "",
    idempotencyKey: "",
  },
};
