import { tryToJson } from "utils/utils";
import { logger } from "utils/logger";
import { UserSettingsMachineContext } from "./types";

const USER_SETTINGS_STORAGE_KEY = "userSettings";

export const loadFromLocalStorage = async (): Promise<
  Partial<UserSettingsMachineContext>
> => {
  try {
    const savedSettings = window.localStorage.getItem(
      USER_SETTINGS_STORAGE_KEY,
    );
    if (savedSettings) {
      const parsedSettings =
        tryToJson<Partial<UserSettingsMachineContext>>(savedSettings);
      if (parsedSettings !== undefined) {
        return parsedSettings;
      } else {
        window.localStorage.removeItem(USER_SETTINGS_STORAGE_KEY);
        logger.log("Couldn't parse user settings, removing from localStorage.");
      }
    }
  } catch (error) {
    logger.error("Error loading user settings from localStorage", error);
  }
  return {};
};

export const saveToLocalStorage = async (
  context: UserSettingsMachineContext,
) => {
  try {
    const settingsToSave = {
      firstWorkflowExecuted: context.firstWorkflowExecuted,
      dismissedMessages: context.dismissedMessages,
      dismissAllMessages: context.dismissAllMessages,
    };
    window.localStorage.setItem(
      USER_SETTINGS_STORAGE_KEY,
      JSON.stringify(settingsToSave),
    );
    return settingsToSave;
  } catch (error) {
    logger.error("Error saving user settings to localStorage", error);
    throw error;
  }
};
