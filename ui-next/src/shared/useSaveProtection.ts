import { useSelector } from "@xstate/react";
import { useEffect, useMemo, useRef } from "react";
import { ActorRef, AnyEventObject, EventObject } from "xstate";

export interface SaveProtectionConfig<
  TContext,
  TEvent extends EventObject = AnyEventObject,
> {
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
  detectSaveSuccessFromEvent?: (
    eventType: string,
    state: {
      context: TContext;
      event: TEvent;
      matches: (state: string | string[]) => boolean;
    },
  ) => boolean | undefined;

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
export function useSaveProtection<
  TContext,
  TEvent extends EventObject = AnyEventObject,
>(config: SaveProtectionConfig<TContext, TEvent>): SaveProtectionResult {
  const {
    actor,
    noFormChanges,
    isSaveInProgress: checkIsSaveInProgress,
    hasErrors: checkHasErrors,
    detectSaveSuccessFromEvent,
    detectSaveSuccessFromContext,
    handleSaveAction,
  } = config;

  // Track the last save result using a ref to persist across renders
  const lastSaveResultRef = useRef<boolean | undefined>(undefined);

  // Track previous context for detecting successful saves
  const prevContextRef = useRef<TContext | null>(null);
  const prevIsSavingRef = useRef(false);

  // Get current context
  const currentContext = useSelector(
    actor,
    (state) => state.context as TContext,
  );

  // Get current saving state
  const isSaving = useSelector(actor, (state) =>
    checkIsSaveInProgress({
      context: state.context as TContext,
      event: state.event as TEvent,
      matches: (statePath) => state.matches(statePath),
      hasTag: (tag) => state.hasTag?.(tag) ?? false,
    }),
  );

  // Detect successful save from context changes (e.g., originTaskDefinition updated)
  useEffect(() => {
    if (detectSaveSuccessFromContext) {
      const wasSaving = prevIsSavingRef.current;
      const isCurrentlySaving = isSaving;

      // Initialize the previous context on first render
      if (prevContextRef.current === null) {
        prevContextRef.current = currentContext;
      }

      // Capture context before we start saving (when transitioning from not saving to saving)
      if (!wasSaving && isCurrentlySaving) {
        // We're about to start saving, capture the current context as the "before" state
        prevContextRef.current = currentContext;
      }

      // If we were saving and now we're not, check if save was successful
      if (wasSaving && !isCurrentlySaving && prevContextRef.current) {
        // Check if context was updated (indicates successful save)
        const success = detectSaveSuccessFromContext({
          currentContext,
          previousContext: prevContextRef.current,
          wasSaving,
          isSaving: isCurrentlySaving,
        });

        if (success) {
          lastSaveResultRef.current = true;
        }
      }

      prevIsSavingRef.current = isSaving;
    } else {
      // If not using context detection, still track saving state
      prevIsSavingRef.current = isSaving;
    }
  }, [isSaving, currentContext, detectSaveSuccessFromContext, actor]);

  // Check for successful save based on event types
  const successfulSave = useSelector(actor, (state) => {
    const eventType = state.event.type;

    // Check for cancel/success events if configured
    if (detectSaveSuccessFromEvent) {
      const result = detectSaveSuccessFromEvent(eventType, {
        context: state.context as TContext,
        event: state.event as TEvent,
        matches: (statePath) => state.matches(statePath),
      });

      if (result !== undefined) {
        lastSaveResultRef.current = result;
        return result;
      }
    }

    // If we detected a successful save via context, verify there's no error
    if (lastSaveResultRef.current === true) {
      const hasError = checkHasErrors({
        context: state.context as TContext,
        event: state.event as TEvent,
        matches: (statePath) => state.matches(statePath),
      });

      if (hasError) {
        // If there's an error, it wasn't successful
        lastSaveResultRef.current = false;
        return false;
      }
    }

    // Return the last known result
    return lastSaveResultRef.current;
  });

  // Check for validation errors
  const hasErrors = useSelector(actor, (state) =>
    checkHasErrors({
      context: state.context as TContext,
      event: state.event as TEvent,
      matches: (statePath) => state.matches(statePath),
    }),
  );

  // Check if save is in progress
  const isSaveInProgress = useSelector(actor, (state) =>
    checkIsSaveInProgress({
      context: state.context as TContext,
      event: state.event as TEvent,
      matches: (statePath) => state.matches(statePath),
      hasTag: (tag) => state.hasTag?.(tag) ?? false,
    }),
  );

  // Determine if we should show the prompt
  const showPrompt = useMemo(
    () => !noFormChanges && !isSaveInProgress,
    [isSaveInProgress, noFormChanges],
  );

  // Handle save action
  const handleSave = () => {
    lastSaveResultRef.current = undefined;
    handleSaveAction(actor);
  };

  return {
    showPrompt,
    successfulSave,
    hasErrors,
    handleSave,
  };
}
