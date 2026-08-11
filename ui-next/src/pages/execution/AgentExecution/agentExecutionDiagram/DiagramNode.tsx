import { Node } from "reaflow";
import { DiagramNodeData } from "../buildAgentExecutionDiagram";
import { NodeCard } from "./NodeCard";

export const DiagramNode = (nodeProps: any) => {
  const {
    selectedId,
    onSelect,
    onDrillIn,
    onExpand,
    onBack,
    onToggleGroup,
    properties,
  } = nodeProps;
  const data: DiagramNodeData = properties?.data;
  return (
    <Node
      {...nodeProps}
      onClick={() => null}
      label={<></>}
      style={{ stroke: "none", fill: "none" }}
    >
      {(ev: any) => (
        <g>
          <foreignObject
            width={ev.width}
            height={ev.height}
            style={{ overflow: "visible" }}
          >
            <NodeCard
              data={data}
              width={ev.width}
              height={ev.height}
              selected={selectedId === properties?.id}
              onSelect={() => onSelect(properties?.id)}
              onDrillIn={onDrillIn}
              onExpand={onExpand}
              onBack={onBack}
              onToggleGroup={
                onToggleGroup ? () => onToggleGroup(properties?.id) : undefined
              }
            />
          </foreignObject>
        </g>
      )}
    </Node>
  );
};
