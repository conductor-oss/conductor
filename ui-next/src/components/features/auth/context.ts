/**
 * Auth context for providing the auth state machine service and/or full auth state.
 * Used by useAuth() to access the current auth state.
 *
 * In OSS mode, NoAuthProvider sets both authService and authState (stub).
 * Enterprise (e.g. orkes) can provide authState so conductor-ui's useAuth() and
 * shared components (e.g. UserInfo) work without a custom footer.
 */
import { createContext } from "react";
import { ActorRef } from "xstate";
import { AuthProviderMachineEvents } from "shared/state/types";
import type { AuthState } from "./types";

interface AuthContextProps {
  authService?: ActorRef<AuthProviderMachineEvents>;
  /** When set (e.g. by enterprise), useAuth() returns this; otherwise stub + authService. */
  authState?: AuthState;
}

export const AuthContext = createContext<AuthContextProps>({
  authService: undefined,
  authState: undefined,
});
