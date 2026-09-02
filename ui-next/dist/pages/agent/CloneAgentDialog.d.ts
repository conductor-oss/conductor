import { AgentSummary } from "./types";
interface CloneAgentDialogProps {
    selectedAgent: AgentSummary;
    agentList: AgentSummary[];
    onClose: () => void;
    onSuccess: () => void;
}
/**
 * Mirrors the workflow clone dialog while deploying the source agent definition under a new name.
 */
export default function CloneAgentDialog({ selectedAgent, agentList, onClose, onSuccess, }: CloneAgentDialogProps): import("react").JSX.Element;
export {};
