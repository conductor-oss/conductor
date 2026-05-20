import { useSelector } from "@xstate/react";
import fastDeepEqual from "fast-deep-equal";
import { FunctionComponent, useEffect, useRef } from "react";
import BlockNavigationWithConfirmation from "components/BlockNavigationWithConfirmation";
import { useSaveProtection } from "shared/useSaveProtection";
import { ActorRef } from "xstate";
import { TaskDefinitionFormMachineEvent } from "./form/state";
import {
  TaskDefinitionMachineContext,
  TaskDefinitionMachineEvent,
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "./state";
import { TASK_FORM_MACHINE_ID } from "./state/helpers";
export interface SaveProtectionPromptProps {
  taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
}

const useCheckForChanges = (
  actor:
    | ActorRef<TaskDefinitionFormMachineEvent>
    | ActorRef<TaskDefinitionMachineEvent>,
) => {
  const [modifiedTaskDefinition, originTaskDefinition] = useSelector(
    actor,
    (state) => [
      state.context.modifiedTaskDefinition,
      state.context.originTaskDefinition,
    ],
  );
  const result = fastDeepEqual(modifiedTaskDefinition, originTaskDefinition);
  return result;
};

export const SaveProtectionPrompt: FunctionComponent<
  SaveProtectionPromptProps
> = ({ taskDefActor }) => {
  // @ts-expect-error - children type is not fully typed
  const formActor = taskDefActor?.children?.get(
    TASK_FORM_MACHINE_ID,
  ) as ActorRef<TaskDefinitionFormMachineEvent>;

  const noFormChanges = useCheckForChanges(
    formActor != null ? formActor : taskDefActor,
  );

  const {
    showPrompt,
    successfulSave,
    hasErrors,
    handleSave: baseHandleSave,
  } = useSaveProtection<
    TaskDefinitionMachineContext,
    TaskDefinitionMachineEvent
  >({
    actor: taskDefActor,
    noFormChanges,
    isSaveInProgress: (state) => {
      // Check if we're in DIFF_EDITOR state (save confirmation dialog) or createTaskDefinition state (saving)
      return (
        state.matches([
          TaskDefinitionMachineState.READY,
          TaskDefinitionMachineState.MAIN_CONTAINER,
          TaskDefinitionMachineState.DIFF_EDITOR,
        ]) ||
        state.matches([
          TaskDefinitionMachineState.READY,
          TaskDefinitionMachineState.MAIN_CONTAINER,
          TaskDefinitionMachineState.DIFF_EDITOR,
          "createTaskDefinition",
        ])
      );
    },
    hasErrors: (state) => {
      const context = state.context;
      const modifiedTaskDefinition = context.modifiedTaskDefinition;

      // Check for parse errors
      if (context.couldNotParseJson) {
        return true;
      }

      // Check for API errors
      if (context.error) {
        return true;
      }

      // Check for required fields
      if (modifiedTaskDefinition) {
        // Check if name is missing or empty
        if (
          !modifiedTaskDefinition.name ||
          modifiedTaskDefinition.name.trim() === ""
        ) {
          return true;
        }
      }

      return false;
    },
    detectSaveSuccessFromEvent: (eventType) => {
      // Check for cancel event
      if (eventType === TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE) {
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
      // If we were saving and now we're not, check if originTaskDefinition was updated
      if (wasSaving && !isSaving && previousContext) {
        const currentOriginStr = JSON.stringify(
          currentContext.originTaskDefinition,
        );
        const prevOriginStr = JSON.stringify(
          previousContext.originTaskDefinition,
        );

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
      const isInSaveConfirmation = snapshot.matches([
        TaskDefinitionMachineState.READY,
        TaskDefinitionMachineState.MAIN_CONTAINER,
        TaskDefinitionMachineState.DIFF_EDITOR,
      ]);

      // If we're already in the save confirmation dialog, trigger the save immediately
      if (isInSaveConfirmation) {
        actor.send({
          type: TaskDefinitionMachineEventType.SAVE_TASK_DEFINITION,
        });
      } else {
        // Open the save confirmation dialog
        // User will need to click "Confirm Save" button in the save confirmation dialog
        actor.send({
          type: TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN,
          isContinueCreate: false,
        });
      }
    },
  });

  // Track last synced form data to avoid unnecessary syncing
  const lastSyncedFormDataRef = useRef<string | null>(null);

  // Continuously sync form data to parent context
  // This ensures form data is always in sync before any re-render happens
  useEffect(() => {
    if (!formActor) return;

    const subscription = formActor.subscribe((state) => {
      if (state.context?.modifiedTaskDefinition) {
        const formDataString = JSON.stringify(
          state.context.modifiedTaskDefinition,
          null,
          2,
        );

        // Only sync if the data has actually changed
        if (lastSyncedFormDataRef.current !== formDataString) {
          lastSyncedFormDataRef.current = formDataString;
          // Sync form data to parent context
          taskDefActor.send({
            type: TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION,
            modifiedTaskDefinitionString: formDataString,
          });
        }
      }
    });

    return () => subscription.unsubscribe();
  }, [formActor, taskDefActor]);

  const handleSave = baseHandleSave;

  return (
    <BlockNavigationWithConfirmation
      nonBlockPaths={["/taskDef/.*", "/newTaskDef"]}
      promptMessage={
        <>
          Your recent changes are not saved to the server. To run the new task,
          you have to save your progress.
        </>
      }
      title={"Unsaved task confirmation"}
      block={showPrompt}
      onSave={handleSave}
      successfulSave={successfulSave}
      hasErrors={hasErrors}
    />
  );
};
