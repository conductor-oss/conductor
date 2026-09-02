import { UserSettingsMachineContext } from "./types";
export declare const loadFromLocalStorage: () => Promise<Partial<UserSettingsMachineContext>>;
export declare const saveToLocalStorage: (context: UserSettingsMachineContext) => Promise<{
    firstWorkflowExecuted: boolean;
    dismissedMessages: string[];
    dismissAllMessages: boolean;
}>;
