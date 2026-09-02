import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { SwitchTaskDef } from "types/TaskType";
declare const SwitchAdd: ({ nodeData }?: {
    nodeData: Partial<NodeTaskData<SwitchTaskDef>>;
}) => import("react").JSX.Element | null;
export default SwitchAdd;
