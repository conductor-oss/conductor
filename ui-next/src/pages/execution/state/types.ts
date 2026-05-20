import { ActorRef, DoneInvokeEvent } from "xstate";
import {
  FlowEvents,
  SelectNodeEvent,
} from "components/features/flow/state/types";
import {
  TaskDef,
  ExecutedData,
  TaskType,
  AuthHeaders,
  WorkflowDef,
  User,
  WorkflowExecution,
  DoWhileSelection,
  ExecutionTask,
} from "types";
import { StatusMap } from "./StatusMapTypes";
import { SetSelectedTaskEvent } from "../RightPanel/state/types";

export enum COUNT_DOWN_TYPE {
  INFINITE = -1,
}

export enum CountdownEventTypes {
  TICK = "TICK",
  DISABLE = "DISABLE",
  ENABLE = "ENABLE",
  FORCE_FINISH = "FORCE_FINISH",
  UPDATE_DURATION = "UPDATE_DURATION",
}

export enum ExecutionActionTypes {
  UPDATE_EXECUTION = "updateExecution",
  REFETCH = "refetch",
  EXPAND_DYNAMIC_TASK = "expandDynamicTask",
  COLLAPSE_DYNAMIC_TASK = "collapseDynamicTask",
  CLEAR_ERROR = "clearError",
  RESTART_EXECUTION = "restartExecution",
  RETRY_EXECUTION = "retryExecution",
  TERMINATE_EXECUTION = "terminateExecution",
  RESUME_EXECUTION = "resumeExecution",
  PAUSE_EXECUTION = "pauseExecution",
  CHANGE_EXECUTION_TAB = "CHANGE_EXECUTION_TAB",
  UPDATE_DURATION = "UPDATE_DURATION",
  FETCH_FOR_LOGS = "FETCH_FOR_LOGS",
  CLOSE_RIGHT_PANEL = "CLOSE_RIGHT_PANEL",
  EXECUTION_UPDATED = "EXECUTION_UPDATED",
  REPORT_FLOW_ERROR = "REPORT_FLOW_ERROR",
  UPDATE_VARIABLES = "UPDATE_VARIABLES",
  UPDATE_TASKID_IN_URL = "UPDATE_TASK_ID_IN_URL",
  SET_DO_WHILE_ITERATION = "SET_DO_WHILE_ITERATION",
  UPDATE_SELECTED_TASK = "UPDATE_SELECTED_TASK",
  UPDATE_QUERY_PARAM = "UPDATE_QUERY_PARAM",
  TOGGLE_ASSISTANT_PANEL = "TOGGLE_ASSISTANT_PANEL",
}

export enum ExecutionTabs {
  DIAGRAM_TAB = "diagram",
  TASK_LIST_TAB = "taskList",
  TIMELINE_TAB = "timeLine",
  WORKFLOW_INTROSPECTION = "workflowIntrospection",
  SUMMARY_TAB = "summary",
  WORKFLOW_INPUT_OUTPUT_TAB = "workflowInputOutput",
  JSON_TAB = "json",
  VARIABLES_TAB = "variables",
  TASKS_TO_DOMAIN_TAB = "tasksToDomain",
}
export interface CountdownContext {
  duration: number;
  elapsed: number;
  executionStatus?: string;
  countdownType?: COUNT_DOWN_TYPE;
  isDisabled?: boolean;
}

type TickEvent = {
  type: CountdownEventTypes.TICK;
};

type DisableEvent = {
  type: CountdownEventTypes.DISABLE;
};

type EnableEvent = {
  type: CountdownEventTypes.ENABLE;
};

type ForceEndEvent = {
  type: CountdownEventTypes.FORCE_FINISH;
};

export type UpdateDurationEvent = {
  type:
    | ExecutionActionTypes.UPDATE_DURATION
    | CountdownEventTypes.UPDATE_DURATION;
  duration?: number;
  countdownType?: COUNT_DOWN_TYPE;
  isDisabled?: boolean;
};

export type CountdownEvents =
  | TickEvent
  | DisableEvent
  | EnableEvent
  | ForceEndEvent
  | UpdateDurationEvent
  | { type: "" }; // TODO use always instead since this is deprecated

export enum ErrorSeverity {
  INFO = "info",
  ERROR = "error",
}

export enum MessageSeverity {
  SUCCESS = "success",
}

export interface TaskDefExecutionContext extends TaskDef {
  executionData: ExecutedData;
  forkTasks?: Array<TaskDefExecutionContext[]>;
  decisionCases?: Record<string, TaskDefExecutionContext[]>;
  loopOver?: TaskDefExecutionContext[];
  type: TaskType;
}

export interface WorkflowDefExecutionContext extends WorkflowDef {
  tasks: TaskDefExecutionContext[];
}

export type ErrorType = {
  severity: ErrorSeverity;
  text: string;
};

export type MessageType = {
  severity: MessageSeverity;
  text: string;
};

export interface ExecutionMachineContext {
  execution?: WorkflowExecution;
  executionId?: string;
  flowChild?: ActorRef<FlowEvents>;
  expandedDynamic: string[];
  workflowDefinition?: Partial<WorkflowDef>;
  executionStatusMap?: StatusMap;
  error?: ErrorType;
  authHeaders?: AuthHeaders;
  currentTab: ExecutionTabs;
  duration?: number;
  countdownType?: COUNT_DOWN_TYPE;
  isDisabledCountdown?: boolean;
  currentUserInfo?: User;
  message?: MessageType;
  doWhileSelection?: DoWhileSelection[];
  selectedTask?: ExecutionTask;
  selectedTaskReferenceName?: string;
  selectedTaskId?: string;
  isAssistantPanelOpen: boolean;
}

export type UpdateExecutionEvent = {
  type: ExecutionActionTypes.UPDATE_EXECUTION;
  executionId: string;
};

export type ClearErrorEvent = {
  type: ExecutionActionTypes.CLEAR_ERROR;
};

export type PersistErrorEvent = {
  type: ExecutionActionTypes.REPORT_FLOW_ERROR;
  text: string;
  severity: ErrorSeverity;
};

export type RefetchEvent = {
  type: ExecutionActionTypes.REFETCH;
};

export type ExpandDynamicTaskEvent = {
  type: ExecutionActionTypes.EXPAND_DYNAMIC_TASK;
  taskReferenceName: string;
};

export type CollapseDynamicTaskEvent = {
  type: ExecutionActionTypes.COLLAPSE_DYNAMIC_TASK;
  taskReferenceName: string;
};

export type SetDoWhileIterationEvent = {
  type: ExecutionActionTypes.SET_DO_WHILE_ITERATION;
  data: DoWhileSelection;
};

export type RestartExecutionEvent = {
  type: ExecutionActionTypes.RESTART_EXECUTION;
  options?: Record<string, string>;
};

export type RetryExecutionEvent = {
  type: ExecutionActionTypes.RETRY_EXECUTION;
  options?: Record<string, string>;
};

export type TerminateExecutionEvent = {
  type: ExecutionActionTypes.TERMINATE_EXECUTION;
};

export type ResumeExecutionEvent = {
  type: ExecutionActionTypes.RESUME_EXECUTION;
};

export type PauseExecutionEvent = {
  type: ExecutionActionTypes.PAUSE_EXECUTION;
};

export type ChangeExecutionTabEvent = {
  type: ExecutionActionTypes.CHANGE_EXECUTION_TAB;
  tab: ExecutionTabs;
};

export type CloseRightPanelEvent = {
  type: ExecutionActionTypes.CLOSE_RIGHT_PANEL;
};

export type FetchForLogsEvent = {
  type: ExecutionActionTypes.FETCH_FOR_LOGS;
};

export type ExecutionUpdatedEvent = {
  type: ExecutionActionTypes.EXECUTION_UPDATED;
};

export type UpdateVariablesEvent = {
  type: ExecutionActionTypes.UPDATE_VARIABLES;
  data: string;
};

export type UpdateSelectedTaskEvent = {
  type: ExecutionActionTypes.UPDATE_SELECTED_TASK;
  selectedTask: ExecutionTask;
};

export type UpdateQueryParamEvent = {
  type: ExecutionActionTypes.UPDATE_QUERY_PARAM;
  taskReferenceName: string;
};

export type ToggleAssistantPanelEvent = {
  type: ExecutionActionTypes.TOGGLE_ASSISTANT_PANEL;
};

export type ExecutionMachineEvents =
  | UpdateExecutionEvent
  | ExecutionUpdatedEvent
  | RefetchEvent
  | ChangeExecutionTabEvent
  | UpdateDurationEvent
  | ExpandDynamicTaskEvent
  | CloseRightPanelEvent
  | CollapseDynamicTaskEvent
  | SelectNodeEvent
  | ClearErrorEvent
  | RestartExecutionEvent
  | RetryExecutionEvent
  | TerminateExecutionEvent
  | ResumeExecutionEvent
  | PauseExecutionEvent
  | FetchForLogsEvent
  | SetSelectedTaskEvent
  | PersistErrorEvent
  | UpdateVariablesEvent
  | SetDoWhileIterationEvent
  | UpdateSelectedTaskEvent
  | UpdateQueryParamEvent
  | ToggleAssistantPanelEvent
  | DoneInvokeEvent<any>;
