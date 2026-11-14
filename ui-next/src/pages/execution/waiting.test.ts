import { describe, expect, it } from "vitest";
import {
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
  nativeState: { codeType: "kitchensink.v1.code", bootstrap },
  ...extra,
});

describe("waitingCode", () => {
  it("names the code type and lane of a continuation offered and not yet run", () => {
    expect(
      waitingCode(
        execution(
          marker("LEASED", { queuedAt: 100, queue: "kitchensink.v1.code" }),
        ),
      ),
    ).toEqual({
      codeType: "kitchensink.v1.code",
      domain: undefined,
      lane: "main",
      since: 100,
    });
  });

  it("reads the domain from the queue it was offered in", () => {
    expect(
      waitingCode(
        execution(marker("RUNNABLE", { queue: "team-a:kitchensink.v1.code" })),
      )?.domain,
    ).toBe("team-a");
  });

  it("is nothing once the code ran, while held, or when the run ended", () => {
    expect(waitingCode(execution(marker("APPLIED")))).toBeUndefined();
    expect(
      waitingCode(
        execution(marker("RUNNABLE", { deferred: "diverged: changed code" })),
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
              queuedAt: 50,
            }),
          },
          { continuation: marker("LEASED", { lane: "main", queuedAt: 70 }) },
        ],
      }),
    );
    expect(waiting?.lane).toBe("fork_1.0");
    expect(waiting?.since).toBe(50);
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

  it("counts only polls in the continuation's domain", () => {
    const polls = [
      { domain: "dev", lastPollTime: 900 },
      { lastPollTime: 500 },
      { domain: "", lastPollTime: 600 },
    ];
    expect(lastPoll(waiting, polls)).toBe(600);
    expect(lastPoll({ ...waiting, domain: "dev" }, polls)).toBe(900);
    expect(lastPoll({ ...waiting, domain: "team-a" }, polls)).toBeUndefined();
  });
});
