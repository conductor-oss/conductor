import {
  BOTTOM_PORT_MARGIN,
  taskToSize,
} from "components/flow/nodes/mapper/layout";
import { getFlowTheme } from "components/flow/theme";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";

const TerminalTask = ({ nodeData, portsVisible }) => {
  const { mode } = useContext(ColorModeContext);
  const theme = getFlowTheme(mode);

  const { task } = nodeData;
  const terminalClick = (event) => {
    event.stopPropagation();
  };

  const { width, height } = taskToSize(task);
  return (
    <div
      onClick={terminalClick}
      style={{
        width: width,
        height: height,
        marginTop:
          task.name === "start" && portsVisible ? -BOTTOM_PORT_MARGIN : 0,
      }}
    >
      <div
        style={{
          display: "flex",
          width: width,
          // Not a mistake, we want the height to be the same as the width
          height: width,
          borderRadius: width,
          color: theme.terminalTask.color,
          background: theme.terminalTask.background,
          border: theme.terminalTask.border,
          textAlign: "center",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {task.name === "start" ? "Start" : "End"}
      </div>
    </div>
  );
};

export default TerminalTask;
