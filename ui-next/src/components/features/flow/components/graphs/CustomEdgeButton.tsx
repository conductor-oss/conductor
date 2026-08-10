import { FunctionComponent, useMemo } from "react";
import { BOTTOM_PORT_MARGIN } from "components/features/flow/nodes/mapper/layout";
import PlusIcon from "../shapes/TaskCard/icons/PlusIcon";
import MinusIcon from "../shapes/TaskCard/icons/MinusIcon";
import { PortChildProps } from "reaflow";
import { TaskDef, Crumb } from "types";
import { keyframes, styled } from "@mui/system";
import { isSafari } from "utils/utils";
import { useDroppableNode } from "components/features/flow/dragDrop";
import { DraggedNodeData } from "components/features/flow/state";
import classnames from "classnames";

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

const changeColor = keyframes`
0% {
  background-position: left top, right bottom, left bottom, right   top;
}
100% {
  background-color:  rgba(159,220,170,0.5);
  background-position: left 15px top, right 15px bottom , left bottom 15px , right   top 15px;
}
`;

const pulseAnimation = keyframes`
  0% {
    box-shadow: 0 0 8px 2px rgba(33, 150, 243, 0.5);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 12px 4px rgba(33, 150, 243, 0.7);
    transform: scale(1.02);
  }
  100% {
    box-shadow: 0 0 8px 2px rgba(33, 150, 243, 0.5);
    transform: scale(1);
  }
`;

const ActiveButtonStyle = styled("div")`
  &.active {
    animation: ${pulseAnimation} 1.5s ease-in-out infinite;
    background-color: #e3f2fd;
    border: 2px solid #2196f3;
  }
`;

const DroppablePlace = styled("div")<{
  dropIsDisabled: boolean;
  draggedNodeData?: DraggedNodeData;
}>`
  &.over {
    background-image:
      linear-gradient(90deg, silver 50%, transparent 50%),
      linear-gradient(90deg, silver 50%, transparent 50%),
      linear-gradient(0deg, silver 50%, transparent 50%),
      linear-gradient(0deg, silver 50%, transparent 50%);
    background-repeat: repeat-x, repeat-x, repeat-y, repeat-y;
    background-size:
      15px 2px,
      15px 2px,
      2px 15px,
      2px 15px;
    background-position:
      left top,
      right bottom,
      left bottom,
      right top;
    animation: ${changeColor} 1s infinite linear;
  }

  &.dragging {
  }
  position: absolute;
  top: 10px;
  height: ${(props) =>
    props.dropIsDisabled || props.draggedNodeData == null ? 0 : 80}px;
  width: ${(props) =>
    props.dropIsDisabled || props.draggedNodeData == null
      ? 0
      : props.draggedNodeData.width}px;
`;

export const CustomEdgeButton: FunctionComponent<CustomEdgeButtonProps> = ({
  activeEdgeId,
  x,
  y,
  size = 20,
  hidden = true,
  variant = "ADD",
  onEnter = () => undefined,
  onLeave = () => undefined,
  onClick = () => undefined,
  onDeleteClick = () => undefined,
  data,
  nodeId,
  port,
}) => {
  const {
    droppableResult: { isOver, setNodeRef },
    draggedNodeData,
    dropIsDisabled,
  } = useDroppableNode({
    nodeData: data,
    position: port.side === "NORTH" ? "ABOVE" : "BELOW",
    nodeId,
  });

  const { translateX, translateY, offset } = useMemo(() => {
    const half = size / 2;
    const translateX = x - half;
    const translateY =
      y - (half + (port.side === "SOUTH" ? BOTTOM_PORT_MARGIN : 0));

    const offset = isSafari ? 15 : 0;

    return { translateX, translateY, offset };
  }, [port.side, size, x, y]);

  return hidden ? null : (
    <>
      <g transform={`translate(${translateX}, ${translateY + offset})`}>
        <foreignObject
          style={{
            overflow: "visible",
            cursor: "pointer",
          }}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onClick(event);
          }}
          width={size + 20}
          height={size + 20}
        >
          {variant === "ADD" || variant === "DELETE" ? (
            <ActiveButtonStyle
              className={activeEdgeId === port.id ? "active" : ""}
              style={{
                cursor: "pointer",
                display: "flex",
                width: `${size}px`,
                height: `${size}px`,
                backgroundColor: "#ffffff",
                alignItems: "center",
                justifyContent: "center",
                borderRadius: `${size}px`,
                boxShadow: "0 0 10px rgba(0, 0, 0, 0.5)",
                whiteSpace: "nowrap",
                overflow: "hidden",
              }}
              id={`${variant}-${port.id}`}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                onClick(event);
              }}
              onMouseEnter={onEnter}
              onMouseLeave={onLeave}
            >
              <DroppablePlace
                draggedNodeData={draggedNodeData}
                className={classnames(
                  { over: isOver },
                  { dragging: draggedNodeData != null },
                )}
                dropIsDisabled={dropIsDisabled}
                ref={setNodeRef}
                id="dropping_zone"
              ></DroppablePlace>
              {variant === "ADD" ? (
                <PlusIcon size={14} />
              ) : (
                <MinusIcon size={14} />
              )}
            </ActiveButtonStyle>
          ) : (
            <ActiveButtonStyle
              className={activeEdgeId === port.id ? "active" : ""}
              style={{
                display: "flex",
                width: `${size * 2 + 10}px`,
                height: `${size}px`,
                marginLeft: `-${size / 2 + 5}px`,
                backgroundColor: "#ffffff",
                alignItems: "center",
                justifyContent: "center",
                borderRadius: `${size}px`,
                boxShadow: "0 0 10px rgba(0, 0, 0, 0.5)",
                whiteSpace: "nowrap",
                overflow: "hidden",
              }}
              onMouseEnter={onEnter}
              onMouseLeave={onLeave}
            >
              <div
                style={{
                  cursor: "pointer",
                  height: `${size}px`,
                  width: "100%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
                id={`ADD-${port.id}`}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  onClick(event);
                }}
              >
                <PlusIcon size={14} />
              </div>
              <div
                style={{
                  cursor: "pointer",
                  height: `${size}px`,
                  marginLeft: "-1px",
                  borderLeft: "1px solid rgba(0,0,0,.3)",
                  width: "100%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
                id={`DELETE-${port.id}`}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  onDeleteClick(event);
                }}
              >
                <MinusIcon size={14} />
              </div>
            </ActiveButtonStyle>
          )}
        </foreignObject>
      </g>
    </>
  );
};

export default CustomEdgeButton;
