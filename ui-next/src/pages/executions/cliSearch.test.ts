import {
  buildSchedulerSearchCli,
  buildTaskSearchCli,
  buildWorkflowSearchCli,
} from "./cliSearch";

describe("search CLI code generation", () => {
  it("maps supported workflow search parameters to conductor-cli flags", () => {
    expect(
      buildWorkflowSearchCli({
        start: 0,
        size: 25,
        sort: "startTime:DESC",
        freeText: "payments",
        query:
          "workflowType IN (payment_workflow) AND status IN (FAILED,TIMED_OUT) AND startTime>1700000000000 AND startTime<1800000000000",
      }),
    ).toBe(
      "conductor workflow search --count 25 --workflow 'payment_workflow' --status 'FAILED,TIMED_OUT' --start-time-after 1700000000000 --start-time-before 1800000000000 --json 'payments'",
    );
  });

  it("calls out workflow search parameters the CLI cannot represent", () => {
    expect(
      buildWorkflowSearchCli({
        start: 15,
        size: 15,
        sort: "endTime:ASC",
        freeText: "*",
        query: "workflowId='wf-123'",
      }),
    ).toContain(
      "# Not yet expressible with conductor workflow search: workflowId='wf-123'",
    );
  });

  it("maps supported scheduler search parameters", () => {
    expect(
      buildSchedulerSearchCli({
        start: 0,
        size: 10,
        sort: "startTime:DESC",
        freeText: "*",
        query: "workflowType IN (nightly) AND status IN (COMPLETED)",
      }),
    ).toBe(
      "conductor scheduler search --count 10 --workflow 'nightly' --status 'COMPLETED'",
    );
  });

  it("does not invent a task search command missing from conductor-cli", () => {
    expect(buildTaskSearchCli()).toContain(
      "Task execution search is not available in conductor-cli yet.",
    );
  });
});
