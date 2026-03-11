import { createContext } from "react";
import { InterpreterFrom } from "xstate";
import { userSettingsMachine } from "./state/userSettingsMachine";

export interface UserSettingsContextValue {
  userSettingsService: InterpreterFrom<typeof userSettingsMachine>;
}

export const UserSettingsContext = createContext<
  UserSettingsContextValue | undefined
>(undefined);
