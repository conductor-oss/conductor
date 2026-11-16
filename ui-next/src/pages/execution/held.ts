import { ExecutionTask, TaskContinuation } from "types/Execution";

const HELD_PREFIX = "diverged: ";

const TERMINAL = new Set([
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "FAILED",
  "FAILED_WITH_TERMINAL_ERROR",
  "CANCELED",
  "TIMED_OUT",
  "SKIPPED",
]);

/** Where a continuation stands, as the server decides it from its marker and its task. */
export type ContinuationState = "waiting" | "runnable" | "leased" | "applied";

/** A continuation of the code: which one it is, where it stands, and its marker. */
export interface Continuation {
  /** The lane of the code, such as main. */
  lane: string;
  /** Its number in the lane. */
  n: number;
  state: ContinuationState;
  /** Since when it could run: when its task ended, or when the run started. */
  since?: number;
  marker: TaskContinuation;
  /** The task it waits for; none for the first continuation and the handler lane. */
  task?: ExecutionTask;
}

const stateOf = (
  marker: TaskContinuation,
  canRun: boolean,
): ContinuationState =>
  marker.endTime
    ? "applied"
    : marker.leased
      ? "leased"
      : canRun
        ? "runnable"
        : "waiting";

/** The continuation a task's marker is: the one its entry names. */
export const continuationOf = (
  task: ExecutionTask,
): Continuation | undefined => {
  const marker = task.continuation;
  if (!marker) {
    return undefined;
  }
  const metadata = task.workflowTask?.metadata ?? {};
  const ended = TERMINAL.has(String(task.status));
  return {
    lane: String(metadata.lane ?? "main"),
    n: Number(metadata.continuation ?? 0),
    state: stateOf(marker, ended),
    since: ended ? task.endTime : undefined,
    marker,
    task,
  };
};

/**
 * Every continuation of an execution: its first one, its handler lane's and those on its tasks.
 * The first is always main #1, runnable until it applied; the handler lane runs when messages
 * arrive, which only the server sees.
 */
export const continuationsOf = (execution?: {
  startTime?: number | string;
  createTime?: number | string;
  nativeState?: { bootstrap?: TaskContinuation; handlers?: TaskContinuation };
  tasks?: ExecutionTask[];
}): Continuation[] => {
  const state = execution?.nativeState;
  return [
    state?.bootstrap && {
      lane: "main",
      n: 1,
      state: stateOf(state.bootstrap, true),
      since: Number(execution?.startTime ?? execution?.createTime) || undefined,
      marker: state.bootstrap,
    },
    state?.handlers && {
      lane: "handlers",
      n: state.handlers.n ?? 1,
      state: stateOf(state.handlers, false),
      marker: state.handlers,
    },
    ...(execution?.tasks ?? []).map(continuationOf),
  ].filter(Boolean) as Continuation[];
};

export interface HeldMismatch {
  /** Where in the code the mismatch is, such as Held.run:52. */
  site: string;
  /** What the code calls there now. */
  calls: string;
  /** What this execution did there, or undefined when it recorded nothing further. */
  recorded?: string;
}

const MISMATCH =
  /^The running code differs from what this execution recorded: at (.+?) it calls (.+?), but the recording has (.+?) there\./;

/** The parts of the SDK's mismatch message; undefined for any other reason, shown as it is. */
export const heldMismatch = (reason: string): HeldMismatch | undefined => {
  const match = MISMATCH.exec(reason);
  return match
    ? {
        site: match[1],
        calls: match[2],
        recorded: match[3] === "no further call" ? undefined : match[3],
      }
    : undefined;
};

/** A finished execution is no longer held, whatever its last marker says. */
export const canBeHeld = (execution?: object): boolean => {
  const status = (execution as { status?: unknown } | undefined)?.status;
  return status === "RUNNING" || status === "PAUSED";
};

/**
 * Why a native workflow is held: its code no longer matches its recording. It stays running and
 * continues once a compatible build is deployed. Undefined when it is not held. One continuation
 * runs at a time, so the reason is the run's.
 */
export const heldReason = (execution?: {
  status?: unknown;
  reasonForIncompletion?: string;
}): string | undefined => {
  const reason = execution?.reasonForIncompletion;
  return canBeHeld(execution) && reason?.startsWith(HELD_PREFIX)
    ? reason.slice(HELD_PREFIX.length)
    : undefined;
};

/**
 * Why the code that runs after this task is held, or undefined: the run is held at the one
 * continuation it has leased.
 */
export const heldAt = (
  execution: Parameters<typeof heldReason>[0],
  task?: { continuation?: TaskContinuation },
): string | undefined =>
  task?.continuation?.leased ? heldReason(execution) : undefined;
