import { State } from "xstate";
import { AuthProviderMachineContext, AuthProviderStates } from "./types";
export declare const isAuthenticated: (state: State<AuthProviderMachineContext>) => state is State<AuthProviderMachineContext, import("xstate").EventObject, any, {
    value: any;
    context: AuthProviderMachineContext;
}, import("xstate").TypegenDisabled> & {
    value: AuthProviderStates;
};
export declare const noUserManagement: (state: State<AuthProviderMachineContext>) => state is State<AuthProviderMachineContext, import("xstate").EventObject, any, {
    value: any;
    context: AuthProviderMachineContext;
}, import("xstate").TypegenDisabled> & {
    value: AuthProviderStates[];
};
export declare const getUserPersistableProfileActor: (state: State<AuthProviderMachineContext>) => import("xstate").ActorRef<any, any>;
export declare const isSidebarInitialized: (state: State<AuthProviderMachineContext>) => state is State<AuthProviderMachineContext, import("xstate").EventObject, any, {
    value: any;
    context: AuthProviderMachineContext;
}, import("xstate").TypegenDisabled> & {
    value: AuthProviderStates[];
};
