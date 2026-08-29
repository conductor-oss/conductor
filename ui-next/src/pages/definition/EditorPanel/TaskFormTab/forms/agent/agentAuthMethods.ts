/**
 * The ways each hosted agent platform can authenticate, mirroring the server's own resolution.
 *
 * Order matters: the clients take the first match by which credential keys are present, so the UI
 * detects the configured method the same way and never disagrees with what will actually run.
 */
export type AgentAuthField = {
  key: string;
  label: string;
  placeholder?: string;
  optional?: boolean;
};

export type AgentAuthMethod = {
  id: string;
  label: string;
  /** What this method is for, in the user's terms. */
  hint?: string;
  fields: AgentAuthField[];
  /**
   * True when the secret holds the value directly rather than a JSON object of fields — an API key
   * is a string, a service principal is a document. Decides whether picking a stored secret writes
   * `${workflow.secrets.NAME}` or `${workflow.secrets.NAME.field}`.
   */
  wholeSecret?: boolean;
};

const API_KEY = (key: string): AgentAuthMethod => ({
  id: "apiKey",
  label: "API key",
  fields: [{ key, label: "API key" }],
  wholeSecret: true,
});

export const AGENT_AUTH_METHODS: Record<string, AgentAuthMethod[]> = {
  "azure-foundry": [
    API_KEY("apiKey"),
    {
      id: "servicePrincipal",
      label: "Service principal",
      hint: "Sign in as an Entra ID app registration: which app (client ID), its password (client secret), and the directory it belongs to (tenant ID).",
      fields: [
        { key: "client_id", label: "Client ID" },
        { key: "client_secret", label: "Client secret" },
        { key: "tenant_id", label: "Tenant ID" },
      ],
    },
    {
      id: "managedIdentity",
      label: "Managed identity",
      hint: "Azure vouches for the host this server runs on — no password to store or rotate. Name the user-assigned identity by its client ID.",
      fields: [
        { key: "managedIdentityClientId", label: "Managed identity client ID" },
      ],
      wholeSecret: true,
    },
    {
      id: "default",
      label: "The server's own Azure identity",
      hint: "Environment, workload identity, managed identity, or Azure CLI — whatever the server is running as. Nothing to configure.",
      fields: [],
    },
  ],
  bedrock: [
    API_KEY("apiKey"),
    {
      id: "staticKeys",
      label: "Access key",
      hint: "A long-lived IAM access key pair — an access key ID and its secret.",
      fields: [
        { key: "accessKeyId", label: "Access key ID" },
        { key: "secretAccessKey", label: "Secret access key" },
      ],
    },
    {
      id: "assumeRole",
      label: "Assume a role",
      hint: "Conductor assumes this role and refreshes the temporary credentials for as long as the agent runs.",
      fields: [
        {
          key: "roleArn",
          label: "Role ARN",
          placeholder: "arn:aws:iam::…:role/…",
        },
        { key: "roleSessionName", label: "Session name", optional: true },
        { key: "externalId", label: "External ID", optional: true },
      ],
    },
    {
      id: "default",
      label: "The server's own AWS credentials",
      hint: "Instance or task role, environment variables, or ~/.aws/credentials. Nothing to configure.",
      fields: [],
    },
  ],
  "openai-assistants": [API_KEY("api_key")],
};

/** Every credential key any method for this runtime could set. */
export const allAuthKeys = (runtime: string): string[] =>
  (AGENT_AUTH_METHODS[runtime] ?? []).flatMap((method) =>
    method.fields.map((field) => field.key),
  );

/**
 * Which method the current credentials represent — by the same first-match rule the server uses, so
 * the form always shows what will actually run. Falls back to the last method, which is the
 * "server's own identity" option where a provider has one.
 */
export const detectAuthMethod = (
  runtime: string,
  credentials: Record<string, unknown> | undefined,
): AgentAuthMethod | undefined => {
  const methods = AGENT_AUTH_METHODS[runtime] ?? [];
  if (methods.length === 0) return undefined;
  const present = (key: string) => {
    const value = credentials?.[key];
    return typeof value === "string" && value.trim().length > 0;
  };
  const matched = methods.find(
    (method) =>
      method.fields.length > 0 &&
      method.fields.filter((f) => !f.optional).every((f) => present(f.key)),
  );
  if (matched) return matched;
  // Nothing complete yet: keep showing whichever method the user has started filling in.
  const started = methods.find((method) =>
    method.fields.some((field) => present(field.key)),
  );
  return started ?? methods[methods.length - 1];
};

/** The reference to write for a field when the user picks a stored secret. */
export const secretReference = (
  method: AgentAuthMethod,
  field: AgentAuthField,
  secretName: string,
): string =>
  method.wholeSecret
    ? `\${workflow.secrets.${secretName}}`
    : `\${workflow.secrets.${secretName}.${field.key}}`;
