import { Box, Stack, Tooltip } from "@mui/material";
import { useSelector } from "@xstate/react";
import { FunctionComponent, useMemo } from "react";
import fastDeepEqual from "fast-deep-equal";
import { omit } from "lodash";
import { colors } from "theme/tokens/variables";
import Button, { MuiButtonProps } from "components/ui/buttons/MuiButton";
import _isEmpty from "lodash/isEmpty";
import { tryToJson } from "utils/utils";
import ResetIcon from "components/icons/ResetIcon";
import SaveIcon from "components/icons/SaveIcon";
import TrashIcon from "components/icons/TrashIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import { useAuth } from "components/features/auth";

const withFormState =
  (
    ButtonComponent: FunctionComponent<MuiButtonProps>,
    actor: any,
    isTrialExpired: boolean,
  ) =>
  (buttonProps: MuiButtonProps) => {
    const [eventAsJson, originalSource] = useSelector(actor, (state: any) => [
      state.context.eventAsJson,
      state.context.originalSource,
    ]);
    const noChanges = useMemo(
      () => fastDeepEqual(omit(eventAsJson, "action"), originalSource),
      [eventAsJson, originalSource],
    );
    const { name, event } = eventAsJson;
    const emptyValue = [event?.trim(), name?.trim()].some((value) =>
      _isEmpty(value?.trim()),
    );
    const isReset = buttonProps?.role === "reset";
    const disableSave = emptyValue || noChanges || isTrialExpired;
    return (
      <ButtonComponent
        {...buttonProps}
        disabled={isReset ? noChanges : disableSave}
      />
    );
  };

const withEditorState =
  (
    ButtonComponent: FunctionComponent<MuiButtonProps>,
    actor: any,
    isTrialExpired: boolean,
  ) =>
  (buttonProps: MuiButtonProps) => {
    const [editorChanges, originalSource, invalidJson] = useSelector(
      actor,
      (state: any) => [
        state.context.editorChanges,
        state.context.originalSource,
        state.context.couldNotParseJson,
      ],
    );

    const isEmptyValue = useMemo(() => {
      if (!editorChanges) return false;
      const parsedEditorChanges = tryToJson(editorChanges) as {
        name: string;
        event: string;
      };
      const { name, event } = parsedEditorChanges || {};
      return [event?.trim(), name?.trim()].some((value) => _isEmpty(value));
    }, [editorChanges]);

    const noChanges = useMemo(
      () => fastDeepEqual(editorChanges, originalSource),
      [editorChanges, originalSource],
    );
    const isReset = buttonProps?.role === "reset";
    const disableSave =
      noChanges || invalidJson || isEmptyValue || isTrialExpired;
    return (
      <ButtonComponent
        {...buttonProps}
        disabled={isReset ? noChanges : disableSave}
      />
    );
  };

type Props = {
  isConfirmSave?: boolean;
  isConfirmReset?: boolean;
  isSaving?: boolean;
  handleConfirmSaveRequest?: () => void;
  handleCancelRequest?: () => void;
  handleSaveRequest?: () => void;
  handleResetRequest?: () => void;
  isNewEventHandler?: boolean;
  handleDeleteRequest?: () => void;
  service: any;
  disableDeleteBtn: boolean;
};

const EventHandlerButton = ({
  isConfirmSave,
  isSaving,
  handleConfirmSaveRequest,
  handleCancelRequest,
  handleSaveRequest,
  handleResetRequest,
  handleDeleteRequest,
  isNewEventHandler,
  service,
  disableDeleteBtn,
}: Props) => {
  const { isTrialExpired } = useAuth();
  const formActor = service?.children?.get("eventFormMachine");
  const isInForm = useSelector(service, (state: any) =>
    state.matches("idle.form"),
  );

  const SaveResetButton =
    isInForm && formActor
      ? withFormState(Button, formActor, isTrialExpired)
      : withEditorState(Button, service, isTrialExpired);

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
          gap: 2,
        }}
      >
        {(isConfirmSave || isSaving) && (
          <Stack flexDirection="row" gap={1} flexWrap="wrap">
            <Button
              color="secondary"
              onClick={handleCancelRequest}
              startIcon={<XCloseIcon />}
            >
              Cancel
            </Button>
            <Button
              onClick={handleConfirmSaveRequest}
              disabled={isSaving}
              id="confirm-save-event-handler"
              startIcon={<SaveIcon />}
            >
              {isConfirmSave ? "Confirm Save" : "Saving..."}
            </Button>
          </Stack>
        )}
        {!isConfirmSave && !isSaving && (
          <>
            <Stack flexDirection="row" gap={1} flexWrap="wrap">
              {!isNewEventHandler && (
                <Tooltip
                  title={
                    disableDeleteBtn
                      ? ""
                      : "Delete this Event Handler definition"
                  }
                  arrow
                >
                  <Button
                    color="secondary"
                    onClick={handleDeleteRequest}
                    disabled={disableDeleteBtn || isTrialExpired}
                    id="delete-event-handler"
                    startIcon={<TrashIcon />}
                  >
                    Delete
                  </Button>
                </Tooltip>
              )}

              <SaveResetButton
                onClick={handleResetRequest}
                color="secondary"
                role="reset"
                startIcon={<ResetIcon />}
              >
                Reset
              </SaveResetButton>

              <SaveResetButton
                onClick={handleSaveRequest}
                id="save-event-handler"
                startIcon={<SaveIcon />}
              >
                Save
              </SaveResetButton>
            </Stack>
          </>
        )}
      </Box>
    </Box>
  );
};

export default EventHandlerButton;
