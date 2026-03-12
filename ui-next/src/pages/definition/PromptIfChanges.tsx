import fastDeepEqual from "fast-deep-equal";
import { FunctionComponent, useCallback } from "react";
import BlockNavigationWithConfirmation from "shared/BlockNavigationWithConfirmation";
import { useSaveProtection } from "shared/useSaveProtection";
import { ActorRef } from "xstate";
import { SaveWorkflowMachineEventTypes } from "./confirmSave/state/types";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "./state/types";
import { useWorkflowChanges } from "./state/useMadeChanges";
import { useAgentContext } from "components/agent/AgentContext";
export interface HeaderActionButtonsProps {
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
}

export const PromptIfChanges: FunctionComponent<HeaderActionButtonsProps> = ({
  definitionActor: service,
}) => {
  const { cancelStream, clearMessages } = useAgentContext();
  const { workflowChanges, currentWf } = useWorkflowChanges(service);
  const noFormChanges = currentWf
    ? fastDeepEqual(workflowChanges, currentWf)
    : true;

  // Simple validation check for common issues
  const checkHasErrors = (workflowToCheck: typeof workflowChanges) => {
    if (!workflowToCheck) {
      return false;
    }

    // Check for common validation issues
    const issues = [];

    // Check if name is missing or empty
    if (!workflowToCheck.name || workflowToCheck.name.trim() === "") {
      issues.push("Missing workflow name");
    }

    // Check if description is missing or empty
    if (
      !workflowToCheck.description ||
      workflowToCheck.description.trim() === ""
    ) {
      issues.push("Missing workflow description");
    }

    // Check if tasks array exists and has items
    if (!workflowToCheck.tasks || workflowToCheck.tasks.length === 0) {
      issues.push("No tasks defined");
    }

    // Check for tasks with missing required fields
    if (workflowToCheck.tasks) {
      workflowToCheck.tasks.forEach(
        (
          task: { name?: string; taskReferenceName?: string; type?: string },
          index: number,
        ) => {
          if (!task.name || task.name.trim() === "") {
            issues.push(`Task ${index + 1} is missing a name`);
          }
          if (!task.taskReferenceName || task.taskReferenceName.trim() === "") {
            issues.push(`Task ${index + 1} is missing a taskReferenceName`);
          }
          if (!task.type || task.type.trim() === "") {
            issues.push(`Task ${index + 1} is missing a type`);
          }
        },
      );
    }

    return issues.length > 0;
  };

  const { showPrompt, successfulSave, hasErrors, handleSave } =
    useSaveProtection({
      actor: service,
      noFormChanges,
      isSaveInProgress: (state) => state.hasTag?.("saveRequest") ?? false,
      hasErrors: () => {
        const workflowToCheck = workflowChanges || currentWf;
        return checkHasErrors(workflowToCheck);
      },
      detectSaveSuccessFromEvent: (eventType) => {
        if (eventType === SaveWorkflowMachineEventTypes.SAVED_SUCCESSFUL) {
          return true;
        } else if (
          eventType === SaveWorkflowMachineEventTypes.SAVED_CANCELLED
        ) {
          return false;
        }
        return undefined;
      },
      handleSaveAction: (actor) => {
        actor.send({ type: DefinitionMachineEventTypes.SAVE_EVT });
      },
    });

  const handleDiscard = useCallback(() => {
    // Cancel any ongoing stream
    cancelStream();
    // Clear assistant chat messages
    clearMessages();
  }, [cancelStream, clearMessages]);

  return (
    <BlockNavigationWithConfirmation
      nonBlockPaths={["/workflowDef/.*", "/newWorkflowDef"]}
      title="Unsaved Workflow"
      block={showPrompt}
      onSave={handleSave}
      successfulSave={successfulSave}
      hasErrors={hasErrors}
      onDiscard={handleDiscard}
    />
  );
};
