import { WorkflowExecution } from "types/Execution";
import { PollData } from "utils/query";
import { canBeHeld, Continuation, continuationsOf, heldReason } from "./held";

/**
 * A native workflow runs only while something hosts its code. A continuation the server offered
 * and no worker has run yet waits in the code type's queue; when no worker polls that queue, the
 * execution waits for one to start, and nothing else happens in the meantime.
 */
export interface WaitingCode {
  /** The code type workers poll, such as kitchensink.v1.code. */
  codeType: string;
  /** The lane the continuation runs, such as main. */
  lane: string;
  /** Since when it could run. */
  since?: number;
}

/** A worker that last polled longer ago than this is not counted as running. */
export const NO_WORKER_AFTER_MS = 30_000;

const offered = (continuation: Continuation): boolean =>
  continuation.state === "runnable" || continuation.state === "leased";

/** The task type a native workflow's hosts poll: `<name>.v<version>.code`, one per version. */
export const codeTypeOf = (
  execution?: WorkflowExecution,
): string | undefined => {
  const def: any = execution?.workflowDefinition;
  return def?.metadata?.native ? `${def.name}.v${def.version}.code` : undefined;
};

/** How many continuations of the code have applied: the first one and those on tasks. */
export const appliedContinuations = (execution?: WorkflowExecution): number =>
  continuationsOf(execution).filter(
    (continuation) =>
      continuation.lane !== "handlers" && continuation.state === "applied",
  ).length;

/** The oldest continuation offered to the code's workers and not yet applied, if any. */
export const waitingCode = (
  execution?: WorkflowExecution,
): WaitingCode | undefined => {
  const codeType = codeTypeOf(execution);
  // held: a worker ran it, and the code did not match
  if (!codeType || !canBeHeld(execution) || heldReason(execution)) {
    return undefined;
  }
  const offers = continuationsOf(execution).filter(offered);
  if (offers.length === 0) {
    return undefined;
  }
  const oldest = offers.reduce((a, b) =>
    (b.since ?? Infinity) < (a.since ?? Infinity) ? b : a,
  );
  return {
    codeType,
    lane: oldest.lane,
    since: oldest.since,
  };
};

/** When a worker last polled the code type's shared queue, if ever: workflows have no domains. */
export const lastPoll = (pollData: PollData[] = []): number | undefined => {
  const times = pollData
    .filter((p) => !p.domain)
    .map((p) => p.lastPollTime ?? 0)
    .filter((t) => t > 0);
  return times.length > 0 ? Math.max(...times) : undefined;
};

/**
 * Why the execution waits for a worker, or undefined when a worker is polling (a continuation
 * then waits a moment at most) or nothing is offered.
 */
export const waitingForWorker = (
  waiting: WaitingCode | undefined,
  polled: number | undefined,
  now: number,
): string | undefined => {
  if (!waiting || (polled !== undefined && now - polled < NO_WORKER_AFTER_MS)) {
    return undefined;
  }
  const last =
    polled === undefined
      ? "no worker has polled it"
      : `no worker has polled it since ${new Date(polled).toLocaleString()}`;
  return `Waiting for a worker of ${waiting.codeType}: ${last}. The code runs when one starts.`;
};
