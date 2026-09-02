import { ActorRef } from "xstate";
import { AuthProviderMachineEvents } from "shared/state/types";
import type { AuthState } from "./types";
interface AuthContextProps {
    authService?: ActorRef<AuthProviderMachineEvents>;
    /** When set (e.g. by enterprise), useAuth() returns this; otherwise stub + authService. */
    authState?: AuthState;
}
export declare const AuthContext: import("react").Context<AuthContextProps>;
export {};
