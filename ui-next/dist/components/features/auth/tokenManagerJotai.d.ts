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
export declare function subscribe(_listener: () => void): () => void;
/** Store token data. In OSS, this is a no-op. */
export declare function setTokenData(_tokenData: TokenData | PartialTokenData, _useIdToken?: boolean): void;
/** Get token data. In OSS, always returns null. */
export declare function getTokenData(): TokenData | null;
/** Get complete token data. In OSS, always returns nulls. */
export declare function getCompleteTokenData(): {
    accessToken: string | null;
    idToken: string | null;
    refreshToken: string | null;
    expiresAt: number | null;
};
/** Get access token. In OSS, always returns null. */
export declare function getAccessToken(): string | null;
/** Get refresh token. In OSS, always returns null. */
export declare function getRefreshToken(): string | null;
/** Get auth headers. In OSS, always returns empty object. */
export declare function getAuthHeaders(): AuthHeaders;
/** Store auth headers. In OSS, this is a no-op. */
export declare function setAuthHeaders(_authHeaders: AuthHeaders): void;
/** Get stored auth headers. In OSS, always returns empty object. */
export declare function getStoredAuthHeaders(): AuthHeaders;
/** Clear all tokens. In OSS, this is a no-op. */
export declare function clear(): void;
/** Check if token is expired. In OSS, always returns false. */
export declare function isTokenExpired(): boolean;
/** Check if token is malformed. In OSS, always returns true (no token). */
export declare function isTokenMalformed(_token: string | null): boolean;
/** Check if token should be refreshed. In OSS, always returns false. */
export declare function shouldRefreshToken(): boolean;
/** Check if token can be refreshed. In OSS, always returns false. */
export declare function canRefreshToken(): boolean;
/** Get current auth headers. In OSS, always returns empty object. */
export declare function getCurrentAuthHeaders(): AuthHeaders;
