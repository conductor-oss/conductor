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

const isPreview = (t: any): boolean => t?.metadata?.preview === true;

const childLists = (t: any): any[][] => [
  ...(Object.values(t.decisionCases ?? {}) as any[][]),
  t.defaultCase ?? [],
  ...(t.forkTasks ?? []),
  t.loopOver ?? [],
];

const refsOf = (list: any[] = []): string[] =>
  list.flatMap((t) => [t.taskReferenceName, ...childLists(t).flatMap(refsOf)]);

const sameStep = (a: any, b: any): boolean =>
  a?.metadata?.site != null &&
  a.metadata.site === b?.metadata?.site &&
  a.type === b?.type;

/**
 * A running native execution's definition with what the code has yet to do: in each list the code
 * is in (the top level, the arm of a switch it is inside, the branches of a fork it is waiting
 * for), the registered entries after the one at the call site of the last step that ran, as
 * previews. A list whose last step the registered definition does not have is left as it is.
 */
export const withWhatIsAhead = (own: any[], registered: any[]): any[] => {
  const used = new Set(refsOf(own));
  const asPreview = (entry: any): any => {
    const renamed = new Map<string, string>();
    const copy = (t: any): any => {
      let ref = t.taskReferenceName;
      while (used.has(ref)) ref = `${ref}_next`;
      used.add(ref);
      renamed.set(t.taskReferenceName, ref);
      const c: any = {
        ...t,
        taskReferenceName: ref,
        metadata: { ...t.metadata, preview: true },
      };
      if (t.decisionCases) {
        c.decisionCases = Object.fromEntries(
          Object.entries(t.decisionCases).map(([k, v]) => [
            k,
            (v as any[]).map(copy),
          ]),
        );
      }
      if (t.defaultCase) c.defaultCase = t.defaultCase.map(copy);
      if (t.forkTasks) c.forkTasks = t.forkTasks.map((b: any[]) => b.map(copy));
      if (t.loopOver) c.loopOver = t.loopOver.map(copy);
      if (t.joinOn) c.joinOn = t.joinOn.map((r: string) => renamed.get(r) ?? r);
      return c;
    };
    return copy(entry);
  };
  const extend = (list: any[], regList: any[]): any[] => {
    const ran = list.filter((t) => !isPreview(t));
    const last = ran[ran.length - 1];
    let at = -1;
    regList.forEach((r, i) => {
      if (sameStep(r, last)) at = i; // the last: a fork and its join share their site
    });
    if (at < 0) return list;
    // what the code is inside now: the last step, or the fork its join waits for
    const inside = new Set([last]);
    if (last.type === "JOIN" && ran.length > 1) inside.add(ran[ran.length - 2]);
    const out = list.map((t) => {
      const reg = inside.has(t)
        ? regList.find((r) => sameStep(r, t))
        : undefined;
      if (!reg) return t;
      const regLists = childLists(reg);
      const into = (l: any[]) => {
        const steps = l.filter((x) => !isPreview(x));
        const step = steps[steps.length - 1];
        const match = regLists.find((rl) => rl.some((r) => sameStep(r, step)));
        return match ? extend(l, match) : l;
      };
      const c: any = { ...t };
      if (t.decisionCases) {
        c.decisionCases = Object.fromEntries(
          Object.entries(t.decisionCases).map(([k, v]) => [
            k,
            into(v as any[]),
          ]),
        );
      }
      if (t.defaultCase) c.defaultCase = into(t.defaultCase);
      if (t.forkTasks) c.forkTasks = t.forkTasks.map(into);
      return c;
    });
    return [...out, ...regList.slice(at + 1).map(asPreview)];
  };
  return extend(own, registered);
};

/**
 * A running native execution is shown with what its code has yet to do, read from the registered
 * definition the SDK drew from the code: all of it while the code has not run yet, and the rest
 * after where it is once it has.
 */
export const withCodePreview = async (
  execution: any,
  fetchDefinition: (name: string, version: number) => Promise<any>,
) => {
  const own = execution?.workflowDefinition;
  if (!own?.metadata?.native || !canBeHeld(execution)) {
    return execution;
  }
  try {
    const registered = await fetchDefinition(
      execution.workflowName ?? own.name,
      execution.workflowVersion ?? own.version,
    );
    const drawn = registered?.tasks ?? [];
    if (!drawn.length) {
      return execution;
    }
    if ((own.tasks ?? []).length === 0) {
      return {
        ...execution,
        codePreview: true,
        workflowDefinition: { ...own, tasks: drawn },
      };
    }
    const tasks = withWhatIsAhead(own.tasks, drawn);
    return refsOf(tasks).length === refsOf(own.tasks).length
      ? execution
      : {
          ...execution,
          codeAhead: true,
          workflowDefinition: { ...own, tasks },
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
