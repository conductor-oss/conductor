import { FunctionComponent, ReactNode } from "react";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
interface TaskShapeProps {
    onToggleTaskMenu: (event: any) => void;
    nodeData: NodeTaskData & {
        selected?: boolean;
    };
    isInconsistent: boolean;
    width?: number;
    height?: number;
    portsVisible?: boolean;
    children?: ReactNode;
    nodeId: string;
    displayDescription?: boolean;
}
export declare const TaskShape: FunctionComponent<TaskShapeProps>;
export {};
