import { useSelector } from "@xstate/react";
import { useContext } from "react";
import { UserSettingsContext } from "./UserSettingsContext";
import { UserSettingsMachineContext } from "./state/userSettingsMachine";

export const useUserSettings = () => {
  const context = useContext(UserSettingsContext);
  if (!context) {
    throw new Error("useUserSettings must be used within UserSettingsProvider");
  }

  const { userSettingsService } = context;

  const userSettings = useSelector(
    userSettingsService,
    (state) => state.context as UserSettingsMachineContext,
  );

  const isShowingConfetti = useSelector(
    userSettingsService,
    (state) => state.context.isShowingConfettiThisSession,
  );

  return {
    userSettings,
    isShowingConfetti,
    send: userSettingsService.send,
    service: userSettingsService,
  };
};
