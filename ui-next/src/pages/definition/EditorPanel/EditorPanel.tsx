import { Box } from "@mui/material";
import { useSelector } from "@xstate/react";
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { colors } from "theme/tokens/variables";
import { FEATURES, featureFlags, logger } from "utils";
import { ActorRef, EventObject, State } from "xstate";
import ErrorInspector from "../errorInspector/ErrorInspector";
import {
  DefinitionMachineContext,
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";
import { AssistantPanel, SHRINKED_HEIGHT } from "./AssistantPanel";
import { ConfirmationDialogs } from "./ConfirmationDialogs";
import { EditorTabs } from "./EditorTabs";
import { TabContent } from "./TabContent";
import { useDefinitionMachine } from "./hook";

const agentEnabled = featureFlags.isEnabled(FEATURES.SHOW_AGENT);

// Type helper for ActorRef with children property (exists at runtime but not in types)
type ActorRefWithChildren<T extends EventObject> = ActorRef<T> & {
  children?: {
    get: <E extends EventObject = EventObject>(
      id: string,
    ) => ActorRef<E> | undefined;
  };
};

interface EditorPanelProps {
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
}

const EditorPanel = ({ definitionActor }: EditorPanelProps) => {
  const tabsContainerRef = useRef<HTMLDivElement>(null);
  const [
    {
      handleConfirmReset,
      handleConfirmDelete,
      handleCancelRequest,
      changeTab,
      handleConfirmLastForkRemovalRequest,
      setLeftPanelExpanded,
    },
    {
      isConfirmDelete,
      isConfirmReset,
      openedTab,
      isSaveRequest,
      isConfirmingForkRemoval,
      isRunWorkflow,
    },
  ] = useDefinitionMachine(definitionActor);

  const isReady = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) => state.matches("ready"),
  );

  const isInTaskFormState = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) =>
      state.matches("ready.rightPanel.opened.taskEditor"),
  );

  const isFirstTimeFlowWorkflowDialog = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) =>
      state.hasTag("showCongratsMessage"),
  );

  const isShowRunMessageDialog = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) => state.hasTag("showRunMessage"),
  );

  const isShowDependenciesDialog = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) =>
      state.hasTag("showDependenciesMessage"),
  );

  const isAgentExpanded = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) =>
      state.context.isAgentExpanded ?? false,
  );

  const [tabsHeight, setTabsHeight] = useState(48);
  const [agentPanelHeight, setAgentPanelHeight] = useState<number | null>(null);
  const [isResizing, setIsResizing] = useState(false);
  const isResizingRef = useRef(false);
  const resizeStartRef = useRef<{ x: number; y: number } | null>(null);
  const intendedHeightRef = useRef<number | null>(null);
  const resizeStateRef = useRef<{
    startY: number;
    startHeight: number;
    maxHeight: number;
    containerRect: DOMRect;
    wasCollapsed: boolean;
    hasExpanded: boolean;
  } | null>(null);
  const shouldHandleClickRef = useRef<{ wasCollapsed: boolean } | null>(null);
  const editorPanelContainerRef = useRef<HTMLDivElement>(null);

  // Handle document-level mouse events during resize
  // Note: We use refs (wasCollapsed) instead of XState state (isAgentExpanded) during drag
  // to avoid stale state checks and unnecessary re-renders
  const handleMouseMove = useCallback(
    (moveEvent: MouseEvent) => {
      if (!resizeStartRef.current || !resizeStateRef.current) return;

      // Check if mouse moved significantly (more than 5px) to distinguish drag from click
      const moveDistance = Math.sqrt(
        Math.pow(moveEvent.clientX - resizeStartRef.current.x, 2) +
          Math.pow(moveEvent.clientY - resizeStartRef.current.y, 2),
      );

      if (moveDistance > 5) {
        isResizingRef.current = true;
      }

      if (!isResizingRef.current) return;

      const { startY, startHeight, maxHeight, wasCollapsed } =
        resizeStateRef.current;

      // Calculate how much the mouse moved (positive = moved down)
      const diff = moveEvent.clientY - startY;
      // When dragging down, we increase height (top edge moves down, bottom stays fixed)
      // When dragging up, we decrease height (top edge moves up, bottom stays fixed)
      const newHeight = Math.max(200, Math.min(maxHeight, startHeight - diff));

      // If we started from collapsed state, expand the panel only once
      // Use wasCollapsed from ref (not isAgentExpanded from XState) to avoid stale checks
      if (wasCollapsed && !resizeStateRef.current.hasExpanded) {
        // Mark as expanded to prevent multiple expansion calls
        resizeStateRef.current.hasExpanded = true;
        // Store intended height in ref for immediate access
        intendedHeightRef.current = newHeight;
        // CRITICAL: Set height first, then expand in next tick
        // This ensures the height state is set before the component re-renders with expanded=true
        setAgentPanelHeight(newHeight);
        // Use setTimeout to ensure height state update is processed before expansion
        // This prevents the panel from briefly using calc() value (full height)
        setTimeout(() => {
          definitionActor.send({
            type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
            expanded: true,
          });
        }, 0);
      } else {
        intendedHeightRef.current = newHeight;
        setAgentPanelHeight(newHeight);
      }
    },
    [definitionActor],
  );

  const handleMouseUp = useCallback(() => {
    const wasResizing = isResizingRef.current;
    const wasCollapsed = resizeStateRef.current?.wasCollapsed ?? false;
    isResizingRef.current = false;
    setIsResizing(false);

    // If it was just a click (not a drag), mark it for handleHeaderClick to process
    if (!wasResizing && resizeStateRef.current) {
      shouldHandleClickRef.current = { wasCollapsed };
    } else {
      shouldHandleClickRef.current = null;
    }

    resizeStartRef.current = null;
    resizeStateRef.current = null;
    // Note: If it was a drag (wasResizing = true), the state is already updated
    // via handleMouseMove, so we don't need to do anything here
    // Click handling is done in handleHeaderClick
  }, []);

  useEffect(() => {
    if (!isResizing || !resizeStateRef.current) return;

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);

    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isResizing, handleMouseMove, handleMouseUp]);

  useEffect(() => {
    if (!tabsContainerRef.current) return;

    const updateTabsHeight = () => {
      if (tabsContainerRef.current) {
        const height = tabsContainerRef.current.offsetHeight || 48;
        setTabsHeight((prev) => (prev !== height ? height : prev));
      }
    };

    updateTabsHeight();
    const resizeObserver = new ResizeObserver(updateTabsHeight);
    resizeObserver.observe(tabsContainerRef.current);

    return () => {
      resizeObserver.disconnect();
    };
  }, []);

  const handleNextButtonClick = () => {
    definitionActor.send(
      DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG,
    );
  };

  const handleDismissTutorial = () => {
    definitionActor.send(
      DefinitionMachineEventTypes.DISMISS_IMPORT_SUCCESSFUL_DIALOG,
    );
  };

  logger.debug("Rendering Editor Panel");

  const handleResetConfirmation = (val: boolean) =>
    (val ? handleConfirmReset : handleCancelRequest)();

  const handleDeleteWorkflowVersionConfirmation = (val: boolean) =>
    (val ? handleConfirmDelete : handleCancelRequest)();

  const localCopyActor = (
    definitionActor as ActorRefWithChildren<WorkflowDefinitionEvents>
  ).children?.get("localCopyMachine");

  const saveChangesActor = (
    definitionActor as ActorRefWithChildren<WorkflowDefinitionEvents>
  ).children?.get("saveChangesMachine");

  const errorInspectorActor = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) =>
      state.context.errorInspectorMachine,
  );

  // Calculate effective height - use default when auto-expanding to prevent top positioning
  const effectiveAgentPanelHeight = useMemo(() => {
    if (isAgentExpanded && agentPanelHeight === null) {
      // Auto-expanding without a set height - use default 430px
      return SHRINKED_HEIGHT;
    }
    return agentPanelHeight;
  }, [isAgentExpanded, agentPanelHeight]);

  // Calculate available height for tab content (accounting for error inspector and assistant panel)
  const getTabContentHeight = useCallback(() => {
    const errorInspectorHeight = errorInspectorActor ? 50 : 0;
    let assistantPanelHeight = 0;

    if (agentEnabled) {
      if (isAgentExpanded) {
        assistantPanelHeight = effectiveAgentPanelHeight || 0;
      } else {
        assistantPanelHeight = 50; // Header height when collapsed
      }
    }

    const totalOffset = errorInspectorHeight + assistantPanelHeight;
    return totalOffset > 0 ? `calc(100% - ${totalOffset}px)` : "100%";
  }, [isAgentExpanded, effectiveAgentPanelHeight, errorInspectorActor]);

  const handleHeaderMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      e.stopPropagation();

      if (!editorPanelContainerRef.current) return;
      const containerRect =
        editorPanelContainerRef.current.getBoundingClientRect();
      const containerHeight = containerRect.height;
      const maxHeight =
        containerHeight - tabsHeight - (errorInspectorActor ? 50 : 0);

      // When collapsed, start with collapsed height (50px)
      // When expanded, use current height or maxHeight
      const startHeight = isAgentExpanded ? agentPanelHeight || maxHeight : 50; // Collapsed height

      // If starting from collapsed, calculate initial height based on mouse position
      // This prevents the panel from using calc() value when it expands
      if (!isAgentExpanded && agentPanelHeight === null) {
        // Calculate height based on distance from bottom of container
        // Mouse Y position relative to container bottom
        const mouseYFromBottom = containerRect.bottom - e.clientY;
        // Initial height is the distance from bottom, clamped between min and max
        const initialHeight = Math.max(
          200,
          Math.min(maxHeight, mouseYFromBottom),
        );
        // Set height immediately so it's available when panel expands
        setAgentPanelHeight(initialHeight);
        // Store resize state with collapsed height as start point
        resizeStateRef.current = {
          startY: e.clientY,
          startHeight: 50, // Use 50px (collapsed height) as starting point for drag calculations
          maxHeight,
          containerRect,
          wasCollapsed: true,
          hasExpanded: false,
        };
      } else {
        // Store resize state in refs for the useEffect to use
        resizeStateRef.current = {
          startY: e.clientY,
          startHeight,
          maxHeight,
          containerRect,
          wasCollapsed: !isAgentExpanded,
          hasExpanded: false,
        };
      }

      resizeStartRef.current = { x: e.clientX, y: e.clientY };
      isResizingRef.current = false;
      setIsResizing(true);
    },
    [isAgentExpanded, agentPanelHeight, tabsHeight, errorInspectorActor],
  );

  const handleHeaderClick = useCallback(
    (e: React.MouseEvent) => {
      // Prevent the click from propagating if it was on a button
      if (
        (e.target as HTMLElement).closest("button") ||
        (e.target as HTMLElement).closest("a")
      ) {
        return;
      }

      // Only handle click if it was marked as a click (not a drag) in handleMouseUp
      const clickInfo = shouldHandleClickRef.current;
      if (!clickInfo) {
        return;
      }

      // Clear the ref so we don't handle this click again
      shouldHandleClickRef.current = null;

      if (clickInfo.wasCollapsed) {
        // If collapsed, expand to full height
        if (!editorPanelContainerRef.current) return;
        const containerRect =
          editorPanelContainerRef.current.getBoundingClientRect();
        const containerHeight = containerRect.height;
        const maxHeight =
          containerHeight - tabsHeight - (errorInspectorActor ? 50 : 0);
        // Set height first, then toggle
        setAgentPanelHeight(maxHeight);
        definitionActor.send({
          type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
          expanded: true,
        });
      } else {
        // If expanded, collapse
        definitionActor.send({
          type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
          expanded: false,
        });
      }
    },
    [tabsHeight, errorInspectorActor, definitionActor],
  );

  const handleToggleExpanded = useCallback(() => {
    definitionActor.send({
      type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
    });
  }, [definitionActor]);

  const handleMaximize = useCallback(() => {
    if (!editorPanelContainerRef.current) return;
    const containerRect =
      editorPanelContainerRef.current.getBoundingClientRect();
    const containerHeight = containerRect.height;
    const maxHeight =
      containerHeight - tabsHeight - (errorInspectorActor ? 50 : 0);
    setAgentPanelHeight(maxHeight);
  }, [tabsHeight, errorInspectorActor]);

  const handleHeightChange = useCallback((height: number) => {
    setAgentPanelHeight(height);
  }, []);

  return (
    <>
      {isResizing && (
        <style>{`
          body {
            cursor: row-resize !important;
            user-select: none !important;
          }
        `}</style>
      )}
      <Box
        ref={editorPanelContainerRef}
        id="editor-panel-container"
        sx={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          position: "relative",
          color: (theme) =>
            theme.palette?.mode === "dark" ? colors.gray14 : undefined,
          backgroundColor: (theme) => theme.palette.customBackground.form,
        }}
      >
        <ConfirmationDialogs
          isConfirmReset={isConfirmReset}
          isConfirmDelete={isConfirmDelete}
          isConfirmingForkRemoval={isConfirmingForkRemoval}
          isSaveRequest={isSaveRequest}
          localCopyActor={localCopyActor}
          saveChangesActor={saveChangesActor}
          onResetConfirmation={handleResetConfirmation}
          onDeleteConfirmation={handleDeleteWorkflowVersionConfirmation}
          onCancelRequest={handleCancelRequest}
          onConfirmLastForkRemovalRequest={handleConfirmLastForkRemovalRequest}
        />

        <Box
          sx={{
            height: "100%",
            overflow: "hidden",
            display: "flex",
            flexDirection: "column",
          }}
        >
          <EditorTabs
            openedTab={openedTab}
            definitionActor={definitionActor}
            changeTab={changeTab}
            setLeftPanelExpanded={setLeftPanelExpanded}
            isFirstTimeFlowWorkflowDialog={isFirstTimeFlowWorkflowDialog}
            isShowRunMessageDialog={isShowRunMessageDialog}
            isShowDependenciesDialog={isShowDependenciesDialog}
            handleNextButtonClick={handleNextButtonClick}
            handleDismissTutorial={handleDismissTutorial}
            tabsContainerRef={tabsContainerRef}
          />

          <TabContent
            openedTab={openedTab}
            isReady={isReady}
            isRunWorkflow={isRunWorkflow}
            isInTaskFormState={isInTaskFormState}
            definitionActor={definitionActor}
            getTabContentHeight={getTabContentHeight}
          />

          {errorInspectorActor && (
            <ErrorInspector errorInspectorActor={errorInspectorActor} />
          )}

          {agentEnabled && (
            <AssistantPanel
              isAgentExpanded={isAgentExpanded}
              agentPanelHeight={effectiveAgentPanelHeight}
              tabsHeight={tabsHeight}
              errorInspectorActor={errorInspectorActor}
              definitionActor={definitionActor}
              onHeaderMouseDown={handleHeaderMouseDown}
              onHeaderClick={handleHeaderClick}
              onToggleExpanded={handleToggleExpanded}
              onMaximize={handleMaximize}
              onHeightChange={handleHeightChange}
              isResizing={isResizing}
            />
          )}
        </Box>
      </Box>
    </>
  );
};

export default EditorPanel;
