import { AuthHeaders } from "types/common";

export enum StartSubWfNameVersionTypes {
  SELECT_WORKFLOW_NAME = "SELECT_WORKFLOW_NAME",
}
export enum StartSubWfNameVersionStates {
  IDLE = "IDLE",
  HANDLE_SELECT_WORKFLOW_NAME = "HANDLE_SELECT_WORKFLOW_NAME",
  GO_BACK_TO_IDLE = "GO_BACK_TO_IDLE",
}

export type SelectWorkflowNameEvent = {
  type: StartSubWfNameVersionTypes.SELECT_WORKFLOW_NAME;
  name: string;
};

export type StartSubWfNameVersionEvents = SelectWorkflowNameEvent;

export interface StartSubWfNameVersionMachineContext {
  authHeaders: AuthHeaders;
  workflowName: string;
  fetchedNamesAndVersions?: Record<string, number[]>;
  wfNameOptions?: string[];
  availableVersions?: number[];
}
