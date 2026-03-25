import { SetEditingFormFieldEvent, TaskDefinitionFormContext } from "./types";

export const isNameField = (
  context: TaskDefinitionFormContext,
  { name }: SetEditingFormFieldEvent,
) => name === "name";

export const isDescriptionField = (
  context: TaskDefinitionFormContext,
  { name }: SetEditingFormFieldEvent,
) => name === "description";
