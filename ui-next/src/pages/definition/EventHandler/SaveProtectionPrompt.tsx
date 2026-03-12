import { useSelector } from "@xstate/react";
import fastDeepEqual from "fast-deep-equal";
import { omit } from "lodash";
import { FunctionComponent } from "react";
import BlockNavigationWithConfirmation from "shared/BlockNavigationWithConfirmation";
import { useSaveProtection } from "shared/useSaveProtection";
import { ActorRef, AnyEventObject } from "xstate";
import {
  SaveEventHandlerEvents,
  SaveEventHandlerMachineContext,
  SaveEventHandlerMachineEventTypes,
  SaveEventHandlerStates,
} from "./eventhandlers/state";

export interface SaveProtectionPromptProps {
  service: ActorRef<SaveEventHandlerEvents>;
}

const useCheckForChanges = (
  formActor: ActorRef<AnyEventObject> | null,
  editorActor: ActorRef<SaveEventHandlerEvents>,
) => {
  // Always call hooks unconditionally - use editorActor as fallback if formActor is null
  const formData = useSelector(
    formActor || editorActor,
    (state: {
      context: { eventAsJson?: unknown; originalSource?: unknown };
    }) => {
      // Check if this is form actor context
      if (state.context.eventAsJson !== undefined) {
        return {
          eventAsJson: state.context.eventAsJson,
          originalSource: state.context.originalSource,
        };
      }
      return null;
    },
  );

  const [editorChanges, editorOriginalSource] = useSelector(
    editorActor,
    (state) => [state.context.editorChanges, state.context.originalSource],
  );

  // Use form data if formActor exists and we have form data, otherwise use editor data
  if (
    formActor &&
    formData &&
    formData.eventAsJson &&
    formData.originalSource
  ) {
    return fastDeepEqual(
      omit(formData.eventAsJson as Record<string, unknown>, "action"),
      formData.originalSource,
    );
  } else {
    return fastDeepEqual(editorChanges, editorOriginalSource);
  }
};

export const SaveProtectionPrompt: FunctionComponent<
  SaveProtectionPromptProps
> = ({ service }) => {
  // @ts-expect-error - children type is not fully typed
  const formActor = service?.children?.get("eventFormMachine") as
    | ActorRef<AnyEventObject>
    | undefined;

  const noFormChanges = useCheckForChanges(formActor || null, service);

  const {
    showPrompt,
    successfulSave,
    hasErrors,
    handleSave: baseHandleSave,
  } = useSaveProtection<SaveEventHandlerMachineContext, SaveEventHandlerEvents>(
    {
      actor: service,
      noFormChanges,
      isSaveInProgress: (state) => {
        // Check if we're in CONFIRM_SAVE state (save confirmation dialog) or saving states
        return (
          state.matches(SaveEventHandlerStates.CONFIRM_SAVE) ||
          state.matches(SaveEventHandlerStates.CREATE_EVENT_HANDLER) ||
          state.matches(SaveEventHandlerStates.UPDATE_EVENT_HANDLER)
        );
      },
      hasErrors: (state) => {
        const context = state.context;

        // Check for parse errors
        if (context.couldNotParseJson) {
          return true;
        }

        // Check for API errors
        if (context.message) {
          return true;
        }

        return false;
      },
      detectSaveSuccessFromEvent: (eventType) => {
        // Check for successful save event
        if (eventType === SaveEventHandlerMachineEventTypes.SAVED_SUCCESSFUL) {
          return true;
        }
        // Check for cancelled save event
        if (eventType === SaveEventHandlerMachineEventTypes.SAVED_CANCELLED) {
          return false;
        }
        return undefined;
      },
      detectSaveSuccessFromContext: ({
        currentContext,
        previousContext,
        wasSaving,
        isSaving,
      }) => {
        // If we were saving and now we're not, check if originalSource was updated
        if (wasSaving && !isSaving && previousContext) {
          const currentOriginStr = currentContext.originalSource;
          const prevOriginStr = previousContext.originalSource;

          // If origin was updated, save was successful
          if (currentOriginStr !== prevOriginStr) {
            return true;
          }
        }
        return false;
      },
      handleSaveAction: (actor) => {
        // Check current state to see if we're already in the save confirmation dialog
        const snapshot = actor.getSnapshot();
        const isInSaveConfirmation = snapshot.matches(
          SaveEventHandlerStates.CONFIRM_SAVE,
        );

        // If we're already in the save confirmation dialog, trigger the save immediately
        if (isInSaveConfirmation) {
          actor.send({
            type: SaveEventHandlerMachineEventTypes.CONFIRM_SAVE_EVT,
          });
        } else {
          // Open the save confirmation dialog
          // User will need to click "Confirm Save" button in the save confirmation dialog
          actor.send({
            type: SaveEventHandlerMachineEventTypes.SAVE_EVT,
          });
        }
      },
    },
  );

  const handleSave = baseHandleSave;

  return (
    <BlockNavigationWithConfirmation
      nonBlockPaths={["/eventHandlerDef/.*"]}
      promptMessage={
        <>
          Your recent changes are not saved to the server. To run the new event
          handler, you have to save your progress.
        </>
      }
      title={"Unsaved event handler confirmation"}
      block={showPrompt}
      onSave={handleSave}
      successfulSave={successfulSave}
      hasErrors={hasErrors}
    />
  );
};
