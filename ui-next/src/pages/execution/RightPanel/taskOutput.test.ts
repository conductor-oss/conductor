import { getTaskOutputForDisplay } from "./taskOutput";

describe("getTaskOutputForDisplay", () => {
  it("preserves progress output for an active retry", () => {
    const outputData = {
      elapsedSeconds: 134,
      running: true,
      status: "running",
      tokenUsed: 42_000,
      turns: [{ turn: 1, text: "Inspecting the code" }],
    };

    expect(getTaskOutputForDisplay({ outputData })).toBe(outputData);
  });
});

describe("Connected App async acceptance output", () => {
  it("preserves Event acceptance without synthesizing eventual business success", () => {
    const outputData = {
      invocationType: "Event",
      statusCode: 202,
      accepted: true,
    };
    expect(getTaskOutputForDisplay({ outputData })).toBe(outputData);
    expect(getTaskOutputForDisplay({ outputData })).not.toHaveProperty(
      "businessSuccess",
    );
  });
});
