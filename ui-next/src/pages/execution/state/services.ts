import { fetchWithContext } from "plugins/fetch";
import {
  ExecutionMachineContext,
  RestartExecutionEvent,
  RetryExecutionEvent,
  UpdateVariablesEvent,
} from "./types";
import { getErrors } from "utils";
import {
  fetchAgentExecution,
  fetchExecution as fetchWorkflowExecution,
} from "commonServices";
import { toMaybeQueryString } from "utils/toMaybeQueryString";
import { maybeTriggerFailureWorkflow } from "utils/maybeTriggerWorkflow";
import { codeLoopsAsIterations } from "./codeLoops";
import { canBeHeld } from "../held";

/**
 * A native execution whose code has not run yet has an empty definition of its own: the
 * registered one, which the SDK read from the code, is shown instead as a preview.
 */
export const withCodePreview = async (
  execution: any,
  fetchDefinition: (name: string, version: number) => Promise<any>,
) => {
  const own = execution?.workflowDefinition;
  if (
    !own?.metadata?.native ||
    (own.tasks ?? []).length > 0 ||
    !canBeHeld(execution)
  ) {
    return execution;
  }
  try {
    const registered = await fetchDefinition(
      execution.workflowName ?? own.name,
      execution.workflowVersion ?? own.version,
    );
    if (!(registered?.tasks ?? []).length) {
      return execution;
    }
    return {
      ...execution,
      codePreview: true,
      workflowDefinition: { ...own, tasks: registered.tasks },
    };
  } catch {
    return execution; // no preview: the execution is shown as it is
  }
};

export const fetchExecution = (context: ExecutionMachineContext) => {
  const request = {
    executionId: context.executionId!,
    authHeaders: context.authHeaders ?? {},
  };
  const fetchDefinition = (name: string, version: number) =>
    fetchWithContext(
      `/metadata/workflow/${encodeURIComponent(name)}?version=${version}`,
      {},
      { headers: request.authHeaders },
    );
  return context.agentExecution
    ? fetchAgentExecution(request)
    : fetchWorkflowExecution(request)
        .then((execution) => withCodePreview(execution, fetchDefinition))
        .then(codeLoopsAsIterations);
};

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
