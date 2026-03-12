import { getCardVariant } from "./styles";
import CardAttemptsBadge from "./TaskCard/CardAttemptsBadge";
import CardIcon from "./TaskCard/CardIcon";
import CardLabel from "./TaskCard/CardLabel";
import CardStatusBadge from "./TaskCard/CardStatusBadge";
import DeleteButton from "./TaskCard/DeleteButton";
import { showIterationChip } from "./TaskCard/helpers";

const SubWorkflowTask = ({
  nodeData,
  isInconsistent,
  displayDescription = false,
}) => {
  const { task } = nodeData;
  const { type } = task;

  const subWorkflowName = task.name ? task.name : task.subWorkflowParam?.name;
  const showIterationsNumber = showIterationChip(nodeData);

  return (
    <div
      style={{
        cursor: isInconsistent ? "not-allowed" : "pointer",
        display: "flex",
        width: "100%",
        padding: "20px",
        border: "1px dashed black",
        borderRadius: "20px",
        textAlign: "center",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        ...getCardVariant(type, nodeData.status, nodeData.selected),
        background: "#5a8fa3d9",
      }}
    >
      {/* Execution */}
      <CardStatusBadge status={nodeData.status} />
      {showIterationsNumber ? (
        <CardAttemptsBadge attempts={nodeData.attempts} />
      ) : null}

      {/* Definition */}
      <DeleteButton maybeHideData={nodeData} />

      {displayDescription && nodeData.task.description != null ? (
        <>{nodeData.task.description}</>
      ) : (
        <div
          style={{
            flexGrow: 1,
            overflow: "hidden",
          }}
        >
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
            <CardIcon type={type} />
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
              {subWorkflowName}
            </div>
          </div>
          <div
            style={{
              position: "absolute",
              top: "35px",
              left: "49px",
              color: "white",
              opacity: 0.8,
              width: "80%",
              textAlign: "left",
            }}
          >
            <div
              style={{
                display: "block",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {task.taskReferenceName}
            </div>
          </div>
        </div>
      )}
      <div style={{ position: "absolute", top: "20px", right: "20px" }}>
        <CardLabel
          type={nodeData.task.type}
          displayDescription={displayDescription}
        />
      </div>
    </div>
  );
};

export default SubWorkflowTask;
