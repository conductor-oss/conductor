import Stack from "@mui/material/Stack";
import _isEmpty from "lodash/isEmpty";
import { FunctionComponent, useState } from "react";
import { ActorRef } from "xstate";
import { Key } from "ts-key-enum";
import { useHotkeys } from "react-hotkeys-hook";
import _debounce from "lodash/debounce";

import {
  ButtonTooltip,
  ButtonTooltipProps,
} from "components/ui/buttons/ButtonTooltip";
import DownloadIcon from "components/icons/DownloadIcon";
import ResetIcon from "components/icons/ResetIcon";
import SaveIcon from "components/icons/SaveIcon";
import TrashIcon from "components/icons/TrashIcon";

import { exportObjToFile } from "utils";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";
import { useWorkflowChanges } from "../state/useMadeChanges";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { HOT_KEYS_WORKFLOW_DEFINITION } from "utils/constants/common";
import { UnderlinedText } from "components/ui/UnderlinedText";
import { useAuth } from "components/features/auth";
import { RunWorkflowButton } from "./RunWorkflowButton";

export interface HeaderActionButtonsProps {
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
}
export const HeadActionButtons: FunctionComponent<HeaderActionButtonsProps> = ({
  definitionActor: service,
}) => {
  const { isTrialExpired } = useAuth();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSaveRequest = () =>
    service.send({ type: DefinitionMachineEventTypes.SAVE_EVT });

  const handleSaveAsNewVersionRequest = () => {
    service.send({
      type: DefinitionMachineEventTypes.SAVE_EVT,
      isNewVersion: true,
    });
  };
  const handleResetRequest = () =>
    service.send({ type: DefinitionMachineEventTypes.RESET_EVT });

  const handleDeleteRequest = () =>
    service.send({ type: DefinitionMachineEventTypes.DELETE_EVT });

  const { madeChanges, isNewWorkflow, workflowChanges } =
    useWorkflowChanges(service);

  const emptyTaskList = _isEmpty(workflowChanges?.tasks);

  const handleDownloadFile = () => {
    exportObjToFile({
      data: workflowChanges,
      fileName: `${workflowChanges.name || "new"}_${
        workflowChanges.version
      }.json`,
      type: `application/json`,
    });
  };

  const handleSaveAndRunRequest = () => {
    service.send({ type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN });
  };

  const handleSaveAndCreateNewRequest = () => {
    service.send({
      type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW,
    });
  };

  const buttons: ButtonTooltipProps[] = [
    {
      id: "head-action-delete-btn",
      variant: "text",
      tooltip:
        "Delete this version of the workflow definition, previous executions will not be remove (Ctrl D)",
      disabled: isNewWorkflow || isTrialExpired,
      onClick: handleDeleteRequest,
      "data-testid": "workflow-definition-delete-button",
      sx: { color: (theme) => theme.palette.error.main },
      startIcon: <TrashIcon />,
      children: <UnderlinedText text="Delete" underlinedIndexes={[0]} />,
    },
    {
      id: "head-action-reset-btn",
      variant: "text",
      tooltip: "Reset the editor content to the last saved version (Ctrl R)",
      disabled: !madeChanges || emptyTaskList,
      onClick: handleResetRequest,
      "data-testid": "workflow-definition-reset-button",
      startIcon: <ResetIcon />,
      children: <UnderlinedText text="Reset" underlinedIndexes={[0]} />,
      sx: { color: (theme) => theme.palette.error.main },
    },
    {
      id: "head-action-download-btn",
      variant: "text",
      tooltip: "Download JSON as file  (Ctrl W)",
      disabled: false,
      onClick: handleDownloadFile,
      "data-testid": "workflow-definition-download-button",
      startIcon: <DownloadIcon />,
      children: <UnderlinedText text="Download" underlinedIndexes={[2]} />,
    },
  ];

  const saveSplitButtonOptions = [
    // {
    //   label: <UnderlinedText text="Save & Run" underlinedIndexes={[0, 7]} />,
    //   id: "save-and-run-btn",
    //   onClick: handleSaveAndRunRequest,
    // },
    {
      label: (
        <UnderlinedText text="Save & Create New" underlinedIndexes={[0, 7]} />
      ),
      id: "save-and-create-new-btn",
      onClick: handleSaveAndCreateNewRequest,
    },
  ];

  if (!isNewWorkflow) {
    saveSplitButtonOptions.push({
      label: (
        <UnderlinedText text="Save as new version" underlinedIndexes={[0, 8]} />
      ),
      id: "save-as-new-version-btn",
      onClick: handleSaveAsNewVersionRequest,
    });
  }

  const debounceSaveRequest = _debounce(handleSaveRequest, 500);

  // Hotkeys for save workflow
  useHotkeys(
    [
      `${Key.Control} + R`,
      `${Key.Control} + S`,
      `${Key.Control} + E`,
      `${Key.Control} + S + N`,
      `${Key.Control} + W`,
      `${Key.Control} + D`,
      `${Key.Control} + S + C`,
    ],
    (keyboardEvent, { keys }) => {
      keyboardEvent.preventDefault();
      const joinedKeys = keys?.join();

      switch (joinedKeys) {
        // 1. Save
        case [Key.Control, "S"].join().toLowerCase(): {
          if (madeChanges && !emptyTaskList && !isTrialExpired) {
            debounceSaveRequest();
          }
          break;
        }

        // 2. Save & Run
        case [Key.Control, "E"].join().toLowerCase(): {
          if (!emptyTaskList && !isTrialExpired) {
            debounceSaveRequest.cancel();
            handleSaveAndRunRequest();
          }
          break;
        }

        // 3. Save as new version
        case [Key.Control, "S", "N"].join().toLowerCase(): {
          debounceSaveRequest.cancel();

          if (
            !isNewWorkflow &&
            madeChanges &&
            !emptyTaskList &&
            !isTrialExpired
          ) {
            handleSaveAsNewVersionRequest();
          }

          break;
        }

        // 4. Reset
        case [Key.Control, "R"].join().toLowerCase(): {
          if (madeChanges && !emptyTaskList) {
            handleResetRequest();
          }

          break;
        }

        // 5. Download workflow definition JSON
        case [Key.Control, "W"].join().toLowerCase(): {
          handleDownloadFile();
          break;
        }

        // 6. Delete workflow definition
        case [Key.Control, "D"].join().toLowerCase(): {
          if (!isNewWorkflow && !isTrialExpired) {
            handleDeleteRequest();
          }

          break;
        }

        // 7. Save & Create New
        case [Key.Control, "S", "C"].join().toLowerCase(): {
          debounceSaveRequest.cancel();
          if (madeChanges && !emptyTaskList && !isTrialExpired) {
            handleSaveAndCreateNewRequest();
          }
          break;
        }
      }
    },
    {
      scopes: HOT_KEYS_WORKFLOW_DEFINITION,
      enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
    },
  );

  return (
    <Stack flexDirection="row" gap={1} flexWrap="wrap" alignItems="center">
      {errorMessage && (
        <SnackbarMessage
          onDismiss={() => {
            setErrorMessage("");
          }}
          severity="error"
          message={errorMessage}
        />
      )}
      {buttons.map(({ id, ...props }) => (
        <ButtonTooltip key={id} id={id} {...props} />
      ))}

      <RunWorkflowButton definitionActor={service} disabled={emptyTaskList} />

      <SplitButton
        startIcon={<SaveIcon />}
        disabled={!madeChanges || emptyTaskList || isTrialExpired}
        id={"head-action-save-btn"}
        options={saveSplitButtonOptions}
        primaryOnClick={handleSaveRequest}
        tooltip="Save this definition (Ctrl S)"
        data-testid="workflow-definition-save-button"
      >
        <UnderlinedText text="Save" underlinedIndexes={[0]} />
      </SplitButton>
    </Stack>
  );
};
