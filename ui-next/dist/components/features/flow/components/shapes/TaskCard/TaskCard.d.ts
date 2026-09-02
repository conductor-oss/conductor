import { NodeTaskData } from "components/features/flow/nodes/mapper";
declare const TaskCard: ({ nodeData, onClick, isInconsistent, displayDescription, }: {
    nodeData: NodeTaskData;
    onClick: () => void;
    isInconsistent: boolean;
    displayDescription?: boolean;
}) => import("react").JSX.Element;
export default TaskCard;
