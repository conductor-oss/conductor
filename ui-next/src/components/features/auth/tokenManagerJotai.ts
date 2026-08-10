/**
 * Token management stubs for OSS mode (no authentication).
 * Full implementation has been moved to the enterprise package.
 *
 * All functions are no-ops or return null/empty values since
 * OSS mode does not use authentication tokens.
 */
import { AuthHeaders } from "types";

export interface TokenData {
  accessToken: string;
  idToken?: string;
  refreshToken?: string;
  expiresAt?: number;
}

export interface PartialTokenData {
  accessToken?: string;
  idToken?: string;
  refreshToken?: string;
  expiresAt?: number;
}

/** Subscribe to token changes. In OSS, this is a no-op. */
export function subscribe(_listener: () => void): () => void {
  return () => {};
}

/** Store token data. In OSS, this is a no-op. */
export function setTokenData(
  _tokenData: TokenData | PartialTokenData,
  _useIdToken?: boolean,
): void {
  // No-op in OSS mode
}

/** Get token data. In OSS, always returns null. */
export function getTokenData(): TokenData | null {
  return null;
}

/** Get complete token data. In OSS, always returns nulls. */
export function getCompleteTokenData(): {
  accessToken: string | null;
  idToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
} {
  return {
    accessToken: null,
    idToken: null,
    refreshToken: null,
    expiresAt: null,
  };
}

/** Get access token. In OSS, always returns null. */
export function getAccessToken(): string | null {
  return null;
}

/** Get refresh token. In OSS, always returns null. */
export function getRefreshToken(): string | null {
  return null;
}

/** Get auth headers. In OSS, always returns empty object. */
export function getAuthHeaders(): AuthHeaders {
  return {};
}

/** Store auth headers. In OSS, this is a no-op. */
export function setAuthHeaders(_authHeaders: AuthHeaders): void {
  // No-op in OSS mode
}

/** Get stored auth headers. In OSS, always returns empty object. */
export function getStoredAuthHeaders(): AuthHeaders {
  return {};
}

/** Clear all tokens. In OSS, this is a no-op. */
export function clear(): void {
  // No-op in OSS mode
}

/** Check if token is expired. In OSS, always returns false. */
export function isTokenExpired(): boolean {
  return false;
}

/** Check if token is malformed. In OSS, always returns true (no token). */
export function isTokenMalformed(_token: string | null): boolean {
  return true;
}

/** Check if token should be refreshed. In OSS, always returns false. */
export function shouldRefreshToken(): boolean {
  return false;
}

/** Check if token can be refreshed. In OSS, always returns false. */
export function canRefreshToken(): boolean {
  return false;
}

/** Get current auth headers. In OSS, always returns empty object. */
export function getCurrentAuthHeaders(): AuthHeaders {
  return {};
}
