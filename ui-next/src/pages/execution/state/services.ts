import { fetchWithContext } from "plugins/fetch";
import {
  ExecutionMachineContext,
  RestartExecutionEvent,
  RetryExecutionEvent,
  UpdateVariablesEvent,
} from "./types";
import { getErrors } from "utils";
import { fetchExecution } from "commonServices";
import { toMaybeQueryString } from "utils/toMaybeQueryString";
import { maybeTriggerFailureWorkflow } from "utils/maybeTriggerWorkflow";

export { fetchExecution };

export const restartExecution = async (
  { executionId, authHeaders }: ExecutionMachineContext,
  event: any,
) => {
  const { options = {} } = event as RestartExecutionEvent;
  const url = `/workflow/${executionId}/restart${toMaybeQueryString(options)}`;

  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

const defaultRetryOptions = {
  retryIfRetriedByParent: "false",
};

export const retryExecution = async (
  { executionId, authHeaders }: ExecutionMachineContext,
  event: any,
) => {
  const { options = {} } = event as RetryExecutionEvent;

  const retryOptions = {
    ...options,
    ...defaultRetryOptions,
  };

  const url = `/workflow/${executionId}/retry${toMaybeQueryString(
    retryOptions,
  )}`;

  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};
export const terminateExecution = async ({
  executionId,
  authHeaders,
}: ExecutionMachineContext) => {
  const url = `/workflow/${executionId}${maybeTriggerFailureWorkflow()}`;
  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

export const resumeExecution = async ({
  executionId,
  authHeaders,
}: ExecutionMachineContext) => {
  const url = `/workflow/${executionId}/resume`;
  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

export const pauseExecution = async ({
  executionId,
  authHeaders,
}: ExecutionMachineContext) => {
  const url = `/workflow/${executionId}/pause`;
  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

export const updateVariables = async (
  { executionId, authHeaders }: ExecutionMachineContext,
  event: UpdateVariablesEvent,
) => {
  const url = `/workflow/${executionId}/variables`;

  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: event.data,
      },
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};
