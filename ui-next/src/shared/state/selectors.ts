import { State } from "xstate";
import { AuthProviderMachineContext, AuthProviderStates } from "./types";

export const isAuthenticated = (state: State<AuthProviderMachineContext>) =>
  state.matches(AuthProviderStates.LOGGED_USER);

export const noUserManagement = (state: State<AuthProviderMachineContext>) =>
  state.matches([
    AuthProviderStates.UNLOGGED,
    AuthProviderStates.NO_USER_MANAGEMENT,
  ]);

export const getUserPersistableProfileActor = (
  state: State<AuthProviderMachineContext>,
) => state.children["userPersistableProfileMachine"];

export const isSidebarInitialized = (
  state: State<AuthProviderMachineContext>,
) =>
  state.matches([
    AuthProviderStates.LOGGED_USER,
    AuthProviderStates.SIDEBAR_INIT,
  ]) ||
  state.matches([
    AuthProviderStates.UNLOGGED,
    AuthProviderStates.NO_USER_MANAGEMENT,
  ]);
