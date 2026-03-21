import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { LLMFormFieldsMachineContext, FocusEvent } from "./types";
import { logger } from "utils/logger";
import { IntegrationCategory } from "types/Integrations";

const fetchContext = fetchContextNonHook();

export const fetchLlmProviderOptionsService = async ({
  authHeaders: headers,
}: LLMFormFieldsMachineContext) => {
  const path = `/integrations/provider?category=${IntegrationCategory.AI_MODEL}&activeOnly=true`;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, path],
      () => fetchWithContext(path, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error(error);
    return [];
  }
};

export const fetchForModels = async (
  { authHeaders: headers }: LLMFormFieldsMachineContext,
  { task }: FocusEvent,
) => {
  const maybeLlmProvider = task?.inputParameters?.llmProvider;
  if (maybeLlmProvider) {
    const path = `/integrations/provider/${maybeLlmProvider}/integration?activeOnly=true`;
    try {
      const response = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers }),
      );
      return response;
    } catch (error) {
      logger.error(error);
      return [];
    }
  }
  return [];
};

export const fetchForPromptNames = async (
  { authHeaders: headers }: LLMFormFieldsMachineContext,
  { task }: FocusEvent,
) => {
  const maybeLlmProvider = task?.inputParameters?.llmProvider;
  const maybeModel = task?.inputParameters?.model;
  if (maybeModel && maybeLlmProvider) {
    const path = `/integrations/provider/${maybeLlmProvider}/integration/${maybeModel}/prompt`;
    try {
      const response = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers }),
      );
      return response;
    } catch (error) {
      logger.error(error);
      return [];
    }
  }
  return [];
};

export const fetchForVectorDb = async ({
  authHeaders: headers,
}: LLMFormFieldsMachineContext) => {
  const path = `/integrations/provider?category=${IntegrationCategory.VECTOR_DB}&activeOnly=true`;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, path],
      () => fetchWithContext(path, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error(error);
    return [];
  }
};

export const fetchForIndexes = async (
  { authHeaders: headers }: LLMFormFieldsMachineContext,
  { task }: FocusEvent,
) => {
  const maybeVectorDB = task?.inputParameters?.vectorDB;
  if (maybeVectorDB) {
    const path = `/integrations/provider/${maybeVectorDB}/integration?activeOnly=true`;
    try {
      const response = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers }),
      );
      return response;
    } catch (error) {
      logger.error(error);
      return [];
    }
  }
  return [];
};

export const fetchForEmbeddingsModelProvider = async ({
  authHeaders: headers,
}: LLMFormFieldsMachineContext) => {
  const path = `/integrations/provider?category=${IntegrationCategory.AI_MODEL}&activeOnly=true`;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, path],
      () => fetchWithContext(path, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error(error);
    return [];
  }
};

export const fetchForEmbeddingModel = async (
  { authHeaders: headers }: LLMFormFieldsMachineContext,
  { task }: FocusEvent,
) => {
  const maybeEmbeddingModelProvider =
    task?.inputParameters?.embeddingModelProvider;
  if (maybeEmbeddingModelProvider) {
    const path = `/integrations/provider/${maybeEmbeddingModelProvider}/integration?activeOnly=true`;
    try {
      const response = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers }),
      );
      return response;
    } catch (error) {
      logger.error(error);
      return [];
    }
  }
  return [];
};
