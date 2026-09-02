import { AgentRunData } from "./types";
interface AgentRunViewProps {
    agentRun: AgentRunData;
    onDrillIn: (subAgentRun: AgentRunData) => void;
    /** Fetch a collapsed sub-agent's own execution and expand it in place (issue #1452). */
    onExpand?: (subAgentRun: AgentRunData) => void;
    onBack?: () => void;
    isRoot?: boolean;
}
export declare function AgentRunView({ agentRun, onDrillIn, onExpand, onBack, isRoot, }: AgentRunViewProps): import("react").JSX.Element;
export default AgentRunView;
