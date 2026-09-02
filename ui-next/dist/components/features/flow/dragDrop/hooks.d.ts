import { useDraggable } from "@dnd-kit/core";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { DropPosition } from "components/features/flow/state";
interface DragDropNodeProps {
    nodeData: NodeTaskData & {
        selected?: boolean;
    };
    width?: number;
    height?: number;
    nodeId: string;
}
export declare const useDraggableNode: ({ nodeData, width, height, nodeId, }: DragDropNodeProps) => {
    draggableResult: ReturnType<typeof useDraggable>;
    dragIsDisabled: boolean;
};
export declare const useDroppableNode: ({ nodeData, position, nodeId, }: DragDropNodeProps & DropPosition) => {
    droppableResult: {
        active: import("@dnd-kit/core").Active | null;
        rect: import("react").MutableRefObject<import("@dnd-kit/core").ClientRect | null>;
        isOver: boolean;
        node: import("react").MutableRefObject<HTMLElement | null>;
        over: import("@dnd-kit/core").Over | null;
        setNodeRef: (element: HTMLElement | null) => void;
    };
    draggedNodeData: import("components/features/flow/state").DraggedNodeData | undefined;
    dropIsDisabled: boolean;
};
export {};
