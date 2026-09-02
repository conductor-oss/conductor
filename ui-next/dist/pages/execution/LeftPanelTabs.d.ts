import { WorkflowExecution } from "types/Execution";
import { ExecutionTabs } from "./state/types";
export interface LeftPanelTabsProps {
    execution: WorkflowExecution;
    openedTab: ExecutionTabs;
    onChangeExecutionTab: (tab: ExecutionTabs) => void;
    onToggleAssistant?: () => void;
    isAssistantOpen?: boolean;
}
export default function LeftPanelTabs({ execution, openedTab, onChangeExecutionTab, onToggleAssistant, isAssistantOpen, }: LeftPanelTabsProps): import("react").JSX.Element;
