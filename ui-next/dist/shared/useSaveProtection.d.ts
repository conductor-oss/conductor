import { ActorRef, AnyEventObject, EventObject } from "xstate";
export interface SaveProtectionConfig<TContext, TEvent extends EventObject = AnyEventObject> {
    /**
     * The actor/machine to monitor for save events and state
     */
    actor: ActorRef<TEvent>;
    /**
     * Whether there are form changes (false means there are changes)
     */
    noFormChanges: boolean;
    /**
     * Check if save is in progress. Should return true when saving.
     */
    isSaveInProgress: (state: {
        context: TContext;
        event: TEvent;
        matches: (state: string | string[]) => boolean;
        hasTag?: (tag: string) => boolean;
    }) => boolean;
    /**
     * Check for validation errors. Should return true if there are errors.
     */
    hasErrors: (state: {
        context: TContext;
        event: TEvent;
        matches: (state: string | string[]) => boolean;
    }) => boolean;
    /**
     * Optional: Function to detect successful save based on event type.
     * Should return true for successful save, false for cancelled, undefined if unknown.
     */
    detectSaveSuccessFromEvent?: (eventType: string, state: {
        context: TContext;
        event: TEvent;
        matches: (state: string | string[]) => boolean;
    }) => boolean | undefined;
    /**
     * Optional: Function to detect successful save based on context changes.
     * This is useful for cases where success is detected by comparing previous
     * and current context values (e.g., originTaskDefinition changes).
     */
    detectSaveSuccessFromContext?: (options: {
        currentContext: TContext;
        previousContext: TContext | null;
        wasSaving: boolean;
        isSaving: boolean;
    }) => boolean;
    /**
     * Function to trigger the save action
     */
    handleSaveAction: (actor: ActorRef<TEvent>) => void;
}
export interface SaveProtectionResult {
    /**
     * Whether to show the save prompt (true means block navigation)
     */
    showPrompt: boolean;
    /**
     * Whether the last save was successful (undefined if no save attempted yet)
     */
    successfulSave: boolean | undefined;
    /**
     * Whether there are validation errors
     */
    hasErrors: boolean;
    /**
     * Function to trigger the save
     */
    handleSave: () => void;
}
/**
 * Generic hook for save protection logic that can be reused across different
 * save scenarios (workflows, tasks, etc.)
 */
export declare function useSaveProtection<TContext, TEvent extends EventObject = AnyEventObject>(config: SaveProtectionConfig<TContext, TEvent>): SaveProtectionResult;
