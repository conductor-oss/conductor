import _maxBy from "lodash/maxBy";
import { WorkflowDef } from "types/WorkflowDef";
import { logger, tryToJson } from "utils";
import { ActorRef, assign, DoneInvokeEvent, sendParent } from "xstate";
import { cancel, raise, sendTo } from "xstate/lib/actions";
import {
  ErrorInspectorEventTypes,
  ErrorInspectorMachineEvents,
} from "../../errorInspector/state";
import {
  EditEvent,
  SaveWorkflowMachineContext,
  SaveWorkflowMachineEventTypes,
} from "./types";

export const editChanges = assign<SaveWorkflowMachineContext, EditEvent>({
  editorChanges: (_context, { changes }) => changes,
});

export const debounceEditEvent = raise<SaveWorkflowMachineContext, EditEvent>(
  (__, { changes }) => ({
    type: SaveWorkflowMachineEventTypes.EDIT_EVT,
    changes,
  }),
  { delay: 300, id: "debounce_edit_event" },
);

export const cancelDebounceEditChanges = cancel("debounce_edit_event");

export const updateWorkflowVersionAndName = assign<SaveWorkflowMachineContext>(
  ({ editorChanges }) => {
    const workflowJson = tryToJson<{ name: string; version: number }>(
      editorChanges,
    );
    return {
      currentVersion: workflowJson?.version,
      workflowName: workflowJson?.name,
    };
  },
);

export const reportServerErrors = sendTo<
  SaveWorkflowMachineContext,
  DoneInvokeEvent<{
    text: string;
    validationErrors: { message?: string; path?: string }[];
  }>,
  ActorRef<ErrorInspectorMachineEvents>
>(
  ({ errorInspectorMachine }) => errorInspectorMachine!,
  (__, { data }) => {
    return {
      type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR,
      text: data.text,
      validationErrors: data?.validationErrors,
    };
  },
);

export const cleanServerErrors = sendTo<
  SaveWorkflowMachineContext,
  DoneInvokeEvent<{ text: string }>,
  ActorRef<ErrorInspectorMachineEvents>
>(
  ({ errorInspectorMachine }) => errorInspectorMachine!,
  (__context, _event) => {
    return {
      type: ErrorInspectorEventTypes.CLEAN_SERVER_ERRORS,
    };
  },
);

export const sendSuccessSave = sendParent<SaveWorkflowMachineContext, any>(
  (context) => {
    return {
      type: SaveWorkflowMachineEventTypes.SAVED_SUCCESSFUL,
      workflow: tryToJson(context.editorChanges), // TODO send to errorInspector instead.
      isNewWorkflow: false,
      workflowName: context.workflowName,
      currentVersion: context.currentVersion,
      isContinueCreate: context.isContinueCreate,
    };
  },
);

export const sendCancelSave = sendParent<SaveWorkflowMachineContext, any>(
  (context) => {
    try {
      const workflow = JSON.parse(context.editorChanges);

      return {
        type: SaveWorkflowMachineEventTypes.SAVED_CANCELLED,
        workflow,
        isNewWorkflow: context.isNewWorkflow,
      };
    } catch {
      logger.info("Can't parse the json. so returning undefined");
      return {
        type: SaveWorkflowMachineEventTypes.SAVED_CANCELLED,
        workflow: undefined,
        isNewWorkflow: context.isNewWorkflow,
      };
    }
  },
);

export const checkForErrorsInWorkflow = sendTo<
  SaveWorkflowMachineContext,
  any,
  ActorRef<ErrorInspectorMachineEvents>
>(
  ({ errorInspectorMachine }) => errorInspectorMachine!,
  ({ editorChanges }) => ({
    type: ErrorInspectorEventTypes.VALIDATE_WORKFLOW_STRING,
    workflowChanges: editorChanges,
  }),
);

export const grabLastVersionAndPersistAsNew = assign<
  SaveWorkflowMachineContext,
  DoneInvokeEvent<Array<WorkflowDef>>
>({
  currentVersion: (context, { data }) => {
    if (data && data.length > 0) {
      const latestVersion = _maxBy(data, "version")?.version;
      return latestVersion ? latestVersion : context.currentVersion;
    }
    return context.currentVersion;
  },
});
