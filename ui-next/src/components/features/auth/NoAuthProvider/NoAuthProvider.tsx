/**
 * No-auth provider for OSS mode.
 * Provides a minimal auth context (stub authState + sidebar machine service).
 */
import { useInterpret } from "@xstate/react";
import React, { FunctionComponent } from "react";
import { authProviderMachine, SupportedProviders } from "shared/state";
import { AuthContext } from "../context";
import { defaultAuthState } from "../types";

interface NoAuthProviderProps {
  children: React.ReactNode;
}

export const NoAuthProvider: FunctionComponent<NoAuthProviderProps> = ({
  children,
}) => {
  const service = useInterpret(authProviderMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      error: undefined,
      providerUser: undefined,
      provider: SupportedProviders.NO_USER,
      isTrialExpired: false,
      isAnnouncementBannerDismissed: false,
    },
  });

  const authState = React.useMemo(
    () => ({ ...defaultAuthState, authService: service }),
    [service],
  );

  return (
    <AuthContext.Provider value={{ authService: service, authState }}>
      {children}
    </AuthContext.Provider>
  );
};
