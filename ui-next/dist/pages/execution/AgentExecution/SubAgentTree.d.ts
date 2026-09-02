import { AgentRunData, AgentStrategy } from "./types";
interface SubAgentTreeProps {
    subAgents: AgentRunData[];
    strategy?: AgentStrategy;
    onDrillIn: (agentRun: AgentRunData) => void;
    /** Fetch a collapsed sub-agent's own execution and expand it in place (issue #1452). */
    onExpand?: (agentRun: AgentRunData) => void;
    depth?: number;
}
export declare function SubAgentTree({ subAgents, strategy, onDrillIn, onExpand, depth, }: SubAgentTreeProps): import("react").JSX.Element | null;
export default SubAgentTree;
