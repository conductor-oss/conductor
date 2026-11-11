import {
  ExecutionTask,
  TaskContinuation,
  WorkflowExecution,
} from "types/Execution";

const HELD_PREFIX = "diverged: ";

/** Why the code is held at this continuation, or undefined when it is not. */
export const heldReasonOfMarker = (
  marker?: TaskContinuation,
): string | undefined =>
  marker?.deferred?.startsWith(HELD_PREFIX)
    ? marker.deferred.slice(HELD_PREFIX.length)
    : undefined;

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
 * continues once a compatible build is deployed. Undefined when it is not held.
 */
export const heldReason = (
  execution?: WorkflowExecution,
): string | undefined => {
  if (!canBeHeld(execution)) {
    return undefined;
  }
  const markers = [
    execution?.nativeState?.bootstrap,
    execution?.nativeState?.handlers,
    ...(execution?.tasks ?? []).map((task: ExecutionTask) => task.continuation),
  ];
  return markers.map(heldReasonOfMarker).find(Boolean);
};
