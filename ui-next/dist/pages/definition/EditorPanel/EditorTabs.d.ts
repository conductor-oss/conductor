import React from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
interface EditorTabsProps {
    openedTab: number;
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
    changeTab: (tab: number) => void;
    setLeftPanelExpanded: () => void;
    isFirstTimeFlowWorkflowDialog: boolean;
    isShowRunMessageDialog: boolean;
    isShowDependenciesDialog: boolean;
    handleNextButtonClick: () => void;
    handleDismissTutorial: () => void;
    tabsContainerRef: React.RefObject<HTMLDivElement | null>;
}
export declare const EditorTabs: ({ openedTab, definitionActor, changeTab, setLeftPanelExpanded, isFirstTimeFlowWorkflowDialog, isShowRunMessageDialog, isShowDependenciesDialog, handleNextButtonClick, handleDismissTutorial, tabsContainerRef, }: EditorTabsProps) => React.JSX.Element;
export {};
