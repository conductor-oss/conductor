import { WorkflowExecution } from "types/Execution";
import "components/features/flow/ReaflowOverrides.scss";
export declare function AgentDefinitionDiagram({ agentDef, }: {
    agentDef: Record<string, unknown>;
}): import("react").JSX.Element;
interface AgentDefinitionViewProps {
    execution: WorkflowExecution;
}
export declare function AgentDefinitionView({ execution }: AgentDefinitionViewProps): import("react").JSX.Element;
export default AgentDefinitionView;
