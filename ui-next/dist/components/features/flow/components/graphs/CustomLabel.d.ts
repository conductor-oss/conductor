import { FunctionComponent } from "react";
import { EdgeData, LabelProps, NodeData, EdgeChildProps } from "reaflow";
type SelectEdgePram = {
    edge: EdgeData;
};
interface CustomLabelProps extends LabelProps {
    selectEdge: (edgeData: SelectEdgePram) => void;
    nodes: NodeData[];
    edgeChildProps: EdgeChildProps;
}
export declare const CustomLabel: FunctionComponent<Partial<CustomLabelProps>>;
export {};
