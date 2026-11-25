import { buildEndpoint } from "./agentSearchCode";

describe("buildEndpoint", () => {
  it("preserves agent search filters in generated code", () => {
    expect(
      buildEndpoint({
        start: 20,
        size: 10,
        sort: "startTime:DESC",
        freeText: "*",
        query: "status=RUNNING",
        classifier: "agent",
        topLevelOnly: true,
      }),
    ).toBe(
      `${window.location.origin}/api/workflow/search?start=20&size=10&sort=startTime%3ADESC&freeText=*&query=status%3DRUNNING&classifier=agent&topLevelOnly=true`,
    );
  });
});
