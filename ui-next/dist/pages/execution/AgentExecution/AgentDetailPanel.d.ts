import { AgentEvent, AgentRunData, AgentStatus, AgentStrategy } from "./types";
export interface DetailNodeData {
    kind: "llm" | "tool" | "handoff" | "subagent" | "output" | "error" | "start" | "group";
    label: string;
    status: AgentStatus;
    event?: AgentEvent;
    subAgentRun?: AgentRunData;
    /** For group kind */
    groupType?: "agents" | "tools";
    groupAgents?: AgentRunData[];
    groupEvents?: AgentEvent[];
    strategy?: AgentStrategy;
}
interface AgentDetailPanelProps {
    node: DetailNodeData;
    onClose: () => void;
    onDrillIn?: (run: AgentRunData) => void;
}
export declare function AgentDetailPanel({ node, onClose, onDrillIn, }: AgentDetailPanelProps): import("react").JSX.Element;
export default AgentDetailPanel;
