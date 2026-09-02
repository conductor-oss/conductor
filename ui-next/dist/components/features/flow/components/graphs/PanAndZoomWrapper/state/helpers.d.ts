import { ElkRoot, NodeData } from "reaflow";
import { PanAndZoomMachineContext, PositionProps, SizeProps } from "./types";
type CenterParams = {
    layout?: ElkRoot;
    viewportOffsetWidth: number;
    viewportOffsetHeight: number;
    zoom: number;
};
type SizeAndPosition = PositionProps & {
    width: number;
    height: number;
};
export declare const PADDING_TOP = 65;
export declare const centerCanvasToNodePosition: (containerSize: SizeProps, node: SizeAndPosition, scale: number) => {
    x: number;
    y: number;
};
export type NodeWithSizeAndPosition = NodeData & SizeAndPosition & {
    children?: NodeWithSizeAndPosition[];
};
export declare const centerInBestLayoutNode: (children: NodeWithSizeAndPosition[], containerSize: SizeProps, scale: number, selectedNode?: NodeWithSizeAndPosition) => SizeAndPosition | undefined;
export declare const initialZoomCenter: ({ layout, viewportOffsetWidth, viewportOffsetHeight, zoom, }: CenterParams) => Partial<PanAndZoomMachineContext>;
export declare const applyZoomToCursor: (currentPosition: {
    x: number;
    y: number;
}, cursorPosition: {
    x: number;
    y: number;
}, oldZoom: number, newZoom: number) => {
    position: {
        x: number;
        y: number;
    };
    zoom: number;
};
export declare const calculateZoomPosition: ({ context, newZoom, }: {
    context: PanAndZoomMachineContext;
    newZoom: number;
}) => {
    zoom: number;
    position: {
        x: number;
        y: number;
    };
};
export {};
