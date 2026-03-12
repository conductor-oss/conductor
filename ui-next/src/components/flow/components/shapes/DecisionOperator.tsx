import StarShape from "./StarShape";

import { Diamond } from "@phosphor-icons/react";
import { NodeTaskData } from "components/flow/nodes/mapper";
import { SwitchTaskDef } from "types/TaskType";
import { getCardVariant } from "./styles";
import CardAttemptsBadge from "./TaskCard/CardAttemptsBadge";
import DeleteButton from "./TaskCard/DeleteButton";
import { showIterationChip } from "./TaskCard/helpers";
import SwitchAdd from "./TaskCard/SwitchAdd";
import { TaskDescription } from "./TaskDescription";
interface DecisionOperatorProps {
  nodeData: NodeTaskData<SwitchTaskDef>;
  nodeWidth: number;
  portsVisible: boolean;
  isInconsistent: boolean;
  displayDescription?: boolean;
}

const DecisionOperator = ({
  nodeData,
  nodeWidth,
  portsVisible,
  isInconsistent,
  displayDescription,
}: DecisionOperatorProps) => {
  const {
    task: { name, taskReferenceName },
  } = nodeData;
  const showIterationsNumber = showIterationChip(nodeData);
  return (
    <div
      style={{
        paddingBottom: "10px",
      }}
    >
      <div
        style={{
          position: "relative",
          width: `${nodeWidth - 100}px`,
          height: `${portsVisible ? 190 : 200}px`,
          cursor: isInconsistent ? "not-allowed" : "pointer",
          boxShadow: "none",
          border: "1px dashed black",
          ...getCardVariant(
            nodeData.task.type,
            nodeData.status,
            nodeData.selected,
          ),
          backgroundColor: "rgba(0, 0, 0, .1)",
          borderRadius: "20px",
        }}
      >
        <div style={{ width: "100%", height: "100%", position: "relative" }}>
          {/* Definition */}
          <DeleteButton maybeHideData={nodeData} />
          {showIterationsNumber ? (
            <CardAttemptsBadge attempts={nodeData.attempts} />
          ) : null}
          <div
            style={{
              position: "absolute",
              width: "100%",
              height: "100%",
              zIndex: 1,
            }}
          >
            <StarShape />
          </div>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              position: "absolute",
              width: "100%",
              height: "100%",
              zIndex: 2,
            }}
          >
            <div style={{ height: "24px", width: "24px" }}>
              <Diamond size={24} />
            </div>
            <div>{name}</div>
            <div style={{ color: "#aaa" }}>{taskReferenceName}</div>
          </div>
          <SwitchAdd nodeData={nodeData} />
        </div>
        {displayDescription && nodeData.task.description != null && (
          <TaskDescription
            description={nodeData.task.description}
            taskType={nodeData.task.type}
          />
        )}
      </div>
    </div>
  );
};

export default DecisionOperator;
