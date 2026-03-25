/**
 * Auth Provider Selection
 *
 * This module selects the appropriate authentication provider based on configuration.
 * The NoAuthProvider is the default (OSS mode).
 *
 * Enterprise auth providers (Auth0, Okta, OIDC) can be registered via the plugin system.
 * When ACCESS_MANAGEMENT is enabled and a provider is registered, it will be used.
 */
import { ComponentType, ReactNode, useMemo } from "react";
import { pluginRegistry } from "plugins/registry";
import { featureFlags, FEATURES } from "utils/flags";
import { logger } from "utils/logger";
import { NoAuthProvider } from "./NoAuthProvider";

// Define the common interface for all auth providers
interface AuthProviderProps {
  children: ReactNode;
}

type AuthProviderType = ComponentType<AuthProviderProps>;

/**
 * Select the appropriate auth provider based on configuration.
 *
 * If ACCESS_MANAGEMENT is enabled:
 * - Check plugin registry for registered auth providers (enterprise)
 * - Fall back to NoAuthProvider if no matching provider found
 *
 * If ACCESS_MANAGEMENT is disabled:
 * - Use NoAuthProvider (no authentication required)
 */
function selectAuthProvider(): AuthProviderType {
  const accessMgmtEnabled = featureFlags.isEnabled(FEATURES.ACCESS_MANAGEMENT);

  if (!accessMgmtEnabled) {
    return NoAuthProvider;
  }

  const authProviderType = (window as { authConfig?: { type?: string } })
    .authConfig?.type;

  if (!authProviderType) {
    return NoAuthProvider;
  }

  // Check plugin registry for the auth provider (registered by enterprise)
  const pluginAuthProvider = pluginRegistry.getAuthProvider(authProviderType);

  if (pluginAuthProvider) {
    logger.log(`Using ${authProviderType} as Auth Provider (from plugin)`);
    return pluginAuthProvider;
  }

  // No matching provider found
  logger.warn(
    `Auth provider type "${authProviderType}" not found in plugin registry. ` +
      `Falling back to NoAuthProvider.`,
  );
  return NoAuthProvider;
}

/**
 * AuthProvider component that lazily selects the provider at render time.
 * This allows enterprise plugins to register their auth providers before selection.
 */
function AuthProvider({ children }: AuthProviderProps) {
  // Select provider at render time (after plugins are registered)
  const SelectedProvider = useMemo(() => selectAuthProvider(), []);

  return <SelectedProvider>{children}</SelectedProvider>;
}

export { AuthProvider };
