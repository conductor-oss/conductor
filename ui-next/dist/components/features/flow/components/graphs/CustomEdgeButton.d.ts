import { FunctionComponent } from "react";
import { PortChildProps } from "reaflow";
import { TaskDef, Crumb } from "types";
type DataType = {
    task: TaskDef;
    crumbs: Crumb[];
};
type CustomEdgeButtonProps = PortChildProps & {
    size: number;
    hidden: boolean;
    variant: "ADD" | "DELETE" | "ADD_DELETE";
    onDeleteClick: (event: any) => void;
    onClick: (event: any) => void;
    onEnter: (event: any) => void;
    onLeave: (event: any) => void;
    data: DataType;
    nodeId: string;
    activeEdgeId?: string;
};
export declare const CustomEdgeButton: FunctionComponent<CustomEdgeButtonProps>;
export default CustomEdgeButton;
