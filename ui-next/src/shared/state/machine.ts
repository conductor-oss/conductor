/**
 * Minimal auth state machine for OSS mode (no authentication).
 * Full auth implementation has been moved to the enterprise package.
 *
 * This minimal machine only handles the NO_USER_MANAGEMENT state
 * and spawns the sidebar machine for UI state management.
 */
import { createMachine } from "xstate";
import {
  AuthProviderMachineContext,
  AuthProviderStates,
  SupportedProviders,
} from "./types";
import { sidebarMachine } from "shared/PersistableSidebar/state/machine";

export const authProviderMachine = createMachine<AuthProviderMachineContext>({
  id: "authProviderMachine",
  predictableActionArguments: true,
  initial: AuthProviderStates.UNLOGGED,
  context: {
    provider: SupportedProviders.NO_USER,
    error: undefined,
    providerUser: undefined,
    isTrialExpired: false,
    isAnnouncementBannerDismissed: false,
  },
  states: {
    [AuthProviderStates.UNLOGGED]: {
      initial: AuthProviderStates.NO_USER_MANAGEMENT,
      states: {
        [AuthProviderStates.NO_USER_MANAGEMENT]: {
          initial: AuthProviderStates.SIDEBAR_INIT,
          states: {
            [AuthProviderStates.SIDEBAR_INIT]: {
              invoke: {
                src: sidebarMachine,
                id: "sidebarMachine",
              },
            },
          },
        },
      },
    },
  },
});
