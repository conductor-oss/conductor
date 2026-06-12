import { Link } from "@mui/material";
import { TreeStructure as WorkflowIcon } from "@phosphor-icons/react";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";

const StartWorkflowTask = ({ nodeData }) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";

  const { task } = nodeData;
  const {
    inputParameters: { startWorkflow },
  } = task;

  return (
    <div style={{ paddingTop: "20px" }}>
      <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
        <WorkflowIcon style={{ marginRight: "10px", flexShrink: 0 }} />
        <div
          style={{
            fontSize: "0.8em",
            padding: "4px 8px",
            color: darkMode ? colors.gray14 : colors.gray01,
            background: darkMode ? colors.gray06 : colors.gray12,
            borderRadius: "5px",
            height: "fit-content",
          }}
        >
          Workflow
        </div>
        <div
          style={{
            padding: "0 8px",
            lineHeight: "2em",
            overflow: "hidden",
            textOverflow: "ellipsis",
            wordBreak: "keep-all",
            whiteSpace: "nowrap",
          }}
        >
          <Link
            href={`${window.location.origin}/workflowDef/${startWorkflow?.name}`}
            sx={{ color: "cyan" }}
            target="_blank"
            rel="noreferrer"
          >
            {startWorkflow?.name}
          </Link>
        </div>
      </div>
    </div>
  );
};

export default StartWorkflowTask;
