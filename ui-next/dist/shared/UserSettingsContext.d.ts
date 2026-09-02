import { InterpreterFrom } from "xstate";
import { userSettingsMachine } from "./state/userSettingsMachine";
export interface UserSettingsContextValue {
    userSettingsService: InterpreterFrom<typeof userSettingsMachine>;
}
export declare const UserSettingsContext: import("react").Context<UserSettingsContextValue | undefined>;
