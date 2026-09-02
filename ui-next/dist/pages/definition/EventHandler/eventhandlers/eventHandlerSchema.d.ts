import { CompleteActionType, ConductorEvent, FailActionType, StartAgentAction, StartWorkflowAction, TerminateWorkflowAction, UpdateWorkFlowVariableType } from "types/Events";
export declare const NEW_EVENT_HANDLER_TEMPLATE: Partial<ConductorEvent>;
export declare const COMPLETE_TASK_ACTION: CompleteActionType;
export declare const FAIL_TASK_ACTION: FailActionType;
export declare const UPDATE_VARIABLES_ACTION: UpdateWorkFlowVariableType;
export declare const START_WORKFLOW_ACTION: StartWorkflowAction;
export declare const TERMINATE_WORKFLOW_ACTION: TerminateWorkflowAction;
export declare const START_AGENT_ACTION: StartAgentAction;
