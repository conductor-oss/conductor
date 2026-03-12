import { AuthHeaders } from "types/common";
import { PromptDef, TaskDef, UiIntegrationsFieldType } from "types";

export enum LLMFormFieldsMachineEventTypes {
  FOCUS_LLM_PROVIDER = "FOCUS_LLM_PROVIDER",
  FOCUS_PROMPT_NAMES = "FOCUS_PROMPT_NAME",
  FOCUS_MODEL = "FOCUS_MODEL",
  FOCUS_VECTORDB = "FOCUS_VECTORDB",
  FOCUS_INDEX = "FOCUS_INDEX",
  FOCUS_EMBEDDINGS_MODEL_PROVIDER = "FOCUS_EMBEDDINGS_MODEL_PROVIDER",
  FOCUS_EMBEDDINGS_MODEL = "FOCUS_EMBEDDINGS_MODEL",

  SELECT_PROMPT_NAME = "SELECT_PROMPT_NAME",
  UPDATE_TASK = "UPDATE_TASK",
  SELECT_INSTRUCTIONS = "SELECT_INSTRUCTIONS",
}

export enum LLMFormFieldsMachineStates {
  DETERMINE_INITIAL_STATE = "DETERMINE_INITIAL_STATE",
  IDLE = "IDLE",
  FETCH_MODEL_OPTIONS = "FETCH_MODEL_OPTIONS",
  FETCH_PROMPT_NAMES = "FETCH_PROMPT_NAME",
  FETCH_LLM_PROVIDER_OPTIONS = "FETCH_LLM_PROVIDER_OPTIONS",
  FETCH_VECTORDB_OPTIONS = "FETCH_VECTORDB_OPTIONS",
  FETCH_INDEX_OPTIONS = "FETCH_INDEX_OPTIONS",
  FETCH_EMBEDDINGS_MODEL_PROVIDER = "FETCH_EMBEDDINGS_MODEL_PROVIDER",
  FETCH_EMBEDDINGS_MODEL = "FETCH_EMBEDDINGS_MODEL",
}

export type FocusEvent = {
  type: LLMFormFieldsMachineEventTypes;
  task: Partial<TaskDef>;
};

export type UpdateTaskEvent = {
  type: LLMFormFieldsMachineEventTypes;
  task: Partial<TaskDef>;
};

export type SelectPromptNameEvent = {
  type: LLMFormFieldsMachineEventTypes.SELECT_PROMPT_NAME;
  task: Partial<TaskDef>;
};

export type SelectInstructionsEvent = {
  type: LLMFormFieldsMachineEventTypes.SELECT_INSTRUCTIONS;
  task: Partial<TaskDef>;
};

type Error = {
  message: string;
  severity: string; // Not really a string
};

export type LLMFormFieldsEvents =
  | FocusEvent
  | SelectPromptNameEvent
  | UpdateTaskEvent;

export interface LLMFormFieldsMachineContext {
  fields: UiIntegrationsFieldType[];
  selectedPromptName?: Record<string, unknown>;
  authHeaders?: AuthHeaders;
  llmProviderOptions: [];
  promptNameOptions: PromptDef[];
  modelOptions: [];
  vectorDbOptions: [];
  indexOptions: [];
  embeddingModelOptions: [];
  error?: Error;
  task: Partial<TaskDef>;
}
