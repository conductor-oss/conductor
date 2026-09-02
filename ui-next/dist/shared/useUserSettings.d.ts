import { UserSettingsMachineContext } from "./state/userSettingsMachine";
export declare const useUserSettings: () => {
    userSettings: UserSettingsMachineContext;
    isShowingConfetti: boolean;
    send: (event: import("xstate").SingleOrArray<import("xstate").Event<import("./state/userSettingsMachine").UserSettingsEvents>> | import("xstate").SCXML.Event<import("./state/userSettingsMachine").UserSettingsEvents>, payload?: import("xstate").EventData) => import("xstate").State<UserSettingsMachineContext, import("./state/userSettingsMachine").UserSettingsEvents, any, {
        value: any;
        context: UserSettingsMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./state/userSettingsMachine").UserSettingsEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    service: import("xstate").Interpreter<UserSettingsMachineContext, any, import("./state/userSettingsMachine").UserSettingsEvents, {
        value: any;
        context: UserSettingsMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./state/userSettingsMachine").UserSettingsEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
};
