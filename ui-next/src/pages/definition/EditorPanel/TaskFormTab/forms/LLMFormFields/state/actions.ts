import { assign, DoneInvokeEvent } from "xstate";
import { LLMFormFieldsMachineContext } from "./types";

export const persistLlmProviderOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  llmProviderOptions: (_, { data }) => data,
});

export const persistModelOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  modelOptions: (_, { data }) => data,
});

export const persistPromptNameOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  promptNameOptions: (_, { data }) => data,
});

export const persistVectorDbOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  vectorDbOptions: (_, { data }) => data,
});

export const persistEmbeddingModelOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  embeddingModelOptions: (_, { data }) => data,
});

export const persistIndexesOptions = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  indexOptions: (_, { data }) => data,
});

export const persistError = assign<
  LLMFormFieldsMachineContext,
  DoneInvokeEvent<any>
>({
  error: (_, { data }) => ({ message: data, severity: "error" }),
});
