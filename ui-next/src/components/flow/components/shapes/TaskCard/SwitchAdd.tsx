import { NodeTaskData } from "components/flow/nodes/mapper";
import { getFlowTheme } from "components/flow/theme";
import { WorkflowEditContext } from "pages/definition/state";
import {
  TaskAndCrumbs,
  usePerformOperationOnDefinition,
} from "pages/definition/state/usePerformOperationOnDefintion";
import { MouseEvent, useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { SwitchTaskDef } from "types/TaskType";
import { shouldHide } from "./helpers";
import PlusIcon from "./icons/PlusIcon";

const getPosition = (taskcount: number) => {
  switch (taskcount) {
    case 1:
      return {
        bottom: "-12px",
        right: "110px",
      };
    case 2:
      return {
        bottom: "-12px",
        right: "7px",
      };
    case 3:
      return {
        bottom: "-12px",
        right: "7px",
      };
    default:
      return {
        bottom: "15px",
        right: "-10px",
      };
  }
};

const SwitchAdd = (
  { nodeData }: { nodeData: Partial<NodeTaskData<SwitchTaskDef>> } = {
    nodeData: { status: undefined, withinExpandedSubWorkflow: false },
  },
) => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const { handleAddSwitchPath: onAddSwitchPath } =
    usePerformOperationOnDefinition(workflowDefinitionActor!);

  const handleAddEdge = (e: MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    onAddSwitchPath(nodeData as TaskAndCrumbs);
  };
  const { mode } = useContext(ColorModeContext);
  const theme = getFlowTheme(mode);

  return shouldHide(nodeData) ? (
    <div
      // className="AddEdgeButton"
      style={{
        position: "absolute",
        borderRadius: "20px",
        width: "20px",
        height: "20px",
        background: theme.taskCard.switchAdd.background,
        color: theme.taskCard.switchAdd.iconColor,
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        boxShadow: "0 0 4px black",
        zIndex: "2",
        ...getPosition(
          Object.keys(nodeData?.task?.decisionCases || {}).length + 1,
        ),
      }}
      onClick={handleAddEdge}
      role="button"
      id={`add-case-${nodeData.task?.taskReferenceName}`}
    >
      <div
        style={{
          pointerEvents: "none",
          display: "flex",
          height: "100%",
          alignItems: "center",
        }}
      >
        <PlusIcon size={14} color={theme.taskCard.switchAdd.iconColor} />
      </div>
    </div>
  ) : null;
};

export default SwitchAdd;
