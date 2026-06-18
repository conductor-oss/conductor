import { useState } from "react";

export interface UseSummarizeResult {
  isSummarized: boolean;
  confirmOpen: boolean;
  handleToggleChange: (checked: boolean) => void;
  handleConfirm: () => void;
  handleCancel: () => void;
}

/**
 * Manages the summarize toggle and the confirmation dialog state shared by
 * DoWhileIteration and InlineTaskIterations.
 *
 * Starts summarized. When the user disables summarize, a confirmation dialog
 * is shown before fetching the full workflow. Re-enabling summarize is
 * immediate with no confirmation needed. The state persists across task
 * navigation for the lifetime of the RightPanel.
 */
export function useSummarize(): UseSummarizeResult {
  const [isSummarized, setIsSummarized] = useState(true);
  const [confirmOpen, setConfirmOpen] = useState(false);

  return {
    isSummarized,
    confirmOpen,
    handleToggleChange: (checked: boolean) => {
      if (checked) {
        setIsSummarized(true);
      } else {
        setConfirmOpen(true);
      }
    },
    handleConfirm: () => {
      setIsSummarized(false);
      setConfirmOpen(false);
    },
    handleCancel: () => setConfirmOpen(false),
  };
}
