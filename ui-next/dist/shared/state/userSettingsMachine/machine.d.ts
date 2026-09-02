import { UserSettingsMachineContext, UserSettingsEvents } from "./types";
export declare const userSettingsMachine: import("xstate").StateMachine<UserSettingsMachineContext, any, UserSettingsEvents, {
    value: any;
    context: UserSettingsMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, UserSettingsEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
