import { useState } from "react";
import { UploadSimple as ImportIcon } from "@phosphor-icons/react";
import MuiButton from "components/ui/buttons/MuiButton";
import { useAuth } from "components/features/auth";
import { featureFlags, FEATURES } from "utils/flags";
import { ImportBPNFileDialog } from "./ImportBPNFileDialog";

const ImportBpmnButton = ({ disabled }: { disabled?: boolean }) => {
  const { isTrialExpired } = useAuth();
  const [openBPMNModal, setOpenBPMNModal] = useState(false);
  const isImportBpmnHidden = featureFlags.isEnabled(FEATURES.HIDE_IMPORT_BPMN);

  if (isImportBpmnHidden) {
    return null;
  }

  return (
    <>
      <MuiButton
        variant="text"
        color="primary"
        startIcon={<ImportIcon />}
        onClick={() => setOpenBPMNModal(true)}
        disabled={disabled || isTrialExpired}
      >
        Import BPMN
      </MuiButton>
      <ImportBPNFileDialog
        open={openBPMNModal}
        onClose={() => setOpenBPMNModal(false)}
      />
    </>
  );
};

export default ImportBpmnButton;
