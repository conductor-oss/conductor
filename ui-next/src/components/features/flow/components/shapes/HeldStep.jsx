import { Pause as HeldIcon } from "@phosphor-icons/react";
import { taskToSize } from "components/features/flow/nodes/mapper/layout";
import { getFlowTheme } from "components/features/flow/theme";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";

/**
 * The step held native workflow code would take next. It has not happened and may not exist.
 * Clicking it opens HeldStepPanel, which explains the wait.
 */
const HeldStep = ({ nodeData }) => {
  const { mode } = useContext(ColorModeContext);
  const { color } = getFlowTheme(mode).terminalTask;
  const { width, height } = taskToSize(nodeData.task);
  return (
    <div
      title={nodeData.held}
      data-testid="held-step"
      style={{
        width,
        height,
        cursor: "pointer",
        border: `2px ${nodeData.selected ? "solid" : "dashed"} #F5BF42`,
        borderRadius: "10px",
        background: "rgba(245, 191, 66, 0.12)",
        color,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: "8px",
        fontSize: 14,
        fontWeight: 500,
        whiteSpace: "nowrap",
      }}
    >
      <HeldIcon weight="bold" size={14} />
      {nodeData.task.name}
    </div>
  );
};

export default HeldStep;
