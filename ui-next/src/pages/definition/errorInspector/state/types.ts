import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { NodeData } from "reaflow";
import { TaskDef, WorkflowDef, Crumb, CrumbMap, AuthHeaders } from "types";
import { ImportSummary } from "utils/cloudTemplates";

export enum ErrorSeverity {
  WARNING = "WARNING",
  ERROR = "ERROR",
}

export enum ErrorTypes {
  WORKFLOW = "WORKFLOW",
  TASK = "TASK",
  SERVER_ERROR = "SERVER_ERROR",
  RUN_ERROR = "RUN_ERROR",
}

export enum ErrorIds {
  TASK_TYPE_NOT_PRESENT = "task-type-not-present",
  UNKNOWN_TASK_TYPE = "unknown-task-type",
  TASK_REQUIRED_FIELD_MISSING = "task-required-field-missing",
  TASK_REQUIRED_INPUT_PARAMETERS_MISSING = "task-required-input-parameters",
  ALLOWED_VALUES = "non-allowed-values",
  TYPE_ERROR = "type-error",
  GENERIC_ERROR = "generic-error",
  FLOW_ERROR = "flow-error",
  REFERENCE_PROBLEMS = "reference-problems",
  UNREACHABLE_TASK = "unreachable-task",
  SERIALIZATION_ERROR = "serialization-error",
}

export enum ErrorInspectorEventTypes {
  VALIDATE_WORKFLOW_STRING = "VALIDATE_WORKFLOW_STRING",
  VALIDATE_WORKFLOW = "VALIDATE_WORKFLOW",
  VALIDATE_SINGLE_TASK = "VALIDATE_SINGLE_TASK",
  SINGLE_TASK_ERRORS = "SINGLE_TASK_ERRORS",
  REPORT_SERVER_ERROR = "REPORT_SERVER_ERROR",
  REPORT_FLOW_ERROR = "REPORT_FLOW_ERROR",
  REPORT_RUN_ERROR = "REPORT_RUN_ERROR",
  WORKFLOW_WITH_NO_ERRORS = "WORKFLOW_WITH_NO_ERRORS",
  WORKFLOW_HAS_ERRORS = "WORKFLOW_HAS_ERRORS",

  TOGGLE_TASK_ERRORS_VIEWER = "TOGGLE_TASK_ERRORS_VIEWER",
  TOGGLE_WORKFLOW_ERRORS_VIEWER = "TOGGLE_WORKFLOW_ERRORS_VIEWER",

  CLICK_REFERENCE = "CLICK_REFERENCE",

  CLEAN_SERVER_ERRORS = "CLEAN_SERVER_ERRORS",
  CLEAN_RUN_ERRORS = "CLEAN_RUN_ERRORS",
  CLEAN_SERIALIZATION_ERROR = "CLEAN_SERIALIZATION_ERROR",

  REMOVED_TASK_REFERENCES = "REMOVED_TASK_REFERENCES",
  FLOW_FINISHED_RENDERING = "FLOW_FINISHED_RENDERING",

  SET_WORKFLOW = "SET_WORKFLOW",

  UPDATE_SECRETS = "UPDATE_SECRETS",

  TOGGLE_TASK_REFERENCE_ERRORS_VIEWER = "TOGGLE_TASK_REFERENCE_ERRORS_VIEWER",
  TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER = "TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER",

  TOGGLE_ERROR_INSPECTOR = "TOGGLE_ERROR_INSPECTOR",
  SET_ERROR_INSPECTOR_EXPANDED = "SET_ERROR_INSPECTOR_EXPANDED",
  SET_ERROR_INSPECTOR_COLLAPSED = "SET_ERROR_INSPECTOR_COLLAPSED",

  JUMP_TO_FIRST_ERROR = "JUMP_TO_FIRST_ERROR",

  COLLAPSE_INSPECTOR_IF_NO_ERRORS = "COLLAPSE_INSPECTOR_IF_NO_ERRORS",
}

export type ServerValidationError = {
  message?: string;
  path?: string;
  invalidValue?: string;
};

export type TaskHistory = {
  taskPath: string;
  task: TaskDef;
};
export type StoredValidationError = ServerValidationError &
  Partial<TaskHistory>;
export interface ValidationError {
  id: ErrorIds;
  message: string;
  hint?: string;
  taskReferenceName?: string;
  path?: string;
  type: ErrorTypes;
  severity: ErrorSeverity;
  onClickReference?: (data: string) => void;
  taskError?: any;
  validationErrors?: Array<StoredValidationError>;
}

export interface TaskErrors {
  task: TaskDef;
  errors: ValidationError[];
}

export interface SchemaValidationResponse {
  taskErrors: TaskErrors[];
  workflowErrors: ValidationError[];
  tab?: number;
}

export interface SchemaStringValidationResponse extends SchemaValidationResponse {
  currentWf?: Partial<WorkflowDef>;
}

export interface TaskWithUnknownReference {
  task: TaskDef;
  parameters: string[];
  expressions: string[];
  joinOn?: string[];
}

export interface ReferenceProblems {
  workflowReferenceProblems: ValidationError[];
  taskReferencesProblems: TaskErrors[];
  unreachableTaskProblems: TaskErrors[];
}

export interface ErrorInspectorMachineContext
  extends SchemaValidationResponse, ReferenceProblems {
  currentWf?: Partial<WorkflowDef>;
  serverErrors: ValidationError[];
  runWorkflowErrors: ValidationError[];
  crumbMap?: CrumbMap;
  secrets?: Record<string, unknown>[];
  envs?: Record<string, unknown>;
  authHeaders?: AuthHeaders;
  expanded?: boolean;
  importSummary?: ImportSummary;
}

export type ValidateWorkflowStringEvent = {
  type: ErrorInspectorEventTypes.VALIDATE_WORKFLOW_STRING;
  workflowChanges: string;
};

export type ValidateWorkflowEvent = {
  type: ErrorInspectorEventTypes.VALIDATE_WORKFLOW;
  workflow: Partial<WorkflowDef>;
};

export type UpdateSecretsEvent = {
  type: ErrorInspectorEventTypes.UPDATE_SECRETS;
  data?: { secrets: Record<string, unknown>[]; envs: Record<string, unknown> };
};

export type WorkflowWithNoErrorsEvent = {
  type: ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS;
  workflow: WorkflowDef;
};

export type WorkflowHasErrorsEvent = {
  type: ErrorInspectorEventTypes.WORKFLOW_HAS_ERRORS;
  errors: SchemaValidationResponse;
  workflow: WorkflowDef;
};

export type FlowReportedErrorEvent = {
  type: ErrorInspectorEventTypes.REPORT_FLOW_ERROR;
  text: string;
};

export type ReportRunErrorEvent = {
  type: ErrorInspectorEventTypes.REPORT_RUN_ERROR;
  text: string;
};

export type ReportServerErrorEvent = {
  type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR;
  text: string;
  validationErrors?: ServerValidationError[];
};

export type ValidateSingleTaskEvent = {
  type: ErrorInspectorEventTypes.VALIDATE_SINGLE_TASK;
  task: TaskDef;
};

export type FlowFinishedRenderingEvent = {
  type: ErrorInspectorEventTypes.FLOW_FINISHED_RENDERING;
  nodes: NodeData<NodeTaskData<TaskDef>>[];
};

export interface TaskReferenceReportingParameters {
  existingTaskReferences: string[];
  lastTaskRoute: TaskDef[];
}

export type ReportTaskReferencesEvent = {
  type: ErrorInspectorEventTypes.REMOVED_TASK_REFERENCES;
  removedTask: TaskDef;
  lastTaskCrumbs: Crumb[];
  workflow: Partial<WorkflowDef>;
};

export type SetWorkflowEvent = {
  type: ErrorInspectorEventTypes.SET_WORKFLOW;
  workflow: Partial<WorkflowDef>;
};

export type ToggleTaskErrorViewerEvent = {
  type: ErrorInspectorEventTypes.TOGGLE_TASK_ERRORS_VIEWER;
};

export type ToggleWorkflowErrorViewerEvent = {
  type: ErrorInspectorEventTypes.TOGGLE_WORKFLOW_ERRORS_VIEWER;
};

export type ToggleClickReference = {
  type: ErrorInspectorEventTypes.CLICK_REFERENCE;
  referenceText: string;
};

export type ToggleTaskReferenceErrorViewerEvent = {
  type: ErrorInspectorEventTypes.TOGGLE_TASK_REFERENCE_ERRORS_VIEWER;
};

export type ToggleWorkflowReferenceErrorViewerEvent = {
  type: ErrorInspectorEventTypes.TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER;
};

export type CleanServerErrorsEvent = {
  type: ErrorInspectorEventTypes.CLEAN_SERVER_ERRORS;
};

export type CleanSerializationErrorEvent = {
  type: ErrorInspectorEventTypes.CLEAN_SERIALIZATION_ERROR;
};

export type CleanRunErrorsEvent = {
  type: ErrorInspectorEventTypes.CLEAN_RUN_ERRORS;
};

export type ToggleErrorInspectorEvent = {
  type: ErrorInspectorEventTypes.TOGGLE_ERROR_INSPECTOR;
};

export type SetErrorInspectorExpandedEvent = {
  type: ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_EXPANDED;
};

export type SetErrorInspectorCollapsedEvent = {
  type: ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_COLLAPSED;
};

export interface RefractorObject {
  [key: string]: string;
}

export type JumpToFirstErrorEvent = {
  type: ErrorInspectorEventTypes.JUMP_TO_FIRST_ERROR;
};

export type CollapseInspectorIfNoErrorsEvent = {
  type: ErrorInspectorEventTypes.COLLAPSE_INSPECTOR_IF_NO_ERRORS;
};

export type ErrorInspectorMachineEvents =
  | ReportTaskReferencesEvent
  | ValidateWorkflowStringEvent
  | FlowReportedErrorEvent
  | ToggleTaskReferenceErrorViewerEvent
  | ToggleWorkflowReferenceErrorViewerEvent
  | FlowFinishedRenderingEvent
  | CleanServerErrorsEvent
  | ReportServerErrorEvent
  | ToggleTaskErrorViewerEvent
  | ToggleWorkflowErrorViewerEvent
  | ValidateWorkflowEvent
  | SetWorkflowEvent
  | ValidateSingleTaskEvent
  | UpdateSecretsEvent
  | ToggleClickReference
  | ToggleErrorInspectorEvent
  | SetErrorInspectorExpandedEvent
  | JumpToFirstErrorEvent
  | SetErrorInspectorCollapsedEvent
  | ReportRunErrorEvent
  | CleanRunErrorsEvent
  | CleanSerializationErrorEvent
  | CollapseInspectorIfNoErrorsEvent;
