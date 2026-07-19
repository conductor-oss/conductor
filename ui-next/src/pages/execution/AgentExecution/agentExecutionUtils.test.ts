import { describe, expect, it } from "vitest";

import {
  isFailedTaskStatus,
  mapTaskStatus,
  taskSuccess,
} from "./agentExecutionUtils";
import { AgentStatus } from "./types";

// Regression coverage for issue #4260: a FAILED_WITH_TERMINAL_ERROR task (and
// the other terminal-failure statuses) previously fell through to RUNNING,
// rendering a perpetual "running" chip + spinner for an already-failed task.

describe("mapTaskStatus", () => {
  it("maps COMPLETED to COMPLETED", () => {
    expect(mapTaskStatus("COMPLETED")).toBe(AgentStatus.COMPLETED);
  });

  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "maps terminal-failure status %s to FAILED",
    (status) => {
      expect(mapTaskStatus(status)).toBe(AgentStatus.FAILED);
    },
  );

  it.each(["IN_PROGRESS", "SCHEDULED", "PENDING"])(
    "maps in-progress status %s to RUNNING",
    (status) => {
      expect(mapTaskStatus(status)).toBe(AgentStatus.RUNNING);
    },
  );

  it("does not fall through to RUNNING for unknown/other terminal statuses", () => {
    expect(mapTaskStatus("SKIPPED")).toBe(AgentStatus.FAILED);
    expect(mapTaskStatus("COMPLETED_WITH_ERRORS")).toBe(AgentStatus.FAILED);
    expect(mapTaskStatus("SOME_FUTURE_STATUS")).toBe(AgentStatus.FAILED);
  });
});

describe("taskSuccess", () => {
  it("returns true for COMPLETED", () => {
    expect(taskSuccess("COMPLETED")).toBe(true);
  });

  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "returns false for terminal-failure status %s",
    (status) => {
      expect(taskSuccess(status)).toBe(false);
    },
  );

  it.each(["IN_PROGRESS", "SCHEDULED", "PENDING"])(
    "returns undefined (spinner) for in-progress status %s",
    (status) => {
      expect(taskSuccess(status)).toBeUndefined();
    },
  );

  it("never returns undefined (perpetual spinner) for a terminal status", () => {
    expect(taskSuccess("SKIPPED")).toBe(false);
    expect(taskSuccess("COMPLETED_WITH_ERRORS")).toBe(false);
    expect(taskSuccess("NULL")).toBe(false);
  });
});

describe("isFailedTaskStatus", () => {
  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "treats %s as a terminal failure",
    (status) => {
      expect(isFailedTaskStatus(status)).toBe(true);
    },
  );

  it.each(["COMPLETED", "IN_PROGRESS", "SCHEDULED", "SKIPPED"])(
    "does not treat %s as a terminal failure",
    (status) => {
      expect(isFailedTaskStatus(status)).toBe(false);
    },
  );
});
