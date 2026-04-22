import StatusBadge from "components/StatusBadge";
import { taskStatusCompareFn } from "utils";
import CardLabel from "./TaskCard/CardLabel";
import CardStatusBadge from "./TaskCard/CardStatusBadge";
import { getCardVariant } from "./styles";

const TaskSummary = (props) => {
  const { nodeData, nodeHeight } = props;
  const { task } = nodeData;
  const { type } = task;

  return (
    <div
      style={{
        width: "100%",
        position: "relative",
      }}
    >
      <div
        style={{
          cursor: "pointer",
          display: "flex",
          width: "100%",
          padding: "20px",
          borderRadius: "10px",
          justifyContent: "center",
          position: "absolute",
          height: `${nodeHeight}px`,
          transition: "transform 0.2s ease-in-out",
          ...getCardVariant(type, nodeData.status, nodeData.selected),
        }}
      >
        {/* Execution */}
        <CardStatusBadge status={nodeData.status} />

        {/* Definition */}

        <div style={{ width: "100%" }}>
          <div
            style={{
              flexGrow: 1,
              overflow: "hidden",
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
          </div>
          <div
            style={{
              marginTop: "10px",
              display: "flex",
              alignItems: "center",
            }}
          >
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                width: "100%",
              }}
            >
              {Object.entries(nodeData?.summary?.taskCountByStatus)
                .sort(([key1], [key2]) => taskStatusCompareFn(key1, key2))
                .map(([key, value]) => (
                  <div
                    style={{
                      display: "flex",
                      flexDirection: "row",
                      padding: 4,
                    }}
                    key={key}
                  >
                    <StatusBadge status={key} key={key} />
                    <strong
                      style={{
                        paddingLeft: 2,
                        width: "100%",
                        textAlign: "right",
                      }}
                    >
                      {value}
                    </strong>
                  </div>
                ))}
            </div>
          </div>
        </div>

        <CardLabel type={nodeData.task.type} />
      </div>
    </div>
  );
};

export default TaskSummary;
