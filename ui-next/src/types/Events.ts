import { TagDto } from "./Tag";

export type CompleteActionType = {
  action: "complete_task";
  expandInlineJSON: boolean;
  complete_task: {
    workflowId?: string;
    taskRefName?: string;
    taskId?: string;
    output?: Record<string, unknown>;
  };
};

export type FailActionType = {
  action: "fail_task";
  expandInlineJSON: boolean;
  fail_task: {
    workflowId?: string;
    taskRefName?: string;
    taskId?: string;
    output?: Record<string, unknown>;
  };
};

export type UpdateWorkFlowVariableType = {
  action: "update_workflow_variables";
  expandInlineJSON: boolean;
  update_workflow_variables: {
    workflowId: string;
    appendArray?: boolean;
    variables?: Record<string, unknown>;
  };
};

export type StartWorkflowAction = {
  action: "start_workflow";
  start_workflow: {
    name: string;
    version: string;
    correlationId: string;
    idempotencyKey?: string;
    idempotencyStrategy?: string;
    input?: Record<string, unknown>;
    taskToDomain?: {
      [key: string]: string;
    };
  };
  expandInlineJSON: boolean;
};

export type TerminateWorkflowAction = {
  action: "terminate_workflow";
  expandInlineJSON: boolean;
  terminate_workflow: {
    workflowId: string;
    terminationReason: string;
  };
};

export type ConductorEvent = {
  name: string;
  description?: string;
  event: string;
  evaluatorType: string;
  condition: string;
  actions: Array<
    | CompleteActionType
    | FailActionType
    | UpdateWorkFlowVariableType
    | StartWorkflowAction
    | TerminateWorkflowAction
  >;
  active: boolean;
  ownerEmail: string;
  tags?: TagDto[];
};
