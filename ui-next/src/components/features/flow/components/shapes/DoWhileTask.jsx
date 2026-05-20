import { IconButton, keyframes, styled } from "@mui/material";
import { Plus, Repeat } from "@phosphor-icons/react";
import classnames from "classnames";
import { useDroppableNode } from "components/features/flow/dragDrop/hooks";
import _isEmpty from "lodash/isEmpty";
import { ADD_TASK_IN_DO_WHILE } from "pages/definition/state/taskModifier/constants";
import { useMemo } from "react";
import CardAttemptsBadge from "./TaskCard/CardAttemptsBadge";
import CardLabel from "./TaskCard/CardLabel";
import CardStatusBadge from "./TaskCard/CardStatusBadge";
import DeleteButton from "./TaskCard/DeleteButton";
import { getCardVariant } from "./styles";

const changeColor = keyframes`
0% {
  background-position: left top, right bottom, left bottom, right   top;
}
100% {
  background-color:  rgba(159,220,170,0.5);
  background-position: left 15px top, right 15px bottom , left bottom 15px , right   top 15px;
}
`;

const DroppablePlace = styled("div")`
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
    height: 340px;
  }

  &.dragging {
  }
  position: absolute;
  top: 60px;
  height: 340px;
  width: ${(props) =>
    props.dropIsDisabled || props.draggedNodeData == null ? 0 : "350"}px;
`;

const DoWhileTask = ({
  nodeData,
  onToggleTaskMenu,
  isInconsistent,
  nodeId = "",
  displayDescription = false,
}) => {
  const { task } = nodeData;
  const { type } = task;
  const {
    droppableResult: { isOver, setNodeRef },
    draggedNodeData,
    dropIsDisabled,
  } = useDroppableNode({
    nodeData: nodeData,
    position: "ADD_TASK_IN_DO_WHILE",
    nodeId: nodeId + "_drag_to_dowhile",
  });

  const maybeAddButton = useMemo(
    () =>
      task.executionData == null && _isEmpty(task.loopOver) ? (
        <>
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
          <IconButton
            onClick={(event) => {
              onToggleTaskMenu(event, {
                id: `${task.taskReferenceName}_inner_do_while`,
                port: undefined,
                node: {
                  data: { ...nodeData, action: ADD_TASK_IN_DO_WHILE },
                },
              });
            }}
            style={{
              backgroundColor: "#ffffff",
            }}
          >
            <Plus />
          </IconButton>
        </>
      ) : null,
    [
      task,
      nodeData,
      onToggleTaskMenu,
      setNodeRef,
      draggedNodeData,
      dropIsDisabled,
      isOver,
    ],
  );

  return (
    <div
      style={{
        cursor: isInconsistent ? "not-allowed" : "pointer",
        display: "flex",
        width: "100%",
        minWidth: "570px",
        padding: "20px",
        border: "1px dashed black",
        borderRadius: "20px",
        textAlign: "center",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        ...getCardVariant(type, nodeData.status, nodeData.selected),
        background: "rgba(0,50,100,.5)",
      }}
    >
      {/* Execution */}
      <CardStatusBadge status={nodeData.status} />
      {nodeData?.attempts > 1 ? (
        <CardAttemptsBadge attempts={nodeData.attempts} />
      ) : null}

      {/* Definition */}
      <DeleteButton maybeHideData={nodeData} />

      <div
        style={{
          position: "absolute",
          top: "10px",
          left: "10px",
          display: "flex",
          alignItems: "center",
          width: "93%",
        }}
      >
        <div style={{ height: "24px", width: "24px" }}>
          <Repeat size={24} color="white" />
        </div>
        <div
          style={{
            paddingLeft: "6px",
            marginTop: "-2px",
            color: "white",
            textShadow: "0 1px 2px black",
            display: "block",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {displayDescription && nodeData.task.description != null
            ? nodeData.task.description
            : nodeData.task.name}
        </div>
      </div>
      <div style={{ position: "absolute", top: "20px", right: "20px" }}>
        <CardLabel
          type={nodeData.task.type}
          displayDescription={displayDescription}
        />
      </div>
      {maybeAddButton}
    </div>
  );
};

export default DoWhileTask;
