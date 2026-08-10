import fastDeepEqual from "fast-deep-equal";
import _isEmpty from "lodash/isEmpty";
import { FunctionComponent, useMemo } from "react";
import BlockNavigationWithConfirmation from "components/BlockNavigationWithConfirmation";
import { useSaveProtection } from "shared/useSaveProtection";
import { ActorRef, AnyEventObject, EventObject } from "xstate";

export interface SaveProtectionPromptProps {
  isInFormView: number;
  data: Record<string, unknown>;
  initialFormData: Record<string, unknown>;
  changedCodeData: Record<string, unknown>;
  actor?: ActorRef<AnyEventObject>;
  isSaveInProgress?: boolean;
  hasErrors?: boolean;
  onSave?: () => void;
}

// Component that uses useSaveProtection with an actor
const SaveProtectionPromptWithActor: FunctionComponent<{
  actor: ActorRef<AnyEventObject>;
  noFormChanges: boolean;
  isSaveInProgressProp?: boolean;
  hasErrorsProp?: boolean;
  onSave?: () => void;
}> = ({
  actor,
  noFormChanges,
  isSaveInProgressProp,
  hasErrorsProp,
  onSave,
}) => {
  const saveProtectionResult = useSaveProtection<
    Record<string, unknown>,
    EventObject
  >({
    actor,
    noFormChanges,
    isSaveInProgress: (state) => {
      // Check if we're in a saving state
      const context = state.context as Record<string, unknown>;
      if (typeof context.isSaving === "boolean") {
        return context.isSaving;
      }
      if (typeof context.isConfirmingSave === "boolean") {
        return context.isConfirmingSave;
      }
      return isSaveInProgressProp ?? false;
    },
    hasErrors: (state) => {
      // Check for errors in context
      const context = state.context as Record<string, unknown>;
      if (typeof context.hasErrors === "boolean") {
        return context.hasErrors;
      }
      if (typeof context.couldNotParseJson === "boolean") {
        return context.couldNotParseJson;
      }
      if (context.error !== undefined) {
        return true;
      }
      return hasErrorsProp ?? false;
    },
    handleSaveAction: onSave
      ? () => {
          onSave();
        }
      : () => {
          // Default no-op if no handler provided
        },
  });

  return (
    <BlockNavigationWithConfirmation
      nonBlockPaths={["/scheduleDef/.*"]}
      promptMessage={
        <>
          Your recent changes are not saved to the server. To run the new
          schedule, you have to save your progress.
        </>
      }
      title={"Unsaved schedule confirmation"}
      block={saveProtectionResult.showPrompt}
      onSave={saveProtectionResult.handleSave}
      successfulSave={saveProtectionResult.successfulSave}
      hasErrors={saveProtectionResult.hasErrors}
    />
  );
};

// Component that uses fallback logic without actor
const SaveProtectionPromptWithoutActor: FunctionComponent<{
  noFormChanges: boolean;
  isSaveInProgress?: boolean;
  hasErrors?: boolean;
  onSave?: () => void;
}> = ({ noFormChanges, isSaveInProgress, hasErrors, onSave }) => {
  const showPrompt = useMemo(
    () => !noFormChanges && !(isSaveInProgress ?? false),
    [noFormChanges, isSaveInProgress],
  );

  return (
    <BlockNavigationWithConfirmation
      nonBlockPaths={["/scheduleDef/.*"]}
      promptMessage={
        <>
          Your recent changes are not saved to the server. To run the new
          schedule, you have to save your progress.
        </>
      }
      title={"Unsaved schedule confirmation"}
      block={showPrompt}
      onSave={onSave ?? (() => {})}
      successfulSave={undefined}
      hasErrors={hasErrors ?? false}
    />
  );
};

export const SaveProtectionPrompt: FunctionComponent<
  SaveProtectionPromptProps
> = ({
  data,
  initialFormData,
  changedCodeData,
  isInFormView,
  actor,
  isSaveInProgress: isSaveInProgressProp,
  hasErrors: hasErrorsProp,
  onSave,
}) => {
  const noFormChanges = useMemo(() => {
    const formResult = fastDeepEqual(data, initialFormData);
    const codeResult = !_isEmpty(changedCodeData)
      ? fastDeepEqual(data, changedCodeData)
      : true;
    return isInFormView ? formResult : codeResult;
  }, [data, initialFormData, changedCodeData, isInFormView]);

  // Use actor-based component if actor is provided, otherwise use fallback
  if (actor) {
    return (
      <SaveProtectionPromptWithActor
        actor={actor}
        noFormChanges={noFormChanges}
        isSaveInProgressProp={isSaveInProgressProp}
        hasErrorsProp={hasErrorsProp}
        onSave={onSave}
      />
    );
  }

  return (
    <SaveProtectionPromptWithoutActor
      noFormChanges={noFormChanges}
      isSaveInProgress={isSaveInProgressProp}
      hasErrors={hasErrorsProp}
      onSave={onSave}
    />
  );
};
