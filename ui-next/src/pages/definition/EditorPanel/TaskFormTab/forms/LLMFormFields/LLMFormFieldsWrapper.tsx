import React from "react";
import { useInterpret } from "@xstate/react";
import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { FieldComponentType, updateField } from "utils/fieldHelpers";
import { useAuthHeaders } from "utils/query";
import { ActorRef } from "xstate";
import {
  LLMFormFieldsEvents,
  LLMFormFieldsMachineContext,
  SelectInstructionsEvent,
  SelectPromptNameEvent,
  llmFormFieldsMachine,
} from "./state";

interface LLMFormFieldsWrapperProps {
  onChange: (task: Partial<TaskDef>) => void;
  task: Partial<TaskDef>;
  allFieldComponents: Array<[UiIntegrationsFieldType, FieldComponentType]>;
  children: (actor: ActorRef<LLMFormFieldsEvents>) => React.ReactNode;
}

const LLMFormFieldsWrapper = ({
  onChange,
  task,
  allFieldComponents,
  children,
}: LLMFormFieldsWrapperProps) => {
  const authHeaders = useAuthHeaders();
  const fields = allFieldComponents?.map(([type]) => type);
  const actor = useInterpret(llmFormFieldsMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      fields,
      task,
    },
    actions: {
      selectPromptName: (
        ctx: LLMFormFieldsMachineContext,
        event: SelectPromptNameEvent,
      ) => {
        const maybeAvailablePromptName = ctx.promptNameOptions.find(
          ({ name }) => name === event?.task?.inputParameters?.promptName,
        );
        if (maybeAvailablePromptName) {
          const newVariables = Object.fromEntries(
            (maybeAvailablePromptName?.variables as string[]).map((l) => [
              l,
              "",
            ]),
          );

          const resultVariables = {
            ...newVariables,
          };

          const taskWithVariables = updateField(
            `inputParameters.promptVariables`,
            resultVariables,
            event.task,
          );

          const taskWithSelectedPromptName = updateField(
            `inputParameters.${UiIntegrationsFieldType.PROMPT_NAME}`,
            maybeAvailablePromptName?.name,
            taskWithVariables,
          );

          onChange(taskWithSelectedPromptName);
        } else {
          const updatedTask = updateField(
            `inputParameters.${UiIntegrationsFieldType.PROMPT_NAME}`,
            event?.task?.inputParameters?.promptName,
            event.task,
          );

          onChange(updatedTask);
        }
      },
      selectInstructions: (
        ctx: LLMFormFieldsMachineContext,
        event: SelectInstructionsEvent,
      ) => {
        const maybeAvailablePromptName = ctx.promptNameOptions.find(
          ({ name }) => name === event?.task?.inputParameters?.instructions,
        );
        if (maybeAvailablePromptName) {
          const newVariables = Object.fromEntries(
            (maybeAvailablePromptName?.variables as string[]).map((l) => [
              l,
              "",
            ]),
          );

          const resultVariables = {
            ...newVariables,
          };

          const taskWithVariables = updateField(
            `inputParameters.promptVariables`,
            resultVariables,
            event.task,
          );

          const taskWithSelectedPromptName = updateField(
            `inputParameters.${UiIntegrationsFieldType.INSTRUCTIONS}`,
            maybeAvailablePromptName?.name,
            taskWithVariables,
          );

          onChange(taskWithSelectedPromptName);
        } else {
          const updatedTask = updateField(
            `inputParameters.${UiIntegrationsFieldType.INSTRUCTIONS}`,
            event?.task?.inputParameters?.instructions,
            event.task,
          );

          onChange(updatedTask);
        }
      },
    },
  });
  return <>{children(actor)}</>;
};

export default LLMFormFieldsWrapper;
