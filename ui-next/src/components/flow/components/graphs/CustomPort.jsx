import { Port } from "reaflow";
import CustomEdgeButton from "./CustomEdgeButton";
import { TERMINAL_END_NAME } from "components/flow/nodes/mapper/constants";

const CustomPort = ({
  operationContext,
  onClick,
  onDeleteClick,
  nodeProperties,
  ...restProps
}) => {
  const portVariant =
    ["SWITCH", "FORK_JOIN"].includes(nodeProperties.data.task.type) &&
    restProps.properties.side === "SOUTH"
      ? "ADD_DELETE"
      : "ADD";

  const isEndTerminal = nodeProperties.data.task.name === TERMINAL_END_NAME;

  return (
    <Port {...restProps} style={{ display: "none" }}>
      {(event) => {
        return (
          !isEndTerminal && (
            <CustomEdgeButton
              {...event}
              variant={portVariant}
              hidden={false}
              onClick={(clkEvent) => onClick(clkEvent, restProps)}
              onDeleteClick={(clkEvent) => onDeleteClick(clkEvent, restProps)}
              data={nodeProperties.data}
              nodeId={nodeProperties.id}
              activeEdgeId={operationContext?.port?.properties?.id}
            />
          )
        );
      }}
    </Port>
  );
};

export default CustomPort;
