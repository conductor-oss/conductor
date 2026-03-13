import { createMachine } from "xstate";
import {
  LLMFormFieldsMachineContext,
  LLMFormFieldsEvents,
  LLMFormFieldsMachineStates,
  LLMFormFieldsMachineEventTypes,
} from "./types";
import * as services from "./services";
import * as actions from "./actions";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";

export const llmFormFieldsMachine = createMachine<
  LLMFormFieldsMachineContext,
  LLMFormFieldsEvents
>(
  {
    id: "llmFormFieldsMachine",
    predictableActionArguments: true,
    initial: LLMFormFieldsMachineStates.DETERMINE_INITIAL_STATE,
    context: {
      task: {},
      fields: [],
      llmProviderOptions: [],
      modelOptions: [],
      promptNameOptions: [],
      vectorDbOptions: [],
      indexOptions: [],
      embeddingModelOptions: [],
      selectedPromptName: undefined,
    },
    states: {
      [LLMFormFieldsMachineStates.DETERMINE_INITIAL_STATE]: {
        always: [
          {
            target: LLMFormFieldsMachineStates.FETCH_VECTORDB_OPTIONS,
            cond: (context) =>
              context.fields.some(
                (field) => field === UiIntegrationsFieldType.VECTOR_DB,
              ),
          },
          {
            target: LLMFormFieldsMachineStates.FETCH_LLM_PROVIDER_OPTIONS,
            cond: (context) =>
              context.fields.some(
                (field) => field === UiIntegrationsFieldType.LLM_PROVIDER,
              ),
          },
        ],
      },
      [LLMFormFieldsMachineStates.IDLE]: {
        on: {
          [LLMFormFieldsMachineEventTypes.FOCUS_LLM_PROVIDER]: [
            {
              target: LLMFormFieldsMachineStates.FETCH_LLM_PROVIDER_OPTIONS,
              cond: (context: LLMFormFieldsMachineContext) =>
                context.llmProviderOptions.length === 0,
            },
            { target: LLMFormFieldsMachineStates.IDLE },
          ],
          [LLMFormFieldsMachineEventTypes.FOCUS_EMBEDDINGS_MODEL]:
            LLMFormFieldsMachineStates.FETCH_EMBEDDINGS_MODEL,
          [LLMFormFieldsMachineEventTypes.FOCUS_INDEX]:
            LLMFormFieldsMachineStates.FETCH_INDEX_OPTIONS,
          [LLMFormFieldsMachineEventTypes.FOCUS_PROMPT_NAMES]:
            LLMFormFieldsMachineStates.FETCH_PROMPT_NAMES,
          [LLMFormFieldsMachineEventTypes.FOCUS_VECTORDB]: [
            {
              target: LLMFormFieldsMachineStates.FETCH_VECTORDB_OPTIONS,
              cond: (context: LLMFormFieldsMachineContext) =>
                context.vectorDbOptions.length === 0,
            },
            { target: LLMFormFieldsMachineStates.IDLE },
          ],
          [LLMFormFieldsMachineEventTypes.FOCUS_MODEL]:
            LLMFormFieldsMachineStates.FETCH_MODEL_OPTIONS,
          [LLMFormFieldsMachineEventTypes.SELECT_PROMPT_NAME]: {
            actions: "selectPromptName",
          },
          [LLMFormFieldsMachineEventTypes.SELECT_INSTRUCTIONS]: {
            actions: "selectInstructions",
          },
        },
      },
      [LLMFormFieldsMachineStates.FETCH_LLM_PROVIDER_OPTIONS]: {
        invoke: {
          src: "fetchLlmProviderOptionsService",
          onDone: {
            actions: "persistLlmProviderOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
        },
      },
      [LLMFormFieldsMachineStates.FETCH_MODEL_OPTIONS]: {
        invoke: {
          src: "fetchForModels",
          onDone: {
            actions: "persistModelOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
          onError: LLMFormFieldsMachineStates.IDLE,
        },
      },
      [LLMFormFieldsMachineStates.FETCH_VECTORDB_OPTIONS]: {
        invoke: {
          src: "fetchForVectorDb",
          onDone: {
            actions: "persistVectorDbOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
          onError: LLMFormFieldsMachineStates.IDLE,
        },
      },
      [LLMFormFieldsMachineStates.FETCH_PROMPT_NAMES]: {
        invoke: {
          src: "fetchForPromptNames",
          onDone: {
            actions: "persistPromptNameOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
          onError: LLMFormFieldsMachineStates.IDLE,
        },
      },
      [LLMFormFieldsMachineStates.FETCH_INDEX_OPTIONS]: {
        invoke: {
          src: "fetchForIndexes",
          onDone: {
            actions: "persistIndexesOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
          onError: LLMFormFieldsMachineStates.IDLE,
        },
      },
      [LLMFormFieldsMachineStates.FETCH_EMBEDDINGS_MODEL]: {
        invoke: {
          src: "fetchForEmbeddingModel",
          onDone: {
            actions: "persistEmbeddingModelOptions",
            target: LLMFormFieldsMachineStates.IDLE,
          },
          onError: LLMFormFieldsMachineStates.IDLE,
        },
      },
    },
  },
  {
    actions: actions as any,
    services: services as any,
  },
);
