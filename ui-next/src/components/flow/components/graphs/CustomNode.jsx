import { Node } from "reaflow";
import { TaskShape } from "../shapes/TaskShape";
import CustomPort from "./CustomPort";
import _first from "lodash/first";

import { isSafari } from "utils/utils";

export const CustomNode = (nodeProps) => {
  const {
    operationContext,
    onClick,
    onToggleTaskMenu,
    onDeleteBranch,
    properties: nodeProperties,
    isInconsistent,
    displayDescription = false,
  } = nodeProps;
  const portsHidden = _first(nodeProperties?.ports || [])?.hidden === true;

  return (
    <Node
      {...nodeProps}
      onClick={() => null}
      label={<></>}
      style={{ stroke: "none", fill: "none" }}
      port={
        <CustomPort
          operationContext={operationContext}
          nodeProperties={nodeProperties}
          onClick={(e, port) => {
            onToggleTaskMenu(e, {
              id: port.id,
              port,
              node: nodeProperties,
            });
          }}
          onDeleteClick={(e, port) => {
            onDeleteBranch(e, {
              id: port.id,
              port,
              node: nodeProperties,
            });
          }}
        />
      }
    >
      {(event) => {
        return (
          <g>
            <foreignObject
              onClick={(e) => {
                onClick(e, nodeProperties);
              }}
              style={{
                overflow: "visible",
              }}
              height={event.height}
              width={event.width}
            >
              <div
                style={{
                  width: event.width,
                  height: event.height,
                  borderRadius: "10px",
                }}
              >
                <div
                  style={{
                    width: "100%",
                    height: "100%",
                    position: isSafari ? "relative" : "initial",
                    top: event.y,
                    left: event.x + 25,
                  }}
                >
                  <TaskShape
                    displayDescription={displayDescription}
                    portsVisible={!portsHidden}
                    nodeData={event.node.data}
                    width={event.width}
                    height={event.height}
                    onToggleTaskMenu={onToggleTaskMenu}
                    isInconsistent={isInconsistent}
                    nodeId={event.node.id}
                  />
                </div>
              </div>
            </foreignObject>
          </g>
        );
      }}
    </Node>
  );
};
