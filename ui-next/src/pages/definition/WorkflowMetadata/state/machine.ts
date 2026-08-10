import { createMachine } from "xstate";
import * as services from "./services";

import * as actions from "./actions";
import {
  WorkflowMetadataMachineEventTypes,
  WorkflowMetadataMachineContext,
  WorkflowMetadataEvents,
} from "./types";
import * as guards from "./guards";
import { LocalCopyMachineEventTypes } from "../../ConfirmLocalCopyDialog/state/types";
import { createAndDisplayApplicationMachine } from "shared/createAndDisplayApplication/state/machine";

export const workflowMetadataMachine = createMachine<
  WorkflowMetadataMachineContext,
  WorkflowMetadataEvents
>(
  {
    id: "workflowMetadataEditorMachine",
    predictableActionArguments: true,
    context: {
      metadataChanges: {},
      editableFields: [],
      editableFieldActors: [],
      childActorsMachineName: "editInPlaceMachine", // Will be provided with the name of the machine to spawn the actors with.
    },
    on: {
      [LocalCopyMachineEventTypes.USE_LOCAL_COPY_WORKFLOW]: {
        actions: ["updateLocalCopy", "notifyActors"],
      },
      [WorkflowMetadataMachineEventTypes.FORCE_WORKFLOW]: {
        actions: ["updateLocalCopy", "notifyActors"],
        cond: "hasMetadataChanges",
      },
    },
    type: "parallel",
    states: {
      fieldEdition: {
        initial: "init",
        states: {
          init: {
            entry: "spawnFieldActors",
            always: "editingEnabled",
          },
          editingEnabled: {
            tags: ["editingEnabled"],
            on: {
              [WorkflowMetadataMachineEventTypes.UPDATE_METADATA]: {
                actions: ["persistPartialMetaDataChanges", "syncWithParent"],
              },
              [WorkflowMetadataMachineEventTypes.DISABLE_EDITING]: {
                target: "editingDisabled",
                actions: ["forwardActionToActors"],
              },
            },
          },
          editingDisabled: {
            tags: ["editingDisabled"],
            on: {
              [WorkflowMetadataMachineEventTypes.ENABLE_EDITING]: {
                target: "editingEnabled",
              },
              [WorkflowMetadataMachineEventTypes.WORKFLOW_CHANGED]: {
                actions: ["updateLocalCopy", "notifyActors"],
                cond: "hasMetadataChanges",
              },
            },
          },
        },
      },
      fastApp: {
        tags: ["fastAppCreation"],
        invoke: {
          id: "createAndDisplayApplicationMachine",
          src: createAndDisplayApplicationMachine,
          data: {
            applicationName: (context: WorkflowMetadataMachineContext) =>
              context.metadataChanges.name,
            authHeaders: (context: WorkflowMetadataMachineContext) =>
              context.authHeaders,
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
    services: services as any,
  },
);
