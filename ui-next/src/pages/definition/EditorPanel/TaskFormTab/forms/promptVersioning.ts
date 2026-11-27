import { TaskDef } from "types";
import { FEATURES, featureFlags } from "utils/flags";

export const isPromptVersioningEnabled = () =>
  featureFlags.isEnabled(FEATURES.AI_PROMPTS_VERSIONING);

export const VERSION_FIELD_COLUMNS = 2;
export const PROMPT_FIELD_COLUMNS = 12 - VERSION_FIELD_COLUMNS;

export const withoutPromptVersion = (
  task: Partial<TaskDef>,
): Partial<TaskDef> => {
  const inputParameters = { ...(task.inputParameters ?? {}) };
  delete (inputParameters as Record<string, unknown>).promptVersion;
  return { ...task, inputParameters } as Partial<TaskDef>;
};
