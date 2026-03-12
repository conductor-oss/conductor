import { State } from "xstate";
import { DefinitionMachineContext } from "../state";

export const versionSelector = (state: State<DefinitionMachineContext>) =>
  state.context.currentVersion;

export const versionsSelector = (state: State<DefinitionMachineContext>) =>
  state.context.workflowVersions;

export const isSaveRequestSelector = (state: State<DefinitionMachineContext>) =>
  state.hasTag("saveRequest");
