/**
 * Auth Provider Selection
 *
 * This module selects the appropriate authentication provider based on configuration.
 * The NoAuthProvider is the default (OSS mode).
 *
 * Enterprise auth providers (Auth0, Okta, OIDC) can be registered via the plugin system.
 * When ACCESS_MANAGEMENT is enabled and a provider is registered, it will be used.
 */
import { ReactNode } from "react";
interface AuthProviderProps {
    children: ReactNode;
}
/**
 * AuthProvider component that lazily selects the provider at render time.
 * This allows enterprise plugins to register their auth providers before selection.
 */
declare function AuthProvider({ children }: AuthProviderProps): import("react").JSX.Element;
export { AuthProvider };
