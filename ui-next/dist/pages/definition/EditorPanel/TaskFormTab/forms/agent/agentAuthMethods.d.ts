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
export declare const AGENT_AUTH_METHODS: Record<string, AgentAuthMethod[]>;
/** Every credential key any method for this runtime could set. */
export declare const allAuthKeys: (runtime: string) => string[];
/**
 * Which method the current credentials represent — by the same first-match rule the server uses, so
 * the form always shows what will actually run. Falls back to the last method, which is the
 * "server's own identity" option where a provider has one.
 */
export declare const detectAuthMethod: (runtime: string, credentials: Record<string, unknown> | undefined) => AgentAuthMethod | undefined;
/** The reference to write for a field when the user picks a stored secret. */
export declare const secretReference: (method: AgentAuthMethod, field: AgentAuthField, secretName: string) => string;
