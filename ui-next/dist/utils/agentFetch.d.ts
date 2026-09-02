export declare const AUTH_HEADER_NAME = "X-Authorization";
/**
 * fetch wrapper for direct /api calls in the ported agentspan executions page.
 * Injects the enterprise access token (when present) so requests succeed under
 * RBAC-enabled conductor deployments; a no-op for OSS (token is null).
 */
export declare function agentFetch(input: Parameters<typeof fetch>[0], init?: NonNullable<Parameters<typeof fetch>[1]>): Promise<Response>;
