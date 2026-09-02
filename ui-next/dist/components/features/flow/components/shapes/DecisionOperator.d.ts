import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { SwitchTaskDef } from "types/TaskType";
interface DecisionOperatorProps {
    nodeData: NodeTaskData<SwitchTaskDef>;
    nodeWidth: number;
    portsVisible: boolean;
    isInconsistent: boolean;
    displayDescription?: boolean;
}
declare const DecisionOperator: ({ nodeData, nodeWidth, portsVisible, isInconsistent, displayDescription, }: DecisionOperatorProps) => import("react").JSX.Element;
export default DecisionOperator;
