import {
  AGENT_AUTH_METHODS,
  allAuthKeys,
  detectAuthMethod,
  secretReference,
} from "./agentAuthMethods";

describe("agent auth methods", () => {
  it("detects the configured method the way the server resolves it", () => {
    // First match wins, and an API key beats a service principal on the same credential —
    // exactly what AzureFoundryAuth does, so the form cannot disagree with what will run.
    expect(
      detectAuthMethod("microsoft-foundry", {
        apiKey: "sk",
        client_id: "cid",
        client_secret: "cs",
        tenant_id: "tid",
      })?.id,
    ).toBe("apiKey");

    expect(
      detectAuthMethod("microsoft-foundry", {
        client_id: "cid",
        client_secret: "cs",
        tenant_id: "tid",
      })?.id,
    ).toBe("servicePrincipal");

    expect(
      detectAuthMethod("microsoft-foundry", { managedIdentityClientId: "mi" })?.id,
    ).toBe("managedIdentity");
  });

  it("falls back to the server's own identity when nothing is configured", () => {
    expect(detectAuthMethod("microsoft-foundry", {})?.id).toBe("default");
    expect(detectAuthMethod("microsoft-foundry", undefined)?.id).toBe("default");
    expect(detectAuthMethod("bedrock", {})?.id).toBe("default");
  });

  it("keeps showing a method that is only half filled in", () => {
    // Otherwise the form would jump back to "the server's own identity" mid-typing.
    expect(detectAuthMethod("microsoft-foundry", { client_id: "cid" })?.id).toBe(
      "servicePrincipal",
    );
    expect(detectAuthMethod("bedrock", { roleArn: "arn:…" })?.id).toBe(
      "assumeRole",
    );
  });

  it("treats blank values as unset", () => {
    expect(detectAuthMethod("microsoft-foundry", { apiKey: "   " })?.id).toBe(
      "default",
    );
  });

  it("writes a whole-secret reference for a single-value credential", () => {
    const apiKey = AGENT_AUTH_METHODS["openai-assistants"][0];
    // An API key is stored as a string, so the whole secret is the value.
    expect(secretReference(apiKey, apiKey.fields[0], "OPENAI_KEY")).toBe(
      "${workflow.secrets.OPENAI_KEY}",
    );
  });

  it("writes per-key references for a multi-field credential", () => {
    const sp = AGENT_AUTH_METHODS["microsoft-foundry"].find(
      (m) => m.id === "servicePrincipal",
    )!;
    // A service principal is stored as a JSON document, so each field is a path into it.
    expect(sp.fields.map((f) => secretReference(sp, f, "AZURE_CRED"))).toEqual([
      "${workflow.secrets.AZURE_CRED.client_id}",
      "${workflow.secrets.AZURE_CRED.client_secret}",
      "${workflow.secrets.AZURE_CRED.tenant_id}",
    ]);
  });

  it("knows every key a runtime's methods can set, so switching clears them", () => {
    expect(allAuthKeys("bedrock")).toEqual(
      expect.arrayContaining([
        "apiKey",
        "accessKeyId",
        "secretAccessKey",
        "roleArn",
        "roleSessionName",
        "externalId",
      ]),
    );
    expect(allAuthKeys("a2a")).toEqual([]);
  });

  it("offers only what each provider actually supports", () => {
    expect(AGENT_AUTH_METHODS["openai-assistants"].map((m) => m.id)).toEqual([
      "apiKey",
    ]);
    expect(AGENT_AUTH_METHODS["microsoft-foundry"].map((m) => m.id)).toEqual([
      "apiKey",
      "servicePrincipal",
      "managedIdentity",
      "default",
    ]);
    expect(AGENT_AUTH_METHODS["bedrock"].map((m) => m.id)).toEqual([
      "apiKey",
      "staticKeys",
      "assumeRole",
      "default",
    ]);
  });
});
