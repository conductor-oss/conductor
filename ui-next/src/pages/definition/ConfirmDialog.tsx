import { useCallback, FunctionComponent } from "react";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";

interface ConfirmDialogProps {
  onConfirm: () => void;
  onCancel: () => void;
  shouldPrompt: boolean;
  message: string;
  title?: string;
}

export const ConfirmDialog: FunctionComponent<ConfirmDialogProps> = ({
  onConfirm,
  onCancel,
  shouldPrompt,
  title = "Confirmation",
  message,
}) => {
  const handleConfirmUseLocalChanges = useCallback(
    (val: boolean) => (val ? onConfirm : onCancel)(),
    [onConfirm, onCancel],
  );
  return shouldPrompt ? (
    <ConfirmChoiceDialog
      handleConfirmationValue={handleConfirmUseLocalChanges}
      message={message}
      header={title}
    />
  ) : null;
};
