import { AgentRunData } from "./types";
import { DetailNodeData } from "./AgentDetailPanel";
import "components/features/flow/ReaflowOverrides.scss";
interface AgentExecutionDiagramProps {
    agentRun: AgentRunData;
    activeTurn: string;
    onSelectTurn: (id: string) => void;
    selectedId: string | null;
    onNodeSelect: (id: string | null, node: DetailNodeData | null) => void;
    onDrillIn?: (sub: AgentRunData) => void;
    /** Fetch a collapsed sub-agent's own execution and expand it in place (issue #1452). */
    onExpand?: (sub: AgentRunData) => void;
    onBack?: () => void;
}
export declare function AgentExecutionDiagram({ agentRun, activeTurn, onSelectTurn, selectedId, onNodeSelect, onDrillIn, onExpand, onBack, }: AgentExecutionDiagramProps): import("react").JSX.Element;
export default AgentExecutionDiagram;
