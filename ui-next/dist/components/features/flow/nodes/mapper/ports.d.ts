import { NodeData, PortData, PortSide } from "reaflow";
export declare const PORT_SOUTH: PortSide;
export declare const PORT_NORTH: PortSide;
export type DiagramPort = PortData & {
    index?: number;
};
export declare const northPort: (node: NodeData, index?: number, hidden?: boolean) => DiagramPort;
export declare const southPort: (node: NodeData, index?: number) => DiagramPort;
