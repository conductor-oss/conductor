import { describe, expect, it } from "vitest";
import {
  appliedContinuations,
  codeTypeOf,
  lastPoll,
  NO_WORKER_AFTER_MS,
  waitingCode,
  waitingForWorker,
} from "./waiting";

const execution = (bootstrap: object, extra: object = {}): any => ({
  status: "RUNNING",
  startTime: 100,
  tasks: [],
  workflowDefinition: {
    name: "kitchensink",
    version: 1,
    metadata: { native: {} },
  },
  nativeState: { bootstrap },
  ...extra,
});

/** A task the code paused on: its entry names the lane and the continuation. */
const paused = (
  lane: string,
  status: string,
  continuation: object,
  endTime?: number,
) => ({
  status,
  endTime,
  workflowTask: { metadata: { lane, continuation: 2 } },
  continuation,
});

describe("waitingCode", () => {
  it("waits for the first continuation from the run's start until it applied", () => {
    expect(waitingCode(execution({}))).toEqual({
      codeType: "kitchensink.v1.code",
      lane: "main",
      since: 100,
    });
    expect(waitingCode(execution({ leased: true }))?.since).toBe(100);
    expect(waitingCode(execution({ endTime: 5 }))).toBeUndefined();
    expect(appliedContinuations(execution({ endTime: 5 }))).toBe(1);
  });

  it("names the code type after the workflow and its version, as the server does", () => {
    const def = (metadata: object) => ({
      workflowDefinition: { name: "orders", version: 3, metadata },
    });
    expect(codeTypeOf(def({ native: {} }) as any)).toBe("orders.v3.code");
    expect(codeTypeOf(def({}) as any)).toBeUndefined();
  });

  it("is nothing while held, when the run ended, or for a lane whose task still runs", () => {
    expect(
      waitingCode(
        execution(
          { leased: true },
          { reasonForIncompletion: "diverged: changed code" },
        ),
      ),
    ).toBeUndefined();
    expect(
      waitingCode(execution({ leased: true }, { status: "TERMINATED" })),
    ).toBeUndefined();
    expect(
      waitingCode({ status: "RUNNING", tasks: [] } as any),
    ).toBeUndefined();
    expect(
      waitingCode(
        execution(
          { endTime: 5 },
          { tasks: [paused("main", "IN_PROGRESS", {})] },
        ),
      ),
    ).toBeUndefined();
  });

  it("takes the continuation whose task ended first", () => {
    const waiting = waitingCode(
      execution(
        { endTime: 5 },
        {
          tasks: [
            paused("main", "COMPLETED", { leased: true }, 70),
            paused("fork_1.0", "COMPLETED", {}, 50),
          ],
        },
      ),
    );
    expect(waiting?.lane).toBe("fork_1.0");
    expect(waiting?.since).toBe(50);
  });
});

describe("appliedContinuations", () => {
  it("counts the applied bootstrap and task continuations", () => {
    expect(
      appliedContinuations(
        execution(
          { endTime: 5 },
          {
            tasks: [
              paused("main", "COMPLETED", { endTime: 7 }, 6),
              paused("main", "COMPLETED", { leased: true }, 8),
              {},
            ],
          },
        ),
      ),
    ).toBe(2);
    expect(appliedContinuations(undefined)).toBe(0);
  });
});

describe("waitingForWorker", () => {
  const waiting = { codeType: "kitchensink.v1.code", lane: "main" };

  it("says no worker polls when none ever did, or none did lately", () => {
    expect(waitingForWorker(waiting, undefined, 1_000_000)).toContain(
      "Waiting for a worker of kitchensink.v1.code: no worker has polled it.",
    );
    expect(
      waitingForWorker(waiting, 1_000_000 - NO_WORKER_AFTER_MS - 1, 1_000_000),
    ).toContain("no worker has polled it since");
  });

  it("is nothing while a worker polls: the continuation only waits a moment", () => {
    expect(waitingForWorker(waiting, 999_000, 1_000_000)).toBeUndefined();
    expect(waitingForWorker(undefined, undefined, 1)).toBeUndefined();
  });

  it("counts only polls of the shared queue: workflows have no domains", () => {
    const polls = [
      { domain: "dev", lastPollTime: 900 },
      { lastPollTime: 500 },
      { domain: "", lastPollTime: 600 },
    ];
    expect(lastPoll(polls)).toBe(600);
    expect(lastPoll([{ domain: "dev", lastPollTime: 900 }])).toBeUndefined();
  });
});
