import { useActor, useSelector } from "@xstate/react";
import fastDeepEqual from "fast-deep-equal";
import { useContext } from "react";
import { ActorRef } from "xstate";

import { MessageContext } from "components/providers/messageContext";
import {
  TaskDefinitionMachineEvent,
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "pages/definition/task/state/types";
import { newTaskTemplate } from "templates/JSONSchemaWorkflow";
import { PopoverMessage } from "types/Messages";
import { TASK_DIALOGS_MACHINE_ID, TASK_FORM_MACHINE_ID } from "./helpers";

export const useTaskDefinition = (
  actor: ActorRef<TaskDefinitionMachineEvent>,
) => {
  const { setMessage } = useContext(MessageContext);
  // Use send from useActor but don't subscribe to state changes - use selectors instead
  const [, send] = useActor(actor);
  const modifiedTaskDefinition = useSelector(
    actor,
    (state) => state.context.modifiedTaskDefinition,
  );

  const originTaskDefinition = useSelector(
    actor,
    (state) => state.context.originTaskDefinition,
  );

  const originTaskDefinitionString = useSelector(
    actor,
    (state) => state.context.originTaskDefinitionString,
  );

  const modifiedTaskDefinitionString = useSelector(
    actor,
    (state) => state.context.modifiedTaskDefinitionString,
  );

  const taskDefinitions = useSelector(
    actor,
    (state) => state.context.taskDefinitions,
  );

  const originTaskDefinitions = useSelector(
    actor,
    (state) => state.context.originTaskDefinitions,
  );

  const isModified = useSelector(actor, (state) =>
    state.matches("editor.modified"),
  );

  const testInputParameters = useSelector(
    actor,
    (state) => state.context.testInputParameters,
  );

  const testTaskDomain = useSelector(
    actor,
    (state) => state.context.testTaskDomain,
  );

  const testTaskWorkflowId = useSelector(
    actor,
    (state) => state.context.testTaskWorkflowId,
  );

  const couldNotParseJson = useSelector(
    actor,
    (state) => state.context.couldNotParseJson,
  );

  const isDialogOpen = useSelector(actor, (state) =>
    state.matches("editor.dialog"),
  );

  const isFetching = useSelector(actor, (state) =>
    [
      "editor.fetchTaskDefinitions",
      "editor.fetchTaskDefinitionByName",
      "diffEditor.fetchTaskDefinitionByName",
      "diffEditor.createTaskDefinition",
      "diffEditor.updateTaskDefinition",
    ].some(state.matches),
  );

  const isReady = useSelector(actor, (state) =>
    state.matches([TaskDefinitionMachineState.READY]),
  );

  const isContinueCreate = useSelector(
    actor,
    (state) => state.context.isContinueCreate,
  );

  const isNewTaskDef = useSelector(
    actor,
    (state) => state.context.isNewTaskDef,
  );

  const error = useSelector(actor, (state) => state.context.error);

  const numberOfError = useSelector(
    actor,
    (state) => state.context.numberOfError,
  );

  const isEditingName = useSelector(actor, (state) =>
    state.matches("ready.form.editingField.name"),
  );

  const isEditingDescription = useSelector(actor, (state) =>
    state.matches("ready.form.editingField.description"),
  );

  const isConfirmingSave = useSelector(actor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.MAIN_CONTAINER,
      TaskDefinitionMachineState.DIFF_EDITOR,
    ]),
  );

  const isEditingInEditor = useSelector(actor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.MAIN_CONTAINER,
      TaskDefinitionMachineState.EDITOR,
    ]),
  );

  const isEqual = useSelector(
    actor,
    ({
      context: {
        bulkMode,
        mode,
        modifiedTaskDefinitionString,
        originTaskDefinitionString,
        originTaskDefinitions,
        taskDefinitions,
      },
    }) => {
      const isBulkModeEqual = fastDeepEqual(
        originTaskDefinitions,
        taskDefinitions,
      );
      const isJSONStringEqual = fastDeepEqual(
        originTaskDefinitionString,
        modifiedTaskDefinitionString,
      );

      if (mode === "editor") {
        return bulkMode ? isBulkModeEqual : isJSONStringEqual;
      }

      if (isConfirmingSave) {
        return isJSONStringEqual;
      }

      return false;
    },
  );

  // @ts-ignore
  const formActor = actor?.children?.get(TASK_FORM_MACHINE_ID);

  // @ts-ignore
  const dialogActor = actor?.children?.get(TASK_DIALOGS_MACHINE_ID);

  // FUNCTIONS

  const needSyncData = () => {
    send({
      type: TaskDefinitionMachineEventType.NEED_SYNC_DATA_FROM_FORM_MACHINE,
    });
  };

  const handleChangeTaskDefinition = (editorValue: string) => {
    send({
      type: TaskDefinitionMachineEventType.DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION,
      modifiedTaskDefinitionString: editorValue,
    });
  };

  const handleRunTestTask = () => {
    send({
      type: TaskDefinitionMachineEventType.HANDLE_RUN_TEST_TASK,
    });
  };

  const setInputParameters = (inputParameters: string) => {
    send({
      type: TaskDefinitionMachineEventType.SET_INPUT_PARAMETERS,
      inputParameters,
    });
  };

  const setTaskDomain = (domain: string) => {
    send({
      type: TaskDefinitionMachineEventType.SET_TASK_DOMAIN,
      domain,
    });
  };

  // Get additional values needed for convertJSONToString
  const user = useSelector(actor, (state) => state.context.user);
  const bulkMode = useSelector(actor, (state) => state.context.bulkMode);

  const convertJSONToString = () => {
    if (isNewTaskDef) {
      const initialDef = newTaskTemplate(user?.email || "example@email.com");

      return JSON.stringify(bulkMode ? [initialDef] : initialDef, null, 2);
    }

    return JSON.stringify(modifiedTaskDefinition, null, 2);
  };

  const handlePopoverMessage = (popoverMessage: PopoverMessage | null) => {
    setMessage(popoverMessage);
  };

  const saveTaskDefinition = () => {
    send({
      type: TaskDefinitionMachineEventType.SAVE_TASK_DEFINITION,
    });
  };

  const handleDownloadFile = () => {
    send({
      type: TaskDefinitionMachineEventType.EXPORT_TASK_TO_JSON_FILE,
    });
  };

  const setSaveConfirmationOpen = (isContinueCreate = false) => {
    send({
      type: TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN,
      isContinueCreate,
    });
  };

  const setResetConfirmationOpen = () => {
    needSyncData();
    send({
      type: TaskDefinitionMachineEventType.SET_RESET_CONFIRMATION_OPEN,
    });
  };

  const setDeleteConfirmationOpen = () => {
    send({
      type: TaskDefinitionMachineEventType.SET_DELETE_CONFIRMATION_OPEN,
    });
  };

  const cancelConfirmSave = () => {
    send({ type: TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE });
  };

  const closeDialog = () => {
    send({ type: TaskDefinitionMachineEventType.CLOSE_DIALOG });
  };

  const toggleFormMode = (value: boolean) => {
    send({
      type: TaskDefinitionMachineEventType.TOGGLE_FORM_MODE,
      formMode: value,
    });
  };

  return [
    {
      dialogActor,
      error,
      formActor,
      isContinueCreate,
      isDialogOpen,
      isEditingDescription,
      isEditingName,
      isEqual,
      isFetching,
      isReady,
      isModified,
      isNewTaskDef,
      modifiedTaskDefinition,
      modifiedTaskDefinitionString,
      numberOfError,
      originTaskDefinition,
      originTaskDefinitionString,
      originTaskDefinitions,
      saveConfirmationOpen: isConfirmingSave,
      taskDefinitions,
      testInputParameters,
      testTaskDomain,
      testTaskWorkflowId,
      isEditingInEditor,
      isConfirmingSave,
      couldNotParseJson,
    },
    {
      cancelConfirmSave,
      closeDialog,
      convertJSONToString,
      handleChangeTaskDefinition,
      handleDownloadFile,
      handlePopoverMessage,
      handleRunTestTask,
      needSyncData,
      saveTaskDefinition,
      setDeleteConfirmationOpen,
      setInputParameters,
      setResetConfirmationOpen,
      setSaveConfirmationOpen,
      setTaskDomain,
      toggleFormMode,
    },
  ] as const;
};
