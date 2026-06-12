import { WorkflowExecution, WorkflowExecutionStatus } from "./Execution";
import { TaskDef } from "./common";

export interface OpenTestTaskButtonProps {
  task: string | any;
  maxHeight: number;
  disabled?: boolean;
  showForm?: boolean;
  tasksList?: Partial<TaskDef>[];
}

export interface TestTaskButtonProps {
  task: string | any;
  maxHeight: number;
  onDismiss: () => void;
  showForm: boolean;
  tasksList?: Partial<TaskDef>[];
}

export interface TestTaskProps {
  taskModel: Record<string, unknown>;
  onChangeModel: (modelChanges: Record<string, unknown>) => void;
  domain: string;
  onChangeDomain: (value: string) => void;
  value: Record<string, unknown>;
  maxHeight: number;
  handleRunTestTask: () => void;
  isInProgress: boolean;
  onDismiss: () => void;
  testExecutionId?: string;
  testedTaskExecutionResult: WorkflowExecution;
  showForm: boolean;
}

export interface TestControlsProps {
  taskModel: Record<string, unknown>;
  value: Record<string, unknown>;
  isInProgress: boolean;
  handleRunTestTask: () => void;
  onChangeModel: (modelChanges: Record<string, unknown>) => void;
  domain: string;
  onChangeDomain: (value: string) => void;
  showForm: boolean;
}

export interface TestOutputProps {
  onChangeModel: (modelChanges: Record<string, unknown>) => void;
  testedTaskExecutionResult: WorkflowExecution;
  status: WorkflowExecutionStatus;
  testExecutionId?: string;
}

export interface FormSectionProps {
  extractedJsonVariables: Record<string, unknown>;
  onChangeModel: (modelChanges: Record<string, unknown>) => void;
  value: Record<string, unknown>;
  domain: string;
  onChangeDomain: (value: string) => void;
}

export interface JsonSectionProps {
  handleJSONChange: (newValue: string) => void;
  taskModel: Record<string, unknown>;
  value: Record<string, unknown>;
  domain: string;
  onChangeDomain: (value: string) => void;
}
