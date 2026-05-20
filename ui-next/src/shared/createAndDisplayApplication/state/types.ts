import { AuthHeaders } from "types/common";

export interface AccessKey {
  id: string;
  secret: string;
}

export type CreateAndDisplayApplicationMachineContext = {
  applicationAccessKey?: AccessKey;
  authHeaders?: AuthHeaders;
  applicationName?: string;
  applicationId?: string;
  errorCreatingAppMessage?: string;
};

export enum CreateAndDisplayApplicationMachineEventTypes {
  CREATE_APPLICATION = "CREATE_APPLICATION",
  CLOSE_KEYS_DIALOG = "CLOSE_KEYS_DIALOG",
  RECREATE_KEYS = "RECREATE_KEYS",
}

export type CreateApplicationEvent = {
  type: CreateAndDisplayApplicationMachineEventTypes.CREATE_APPLICATION;
};

export type CloseKeysDialogEvent = {
  type: CreateAndDisplayApplicationMachineEventTypes.CLOSE_KEYS_DIALOG;
};

export type RecreateApplicationEvent = {
  type: CreateAndDisplayApplicationMachineEventTypes.RECREATE_KEYS;
};

export type CreateAndDisplayApplicationEvents =
  | CreateApplicationEvent
  | CloseKeysDialogEvent
  | RecreateApplicationEvent;
