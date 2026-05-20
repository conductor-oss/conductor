export type SelectedWorkflowType = {
  name: string;
  version: number | string;
};

export type DeletedWfNameType = string | undefined;

export type DeletedWfVersionType = number | undefined;

export type ParsedSelectedWorkflowType = SelectedWorkflowType | undefined;

export enum IdempotencyStrategyEnum {
  FAIL = "FAIL",
  RETURN_EXISTING = "RETURN_EXISTING",
  FAIL_ON_RUNNING = "FAIL_ON_RUNNING",
}
