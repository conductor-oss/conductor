import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { getFlowTheme } from "components/features/flow/theme";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { shouldHide } from "./helpers";
import DeleteIcon from "./icons/DeleteIcon";

const DeleteButton = (
  { maybeHideData }: { maybeHideData: Partial<NodeTaskData> } = {
    maybeHideData: { status: undefined, withinExpandedSubWorkflow: false },
  },
) => {
  const { mode } = useContext(ColorModeContext);
  const theme = getFlowTheme(mode);

  return shouldHide(maybeHideData) ? (
    <div
      className="DeleteButton"
      style={{
        position: "absolute",
        top: "-10px",
        right: "-10px",
        borderRadius: "20px",
        width: "20px",
        height: "20px",
        background: theme.taskCard.deleteButton.background,
        color: theme.taskCard.deleteButton.iconColor,
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        boxShadow: "0 0 4px black",
      }}
    >
      <div
        style={{
          pointerEvents: "none",
          display: "flex",
          height: "100%",
          alignItems: "center",
        }}
      >
        <DeleteIcon size={14} color={theme.taskCard.deleteButton.iconColor} />
      </div>
    </div>
  ) : null;
};

export default DeleteButton;
