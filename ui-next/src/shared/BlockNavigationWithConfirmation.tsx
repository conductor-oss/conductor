import UnsavedChangesDialog from "components/v1/Modal/UnsavedChangesDialog";
import { ReactNode, useRef, useState } from "react";
import { useBlocker, useNavigate } from "react-router";

export interface BlockNavigationWithConfirmationProps {
  nonBlockPaths: string[];
  promptMessage?: ReactNode;
  title?: ReactNode;
  block?: boolean;
  hasErrors?: boolean;
  onSave?: () => void;
  successfulSave?: boolean;
  onDiscard?: () => void;
}

const isAcceptedPath = (
  nonBlockPaths: string[],
  currentPathWithQuery: string,
) => {
  return nonBlockPaths.some((path) => {
    const regExMatching = new RegExp(path);
    return regExMatching.test(currentPathWithQuery);
  });
};

const BlockNavigationWithConfirmation = ({
  nonBlockPaths,
  block,
  title,
  hasErrors = false,
  onSave,
  successfulSave,
  onDiscard,
}: BlockNavigationWithConfirmationProps) => {
  const navigate = useNavigate();
  const [targetLocation, setTargetLocation] = useState<string | null>(null);
  const isDiscardingRef = useRef(false);
  const shouldBlock = block !== false && !isDiscardingRef.current; // Block unless discarding

  const handleAction = () => {
    onSave?.();
  };

  const handleCancel = () => {
    setTargetLocation(null);
  };

  const handleDiscard = () => {
    // Call the onDiscard callback if provided (e.g., to cancel stream and clear messages)
    onDiscard?.();

    if (targetLocation) {
      const locationToNavigate = targetLocation;
      isDiscardingRef.current = true; // Temporarily disable blocker
      setTargetLocation(null);
      // Use setTimeout to ensure the blocker is disabled before navigating
      setTimeout(() => {
        navigate(locationToNavigate);
        isDiscardingRef.current = false; // Re-enable blocker after navigation
      }, 0);
    }
  };

  // Handle successful save navigation using a ref to track previous state
  const prevSuccessfulSaveRef = useRef(successfulSave);

  if (
    successfulSave !== undefined &&
    successfulSave !== prevSuccessfulSaveRef.current &&
    successfulSave
  ) {
    // If there's a pending navigation, navigate to it
    if (targetLocation) {
      navigate(targetLocation);
    }
    // Close the dialog after successful save (regardless of navigation)
    setTargetLocation(null);
  }
  prevSuccessfulSaveRef.current = successfulSave;

  useBlocker(({ nextLocation }) => {
    if (!block || !shouldBlock) return false;

    const fullLocation = nextLocation.pathname + nextLocation.search;
    if (!isAcceptedPath(nonBlockPaths, nextLocation.pathname)) {
      setTargetLocation(fullLocation);
      return true; // Block navigation
    }
    return false; // Allow navigation
  });

  return (
    <UnsavedChangesDialog
      open={!!(block && targetLocation)}
      message={
        hasErrors
          ? "Please fix the errors before saving."
          : "You have unsaved changes. What would you like to do?"
      }
      header={hasErrors ? "Cannot Save" : title}
      actionButtonLabel={hasErrors ? "Continue Editing" : "Save & Continue"}
      handleAction={hasErrors ? handleCancel : handleAction}
      handleCancel={handleCancel}
      handleDiscard={handleDiscard}
      hasErrors={hasErrors}
    />
  );
};

export default BlockNavigationWithConfirmation;
