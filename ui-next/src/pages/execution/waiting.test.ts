import { describe, expect, it } from "vitest";
import {
  appliedContinuations,
  codeTypeOf,
  lastPoll,
  NO_WORKER_AFTER_MS,
  waitingCode,
  waitingForWorker,
} from "./waiting";

const marker = (state: string, extra: object = {}) => ({
  lane: "main",
  n: 1,
  state,
  ...extra,
});

const execution = (bootstrap: any, extra: object = {}): any => ({
  status: "RUNNING",
  tasks: [],
  workflowDefinition: {
    name: "kitchensink",
    version: 1,
    metadata: { native: {} },
  },
  nativeState: { bootstrap },
  ...extra,
});

describe("waitingCode", () => {
  it("reads the first continuation as the server stores it, without its lane and number", () => {
    expect(
      waitingCode(execution({ state: "RUNNABLE", scheduledTime: 100 })),
    ).toEqual({ codeType: "kitchensink.v1.code", lane: "main", since: 100 });
    expect(appliedContinuations(execution({ state: "APPLIED" }))).toBe(1);
  });

  it("names the code type and lane of a continuation offered and not yet run", () => {
    expect(
      waitingCode(execution(marker("LEASED", { scheduledTime: 100 }))),
    ).toEqual({
      codeType: "kitchensink.v1.code",
      lane: "main",
      since: 100,
    });
  });

  it("names the code type after the workflow and its version, as the server does", () => {
    const def = (metadata: object) => ({
      workflowDefinition: { name: "orders", version: 3, metadata },
    });
    expect(codeTypeOf(def({ native: {} }) as any)).toBe("orders.v3.code");
    expect(codeTypeOf(def({}) as any)).toBeUndefined();
  });

  it("is nothing once the code ran, while held, or when the run ended", () => {
    expect(waitingCode(execution(marker("APPLIED")))).toBeUndefined();
    expect(
      waitingCode(
        execution(
          marker("RUNNABLE", {
            reasonForIncompletion: "diverged: changed code",
          }),
        ),
      ),
    ).toBeUndefined();
    expect(
      waitingCode(execution(marker("LEASED"), { status: "TERMINATED" })),
    ).toBeUndefined();
    expect(
      waitingCode({ status: "RUNNING", tasks: [] } as any),
    ).toBeUndefined();
  });

  it("takes the continuation that has waited longest, on a task too", () => {
    const waiting = waitingCode(
      execution(marker("APPLIED"), {
        tasks: [
          {
            continuation: marker("RUNNABLE", {
              lane: "fork_1.0",
              scheduledTime: 50,
            }),
          },
          {
            continuation: marker("LEASED", { lane: "main", scheduledTime: 70 }),
          },
        ],
      }),
    );
    expect(waiting?.lane).toBe("fork_1.0");
    expect(waiting?.since).toBe(50);
  });
});

describe("appliedContinuations", () => {
  it("counts the applied bootstrap and task continuations", () => {
    expect(
      appliedContinuations(
        execution(marker("APPLIED"), {
          tasks: [
            { continuation: marker("APPLIED") },
            { continuation: marker("LEASED") },
            {},
          ],
        }),
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
