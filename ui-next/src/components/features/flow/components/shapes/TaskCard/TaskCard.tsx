import HTTPPollTask from "components/features/flow/components/shapes/TaskCard/HTTPPollTask";
import { JDBCTask } from "components/features/flow/components/shapes/TaskCard/JDBCTask";
import StartWorkflowTask from "components/features/flow/components/shapes/TaskCard/StartWorkflowTask";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { TaskAndCrumbs } from "pages/definition/state/usePerformOperationOnDefintion";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { DynamicTaskDef, TaskStatus, TaskType, WaitTaskDef } from "types";
import { MCPTaskDef } from "types/TaskType";
import { getCardVariant } from "../styles";
import AddPathButton from "./AddPathButton";
import CardAttemptsBadge from "./CardAttemptsBadge";
import CardIcon from "./CardIcon";
import CardLabel from "./CardLabel";
import CardStatusBadge from "./CardStatusBadge";
import DeleteButton from "./DeleteButton";
import { DynamicTask } from "./DynamicTask";
import EventTask from "./EventTask";
import ForkJoinDynamicTask from "./ForkJoinDynamicTask";
import { showIterationChip } from "./helpers";
import HTTPTask from "./HTTPTask";
import INLINETask from "./INLINETask";
import JSONJQTransformTask from "./JSONJQTransformTask";
import KAFKATask from "./KAFKATask";
import { SimpleTask } from "./SimpleTask";
import { WaitTaskInfo } from "./WaitTaskInfo";
import { TaskDescription } from "../TaskDescription";

const getTaskCardContent = (type: TaskType, nodeData: NodeTaskData) => {
  switch (type) {
    case TaskType.HTTP:
      return <HTTPTask nodeData={nodeData} />;
    case TaskType.HTTP_POLL:
      return <HTTPPollTask nodeData={nodeData} />;
    case TaskType.JSON_JQ_TRANSFORM:
      return <JSONJQTransformTask nodeData={nodeData} />;
    case TaskType.INLINE:
      return <INLINETask nodeData={nodeData} />;
    case TaskType.KAFKA_PUBLISH:
      return <KAFKATask nodeData={nodeData} />;
    case TaskType.FORK_JOIN_DYNAMIC:
      return <ForkJoinDynamicTask nodeData={nodeData} />;
    case TaskType.EVENT:
      return <EventTask nodeData={nodeData} />;
    case TaskType.SIMPLE:
      return <SimpleTask nodeData={nodeData} />;
    case TaskType.JDBC:
      return <JDBCTask nodeData={nodeData} />;
    case TaskType.START_WORKFLOW:
      return <StartWorkflowTask nodeData={nodeData} />;
    case TaskType.DYNAMIC:
      return (
        <DynamicTask nodeData={nodeData as NodeTaskData<DynamicTaskDef>} />
      );
    default:
      return null;
  }
};

const TaskCard = ({
  nodeData,
  onClick = () => null,
  isInconsistent,
  displayDescription,
}: {
  nodeData: NodeTaskData;
  onClick: () => void;
  isInconsistent: boolean;
  displayDescription?: boolean;
}) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";

  const { task, status } = nodeData;
  const { name, type, taskReferenceName } = task;

  const showIterationsNumber = showIterationChip(nodeData);
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        borderRadius: "10px",
        cursor: isInconsistent ? "not-allowed" : "pointer",
        transition: "box-shadow 250ms",
        transitionDelay: "40ms",
        ...getCardVariant(
          type,
          status ?? TaskStatus.PENDING,
          nodeData.selected,
        ),
      }}
      onClick={onClick}
    >
      <div
        style={{
          position: "relative",
          padding: "20px",
          width: "100%",
          height: "100%",
          borderRadius: "10px",
          boxShadow: darkMode ? `0 0 10px gray` : undefined,
          color: darkMode ? colors.gray14 : undefined,
          background: darkMode ? colors.gray04 : undefined,
        }}
      >
        {/* Execution */}
        <CardStatusBadge status={status} />
        {showIterationsNumber ? (
          <CardAttemptsBadge attempts={nodeData.attempts} />
        ) : null}

        {/* Definition */}
        <DeleteButton maybeHideData={nodeData} />

        <div style={{ display: "flex", width: "100%", position: "relative" }}>
          <CardIcon
            type={type}
            integrationType={
              task.type === TaskType.MCP
                ? (task as MCPTaskDef).inputParameters?.integrationType
                : undefined
            }
          />

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
              {name}
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
              {taskReferenceName}
            </div>
            <div
              style={{
                position: "absolute",
                right: 0,
                left: 0,
                marginLeft: "auto",
                marginRight: "auto",
                textAlign: "center",
                marginTop: "3px",
              }}
            >
              {!status && type === TaskType.FORK_JOIN ? (
                <AddPathButton nodeData={nodeData as TaskAndCrumbs}>
                  Add fork
                </AddPathButton>
              ) : null}
              {type === TaskType.WAIT &&
              ((task as WaitTaskDef)?.inputParameters?.duration ||
                (task as WaitTaskDef)?.inputParameters?.until) ? (
                <WaitTaskInfo task={task as WaitTaskDef} />
              ) : null}
            </div>
          </div>

          <CardLabel
            type={type}
            displayDescription={false}
            integrationIconName={
              task.type === TaskType.MCP
                ? (task as MCPTaskDef).inputParameters?.integrationType
                : undefined
            }
          />
        </div>
        <div>{getTaskCardContent(type, nodeData)}</div>

        {displayDescription && task.description != null && (
          <TaskDescription description={task.description} taskType={type} />
        )}
      </div>
    </div>
  );
};

export default TaskCard;
