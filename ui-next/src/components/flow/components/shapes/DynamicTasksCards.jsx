import { useContext, useState } from "react";
import CardLabel from "./TaskCard/CardLabel";
import CardStatusBadge from "./TaskCard/CardStatusBadge";
// import CardAttemptsBadge from "./TaskCard/CardAttemptsBadge";
import Button from "components/MuiButton";
import { FlowExecutionContext } from "pages/execution/state";
import { TaskStatus } from "types/TaskStatus";
import DeleteButton from "./TaskCard/DeleteButton";
import { getCardVariant } from "./styles";

const DynamicTaskChildPlaceholder = ({
  type,
  nodeData,
  x,
  y,
  cardHeight,
  ellipsis,
}) => {
  const placeholderStyles = {
    cursor: "pointer",
    display: "flex",
    width: "100%",
    padding: "20px",
    borderRadius: "10px",
    position: "absolute",
    height: `${cardHeight}px`,
    transform: `translateX(${x}px) translateY(${y}px)`,
    transition: "transform 0.2s ease-in-out",
    ...getCardVariant(type, nodeData.status, nodeData.selected),
    outlineStyle: ellipsis ? "dashed" : "solid",
  };

  if (ellipsis) {
    placeholderStyles.outlineColor = "#444444";
    placeholderStyles.backgroundColor = "#FFEEAA";
  }

  if (nodeData.status === TaskStatus.PENDING) {
    placeholderStyles.outlineColor = "none";
    placeholderStyles.outlineStyle = "none";
  }

  return <div style={placeholderStyles}></div>;
};

const DynamicTasksCards = ({
  nodeData,
  isInconsistent,
  displayDescription = false,
}) => {
  const [isHovering, setIsHovering] = useState(false);
  const { onExpandDynamic } = useContext(FlowExecutionContext);
  const { task } = nodeData;
  const { type } = task;

  const collapsedTasksCount = task.executionData.collapsedTasks.length;
  const hoverMultiplier = 1.8;
  const showEllipsisCard = collapsedTasksCount > 4;
  const finalChildNumber = showEllipsisCard ? 4 : collapsedTasksCount;

  const offsetDistance = 40 / finalChildNumber;
  const cardHeight = 140 - (finalChildNumber - 1) * (40 / finalChildNumber);
  const initialXOffset = -(((finalChildNumber - 1) * offsetDistance) / 2);

  const completedTasks = nodeData?.collapsedTasksStatus
    ? nodeData?.collapsedTasksStatus.filter((item) => item === "COMPLETED")
    : [];
  return (
    <div
      style={{
        width: "100%",
        position: "relative",
      }}
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => setIsHovering(false)}
    >
      {[...Array(finalChildNumber)].map((_, i) => {
        let xTransform =
          initialXOffset + (finalChildNumber - i - 1) * offsetDistance;
        const yTransform = (finalChildNumber - i - 1) * offsetDistance;
        if (isHovering) {
          xTransform *= hoverMultiplier;
        }

        return (
          <DynamicTaskChildPlaceholder
            type={type}
            nodeData={nodeData}
            cardHeight={cardHeight}
            ellipsis={
              showEllipsisCard &&
              i === finalChildNumber - (finalChildNumber - 1)
            }
            x={xTransform}
            y={yTransform}
            key={`${finalChildNumber}_${i}`}
          />
        );
      })}
      <div
        style={{
          cursor: isInconsistent ? "not-allowed" : "pointer",
          display: "flex",
          width: "100%",
          padding: "20px",
          borderRadius: "10px",
          justifyContent: "center",
          position: "absolute",
          height: `${cardHeight}px`,
          transition: "transform 0.2s ease-in-out",
          transform: `translateX(${
            isHovering ? initialXOffset * hoverMultiplier : initialXOffset
          }px)`,
          ...getCardVariant(type, nodeData.status, nodeData.selected),
        }}
      >
        {/* Execution */}
        <CardStatusBadge status={nodeData.status} />

        {/* Definition */}
        <DeleteButton maybeHideData={nodeData} />

        <div style={{ width: "100%" }}>
          <div
            style={{
              flexGrow: 1,
              overflow: "hidden",
            }}
          >
            {displayDescription && nodeData.task.description != null ? (
              <>{nodeData.task.description}</>
            ) : (
              <>
                <div
                  style={{
                    display: "block",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {nodeData.task.name}
                </div>
                <div
                  style={{
                    color: "#AAAAAA",
                    display: "block",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {nodeData.task.taskReferenceName}
                </div>
              </>
            )}
          </div>
          <div
            style={{ marginTop: "10px", display: "flex", alignItems: "center" }}
          >
            <Button
              variant="secondary"
              size="small"
              style={{ height: "30px", fontSize: "9pt" }}
              onClick={() =>
                onExpandDynamic(task.executionData.parentTaskReferenceName)
              }
            >
              Expand
            </Button>
            <div style={{ paddingLeft: "10px" }}>
              {completedTasks?.length} out of {collapsedTasksCount} task
              {collapsedTasksCount > 1 ? "s" : ""} executed.
            </div>
          </div>
        </div>

        <CardLabel
          type={nodeData.task.type}
          displayDescription={displayDescription}
        />
        {/* <CardAttemptsBadge attempts={collapsedTasksCount} /> */}
      </div>
    </div>
  );
};

export default DynamicTasksCards;
