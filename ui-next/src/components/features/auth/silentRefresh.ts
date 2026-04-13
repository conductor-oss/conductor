/**
 * Silent token refresh stubs for OSS mode (no authentication).
 * Full implementation has been moved to the enterprise package.
 *
 * All functions are no-ops since OSS mode does not use authentication.
 */

/** Reset the refresh failure flag. In OSS, this is a no-op. */
export function resetRefreshFailureFlag(): void {
  // No-op in OSS mode
}

/** Check if refresh has permanently failed. In OSS, always returns false. */
export function hasRefreshPermanentlyFailed(): boolean {
  return false;
}

/**
 * Silently refresh the access token.
 * In OSS, always returns false since there's no authentication.
 */
export async function silentlyRefreshToken(
  _oidcConfig?: unknown,
): Promise<boolean> {
  return false;
}
