import { NodeData, PortData, PortSide } from "reaflow";
export const PORT_SOUTH = "SOUTH" as PortSide;
export const PORT_NORTH = "NORTH" as PortSide;

export type DiagramPort = PortData & { index?: number };

export const northPort = (
  node: NodeData,
  index?: number,
  hidden = false,
): DiagramPort => {
  const id = `${node.id}-north-port`;
  return {
    id,
    width: 2,
    height: 2,
    side: PORT_NORTH,
    disabled: true,
    hidden,
    index,
  };
};

export const southPort = (node: NodeData, index?: number): DiagramPort => {
  const id = `${node.id}-south-port`;
  return {
    id,
    width: 2,
    height: 2,
    side: PORT_SOUTH,
    disabled: true,
    index,
  };
};
