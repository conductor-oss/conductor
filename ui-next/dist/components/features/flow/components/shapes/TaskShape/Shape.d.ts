import { DraggableSyntheticListeners } from "@dnd-kit/core";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { CSSProperties } from "react";
import { CommonTaskDef, TaskType } from "types";
interface ShapeProps<T extends CommonTaskDef = CommonTaskDef> {
    displayDescription?: boolean;
    type: ShapeComponentForTypeParams;
    nodeData: NodeTaskData<T>;
    onToggleTaskMenu: (event: any) => void;
    portsVisible?: boolean;
    nodeWidth?: number;
    nodeHeight?: number;
    isInconsistent: boolean;
    listeners?: DraggableSyntheticListeners;
    style?: CSSProperties;
    handle?: boolean;
    nodeId?: string;
}
export type ShapeComponentForTypeParams = TaskType & "FORK_JOIN_COLLAPSED";
export declare const Shape: import("react").ForwardRefExoticComponent<ShapeProps<CommonTaskDef> & import("react").RefAttributes<HTMLDivElement>>;
export {};
