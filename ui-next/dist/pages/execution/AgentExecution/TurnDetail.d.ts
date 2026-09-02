import { AgentRunData, AgentTurn } from "./types";
interface TurnDetailProps {
    turn: AgentTurn;
    onDrillIn: (agentRun: AgentRunData) => void;
    /** Fetch a collapsed sub-agent's own execution and expand it in place (issue #1452). */
    onExpand?: (agentRun: AgentRunData) => void;
}
export declare function TurnDetail({ turn, onDrillIn, onExpand }: TurnDetailProps): import("react").JSX.Element;
export default TurnDetail;
