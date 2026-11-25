import { pluginRegistry } from "plugins/registry";
import { getAccessToken as getAccessTokenStub } from "components/features/auth/tokenManagerJotai";

export const AUTH_HEADER_NAME = "X-Authorization";

// Get access token from plugin registry (enterprise) or fallback to stub (OSS).
// Mirrors the resolution used by utils/query.ts so direct /api calls in the
// ported agentspan executions components work under RBAC-enabled deployments.
function getAccessToken(): string | null {
  const pluginToken = pluginRegistry.getAccessToken();
  if (pluginToken) {
    return pluginToken;
  }
  return getAccessTokenStub();
}

/**
 * fetch wrapper for direct /api calls in the ported agentspan executions page.
 * Injects the enterprise access token (when present) so requests succeed under
 * RBAC-enabled conductor deployments; a no-op for OSS (token is null).
 */
export function agentFetch(
  input: Parameters<typeof fetch>[0],
  init: NonNullable<Parameters<typeof fetch>[1]> = {},
): Promise<Response> {
  const token = getAccessToken();
  const headers = new Headers(init.headers);
  if (token && !headers.has(AUTH_HEADER_NAME)) {
    headers.set(AUTH_HEADER_NAME, token);
  }
  return fetch(input, { ...init, headers });
}
