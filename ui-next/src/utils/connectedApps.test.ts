import {
  connectedAppInput,
  connectedAppOperationPath,
  isConnectedAppDefinition,
} from "./connectedApps";

describe("Connected Apps contract", () => {
  it("accepts only backend packaged plugin CLOUD definitions without a vendor whitelist", () => {
    expect(
      isConnectedAppDefinition({
        type: "plugin:any-new-provider",
        category: "CLOUD",
        tags: ["packaged-plugin"],
      }),
    ).toBe(true);
    for (const definition of [
      { type: "aws", category: "CLOUD", tags: ["packaged-plugin"] },
      { type: "plugin:x", category: "MCP", tags: ["packaged-plugin"] },
      { type: "plugin:x", category: "CLOUD", tags: [] },
    ])
      expect(isConnectedAppDefinition(definition)).toBe(false);
  });
  it("addresses saved integration names and encoded operation metadata, never secret resolving endpoints", () => {
    expect(connectedAppOperationPath("prod/aws")).toBe(
      "/integrations/prod%2Faws/def/apis",
    );
    expect(connectedAppOperationPath("prod/aws", "Invoke Function")).toBe(
      "/integrations/prod%2Faws/def/api/Invoke%20Function",
    );
  });
  it("resets operation and arguments on integration selection with only backend contract fields", () => {
    expect(connectedAppInput("production")).toEqual({
      integrationName: "production",
      operation: "",
      arguments: {},
    });
    expect(connectedAppInput("production", "Invoke Function")).toEqual({
      integrationName: "production",
      operation: "Invoke Function",
      arguments: {},
    });
  });
});
