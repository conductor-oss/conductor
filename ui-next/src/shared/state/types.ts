/**
 * Minimal auth state types for OSS mode (no authentication).
 * Full auth implementation has been moved to the enterprise package.
 */

/**
 * Supported auth providers. In OSS, only NO_USER is used.
 */
export enum SupportedProviders {
  AUTH_0 = "auth0",
  OKTA = "okta",
  OIDC = "oidc",
  NO_USER = "NO_USER",
}

/**
 * Auth provider states. In OSS, only UNLOGGED, NO_USER_MANAGEMENT, and SIDEBAR_INIT are used.
 */
export enum AuthProviderStates {
  UNLOGGED = "UNLOGGED",
  NO_USER_MANAGEMENT = "NO_USER_MANAGEMENT",
  SIDEBAR_INIT = "SIDEBAR_INIT",
  // Keep these for type compatibility with selectors
  LOGGED_USER = "LOGGED_USER",
  MANAGE_PROVIDER = "MANAGE_PROVIDER",
}

/**
 * Auth machine event types. In OSS, most events are no-ops.
 */
export enum AuthMachineEventTypes {
  LOGOUT = "LOGOUT",
  SOLVE_EXPIRED_TOKEN = "SOLVE_EXPIRED_TOKEN",
  DISMISS_ANNOUNCEMENT_BANNER = "DISMISS_ANNOUNCEMENT_BANNER",
  // Keep these for type compatibility
  SET_USER_CREDENTIALS = "SET_USER_CREDENTIALS",
  SET_PROVIDER_USER = "SET_PROVIDER_USER",
  SET_TOKEN = "SET_TOKEN",
  FETCH_FOR_USER_INFO = "FETCH_FOR_USER_INFO",
  FETCH_TOKEN = "FETCH_TOKEN",
  REFRESH_OIDC_TOKEN = "REFRESH_OIDC_TOKEN",
  REDIRECT_TO_AUTHORIZATION_ENDPOINT = "REDIRECT_TO_AUTHORIZATION_ENDPOINT",
}

/**
 * Minimal auth provider machine context for OSS mode.
 */
export interface AuthProviderMachineContext {
  provider: SupportedProviders;
  error?: unknown;
  providerUser?: unknown;
  conductorUser?: { id: string };
  isTrialExpired: boolean;
  trialExpiryDate?: number | Date;
  limits?: unknown;
  isAnnouncementBannerDismissed: boolean;
  oidcConfig?: unknown;
}

/**
 * Auth provider machine events union type.
 */
export type AuthProviderMachineEvents =
  | { type: AuthMachineEventTypes.LOGOUT }
  | { type: AuthMachineEventTypes.SOLVE_EXPIRED_TOKEN }
  | { type: AuthMachineEventTypes.DISMISS_ANNOUNCEMENT_BANNER }
  | { type: AuthMachineEventTypes.SET_TOKEN; data: { token: string } }
  | { type: AuthMachineEventTypes.SET_PROVIDER_USER; user: unknown }
  | {
      type: AuthMachineEventTypes.REDIRECT_TO_AUTHORIZATION_ENDPOINT;
      currentPath: string;
    }
  | {
      type: AuthMachineEventTypes.FETCH_TOKEN;
      data: { code?: string; state?: string };
    };
