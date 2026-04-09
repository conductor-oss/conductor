import { Box, Stack, Tooltip } from "@mui/material";
import { useSelector } from "@xstate/react";
import Button, { MuiButtonProps } from "components/ui/buttons/MuiButton";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import DownloadIcon from "components/icons/DownloadIcon";
import ResetIcon from "components/icons/ResetIcon";
import SaveIcon from "components/icons/SaveIcon";
import TrashIcon from "components/icons/TrashIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import fastDeepEqual from "fast-deep-equal";
import { TaskDefinitionFormMachineEvent } from "pages/definition/task/form/state/types";
import { TASK_FORM_MACHINE_ID } from "pages/definition/task/state/helpers";
import { useTaskDefinition } from "pages/definition/task/state/hook";
import {
  TaskDefinitionButtonsProps,
  TaskDefinitionMachineEvent,
  TaskDefinitionMachineState,
} from "pages/definition/task/state/types";
import { FunctionComponent, useMemo } from "react";
import { useAuth } from "components/features/auth";
import { colors } from "theme/tokens/variables";
import { ActorRef } from "xstate";
import { OpenTestTaskButton } from "../EditorPanel/TaskFormTab/forms/TestTaskButton/OpenTestTaskButton";

// Hoc to get around state for buttons
const withFormState =
  (
    ButtonComponent: FunctionComponent<MuiButtonProps>,
    actor: ActorRef<TaskDefinitionFormMachineEvent>,
    isTrialExpired: boolean,
  ) =>
  (buttonProps: MuiButtonProps) => {
    const [modifiedTaskDefinition, originTaskDefinition, isNewTaskDef] =
      useSelector(actor, (state) => [
        state.context.modifiedTaskDefinition,
        state.context.originTaskDefinition,
        state.context.isNewTaskDef,
      ]);
    const noChanges = useMemo(
      () => fastDeepEqual(modifiedTaskDefinition, originTaskDefinition),
      [modifiedTaskDefinition, originTaskDefinition],
    );
    const isReset = buttonProps?.role === "reset";
    const resetDisabledConditions = noChanges;
    const saveDisabledConditions =
      (!isNewTaskDef && noChanges) || isTrialExpired;
    const noDescription = !(modifiedTaskDefinition.description ?? "").trim();

    return (
      <ButtonComponent
        {...buttonProps}
        disabled={
          isReset
            ? resetDisabledConditions
            : saveDisabledConditions || noDescription
        }
      />
    );
  };

const withEditorState =
  (
    ButtonComponent: FunctionComponent<MuiButtonProps>,
    actor: ActorRef<TaskDefinitionMachineEvent>,
    isTrialExpired: boolean,
  ) =>
  (buttonProps: MuiButtonProps) => {
    const [
      modifiedTaskDefinition,
      originTaskDefinition,
      isNewTaskDef,
      jsonInvalid,
    ] = useSelector(actor, (state) => [
      state.context.modifiedTaskDefinition,
      state.context.originTaskDefinition,
      state.context.isNewTaskDef,
      state.context.couldNotParseJson,
    ]);
    const noChanges = useMemo(
      () => fastDeepEqual(modifiedTaskDefinition, originTaskDefinition),
      [modifiedTaskDefinition, originTaskDefinition],
    );

    const isReset = buttonProps?.role === "reset";
    const resetDisabledConditions = noChanges;
    const saveDisabledConditions =
      jsonInvalid || (!isNewTaskDef && noChanges) || isTrialExpired;
    const noDescription = !(modifiedTaskDefinition.description ?? "").trim();

    return (
      <ButtonComponent
        {...buttonProps}
        disabled={
          isReset
            ? resetDisabledConditions
            : saveDisabledConditions || noDescription
        }
      />
    );
  };

const TaskDefinitionButtons = ({
  taskDefActor,
}: TaskDefinitionButtonsProps) => {
  const [
    {
      isContinueCreate,
      isNewTaskDef,
      saveConfirmationOpen,
      couldNotParseJson,
      modifiedTaskDefinition,
    },
    {
      cancelConfirmSave,
      handleDownloadFile,
      saveTaskDefinition,
      setDeleteConfirmationOpen,
      setResetConfirmationOpen,
      setSaveConfirmationOpen,
    },
  ] = useTaskDefinition(taskDefActor);

  const { isTrialExpired } = useAuth();

  const isInForm = useSelector(taskDefActor, (state) =>
    state.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.MAIN_CONTAINER,
      TaskDefinitionMachineState.FORM,
    ]),
  );

  // @ts-ignore
  const formActor = taskDefActor?.children?.get(TASK_FORM_MACHINE_ID);

  const SaveResetButton =
    isInForm && formActor
      ? withFormState(Button, formActor, isTrialExpired)
      : withEditorState(Button, taskDefActor, isTrialExpired);

  const saveSplitButtonOptions = [
    {
      id: "task-save-and-create-new-btn",
      label: "Save & Create New",
      onClick: () => setSaveConfirmationOpen(true),
    },
  ];

  const suffix = Math.random().toString(36).substring(2, 5);
  const taskDefinition = {
    name: modifiedTaskDefinition?.name,
    taskReferenceName: `test_task_${modifiedTaskDefinition?.name}_${suffix}`,
    type: "SIMPLE",
    inputParameters: {},
  };

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        color: (theme) =>
          theme.palette?.mode === "dark" ? colors.gray14 : undefined,
        backgroundColor: (theme) =>
          theme.palette?.mode === "dark" ? colors.gray00 : colors.gray14,
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexGrow: 2,
          justifyContent: "flex-start",
          alignItems: "center",
          borderColor: (theme) =>
            theme.palette?.mode === "dark" ? colors.gray03 : colors.gray12,
        }}
      >
        {saveConfirmationOpen ? (
          <Stack flexDirection="row" gap={1} flexWrap="wrap">
            <Button
              id="task-cancel-btn"
              color="secondary"
              onClick={cancelConfirmSave}
              startIcon={<XCloseIcon />}
            >
              Cancel
            </Button>
            <Button
              id="task-confirm-save-btn"
              onClick={saveTaskDefinition}
              disabled={couldNotParseJson}
              startIcon={<SaveIcon />}
            >
              {isContinueCreate ? "Confirm Save & Create New" : "Confirm Save"}
            </Button>
          </Stack>
        ) : (
          <Stack
            flexDirection="row"
            gap={1}
            flexWrap="wrap"
            alignItems={"center"}
          >
            {!isNewTaskDef && (
              <Tooltip
                title="Delete this task definition. Workflows that depend on this task will not complete."
                arrow
              >
                <Button
                  id="task-delete-btn"
                  variant="text"
                  onClick={setDeleteConfirmationOpen}
                  startIcon={<TrashIcon />}
                  disabled={isTrialExpired}
                >
                  Delete
                </Button>
              </Tooltip>
            )}

            <SaveResetButton
              id="task-reset-btn"
              onClick={setResetConfirmationOpen}
              variant="text"
              role="reset"
              startIcon={<ResetIcon />}
            >
              Reset
            </SaveResetButton>

            <Button
              id="task-download-btn"
              onClick={handleDownloadFile}
              variant="text"
              startIcon={<DownloadIcon />}
            >
              Download
            </Button>
            <Box pr={2}>
              <OpenTestTaskButton
                task={taskDefinition}
                maxHeight={500}
                disabled={isNewTaskDef || isTrialExpired}
                showForm={false}
              />
            </Box>

            {isNewTaskDef ? (
              <SplitButton
                startIcon={<SaveIcon />}
                id="task-save-btn"
                options={saveSplitButtonOptions}
                primaryOnClick={() => setSaveConfirmationOpen(false)}
                tooltip="Save this definition"
                data-testid="task-definition-save-button"
                disabled={isTrialExpired}
              >
                Save
              </SplitButton>
            ) : (
              <SaveResetButton
                id="task-save-btn"
                onClick={() => setSaveConfirmationOpen(false)}
                startIcon={<SaveIcon />}
              >
                Save
              </SaveResetButton>
            )}
          </Stack>
        )}
      </Box>
    </Box>
  );
};
export default TaskDefinitionButtons;
