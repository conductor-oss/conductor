import { AgentRunData, ExecutionMetrics } from "./types";
interface AgentExecutionHeaderProps {
    metrics: ExecutionMetrics;
    rootRun: AgentRunData;
}
export declare function AgentExecutionHeader({ metrics, rootRun, }: AgentExecutionHeaderProps): import("react").JSX.Element;
export default AgentExecutionHeader;
