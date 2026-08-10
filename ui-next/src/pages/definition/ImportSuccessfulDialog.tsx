import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { openInNewTab } from "utils/helpers";

const ImportSuccessfulDialog = ({
  workflowName,
  blogUrl,
  hideModal,
}: {
  workflowName: string;
  blogUrl?: string;
  hideModal: () => void;
}) => {
  const handleConfirmationValue = (confirmed: boolean) => {
    if (confirmed) {
      hideModal();
    }
  };

  return (
    <ConfirmChoiceDialog
      handleConfirmationValue={handleConfirmationValue}
      message={
        <>
          You have successfully imported {workflowName} into your cluster and
          you can start running this.
          {blogUrl && (
            <div style={{ marginTop: "8px" }}>
              Refer to this{" "}
              <span
                onClick={() => openInNewTab(blogUrl)}
                style={{
                  cursor: "pointer",
                  color: "#1976d2",
                  fontWeight: "500",
                }}
              >
                article
              </span>{" "}
              to understand more details about this workflow and how to use it.
            </div>
          )}
        </>
      }
      id="workflow-import-successful-dialog"
      header={"Nice work"}
      confirmBtnLabel="Close"
      hideCancelBtn
    />
  );
};

export default ImportSuccessfulDialog;
