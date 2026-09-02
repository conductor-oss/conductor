import { LLMFormFieldsMachineContext, FocusEvent } from "./types";
export declare const fetchLlmProviderOptionsService: ({ authHeaders: headers, }: LLMFormFieldsMachineContext) => Promise<any>;
export declare const fetchForModels: ({ authHeaders: headers }: LLMFormFieldsMachineContext, { task }: FocusEvent) => Promise<any>;
export declare const fetchForPromptNames: ({ authHeaders: headers }: LLMFormFieldsMachineContext, { task }: FocusEvent) => Promise<any>;
export declare const fetchForVectorDb: ({ authHeaders: headers, }: LLMFormFieldsMachineContext) => Promise<any>;
export declare const fetchForIndexes: ({ authHeaders: headers }: LLMFormFieldsMachineContext, { task }: FocusEvent) => Promise<any>;
export declare const fetchForEmbeddingsModelProvider: ({ authHeaders: headers, }: LLMFormFieldsMachineContext) => Promise<any>;
export declare const fetchForEmbeddingModel: ({ authHeaders: headers }: LLMFormFieldsMachineContext, { task }: FocusEvent) => Promise<any>;
