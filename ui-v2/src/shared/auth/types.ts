/**
 * Auth state returned by useAuth().
 * Default is no auth (defaultAuthState). OSS or custom providers can set
 * authState in AuthContext to enable auth without the enterprise package.
 */
import { SupportedProviders } from "../state/types";
import { User } from "types/User";

export interface AuthState {
  user: unknown;
  isAuthenticated: boolean;
  isTrialExpired: boolean;
  trialExpiryDate: number | Date | undefined;
  isAnnouncementBannerDismissed: boolean;
  provider: SupportedProviders;
  conductorUser: User | undefined;
  oidcConfig: unknown;
  authService: unknown;
  fetchingUserInformation: boolean;
  logOut: () => void;
  solveExpireToken: () => void;
  setToken: (token: string) => void;
  redirectToAuthorizationEndpoint: (currentPath: string) => void;
  fetchOidcTokenWithCode: (code: string, stateParam: string) => void;
  dismissAnnouncementBanner: () => void;
}

const noop = () => {};
const noopSetToken = (_token: string) => {};
const noopRedirect = (_currentPath: string) => {};
const noopFetchOidc = (_code: string, _stateParam: string) => {};

/** Default when no auth is configured. OSS can add auth by providing a custom auth provider that sets authState in context. */
export const defaultAuthState: AuthState = {
  user: undefined,
  isAuthenticated: false,
  isTrialExpired: false,
  trialExpiryDate: undefined,
  isAnnouncementBannerDismissed: false,
  provider: SupportedProviders.NO_USER,
  conductorUser: undefined,
  oidcConfig: undefined,
  authService: undefined,
  fetchingUserInformation: false,
  logOut: noop,
  solveExpireToken: noop,
  setToken: noopSetToken,
  redirectToAuthorizationEndpoint: noopRedirect,
  fetchOidcTokenWithCode: noopFetchOidc,
  dismissAnnouncementBanner: noop,
};
