import { usePushHistory } from "utils/hooks/usePushHistory";
import SplitButton from "components/v1/ConductorSplitButton";
import AddIcon from "components/v1/icons/AddIcon";
import {
  WORKFLOW_DEFINITION_URL,
  WORKFLOW_EXPLORER_URL,
} from "utils/constants/route";
import { useAuth } from "shared/auth";
import { useMemo, useState } from "react";
import { ImportBPNFileDialog } from "./ImportBPNFileDialog";
import { featureFlags, FEATURES } from "utils/flags";
import { removeCopyFromStorage } from "pages/runWorkflow/runWorkflowUtils";

const SplitWorkflowDefinitionButton = ({
  disabled,
}: {
  disabled?: boolean;
}) => {
  const pushHistory = usePushHistory();
  const { isTrialExpired } = useAuth();
  const [openBPMNModal, setOpenBPMNModal] = useState(false);
  const isImportBpmnHidden = featureFlags.isEnabled(FEATURES.HIDE_IMPORT_BPMN);

  const clearNewWorkflowStorage = () => {
    // Clear any existing new workflow data from localStorage
    removeCopyFromStorage({
      workflowName: "newWorkflowDef",
      currentVersion: undefined,
      isNewWorkflow: true,
    });
  };

  const splitButtonOptions = useMemo(() => {
    const options = [
      {
        label: "New Workflow",
        onClick: () => {
          clearNewWorkflowStorage();
          pushHistory(WORKFLOW_DEFINITION_URL.NEW);
        },
      },
      {
        label: "Select Template",
        onClick: () => pushHistory(WORKFLOW_EXPLORER_URL),
      },
    ];
    if (!isImportBpmnHidden) {
      options.push({
        label: "Import BPMN",
        onClick: () => setOpenBPMNModal(true),
      });
    }
    return options;
  }, [isImportBpmnHidden, pushHistory]);

  return (
    <>
      <SplitButton
        startIcon={<AddIcon />}
        options={splitButtonOptions}
        primaryOnClick={() => {
          clearNewWorkflowStorage();
          pushHistory(WORKFLOW_DEFINITION_URL.NEW);
        }}
        disabled={disabled || isTrialExpired}
      >
        Define workflow
      </SplitButton>
      <ImportBPNFileDialog
        open={openBPMNModal}
        onClose={() => setOpenBPMNModal(false)}
      />
    </>
  );
};

export default SplitWorkflowDefinitionButton;
