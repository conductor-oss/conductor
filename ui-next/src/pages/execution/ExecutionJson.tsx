import { Box } from "@mui/material";
import ReactJson from "components/ReactJson";
import { WorkflowExecution } from "types/Execution";

export default function ExecutionJson({
  execution,
}: {
  execution: WorkflowExecution;
}) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        overflow: "auto",
        height: "100%",
        padding: 3,
        pb: 0,
      }}
    >
      <ReactJson
        src={execution}
        title="Complete Workflow JSON"
        workflowName={execution.workflowName}
        editorHeight="100%"
      />
    </Box>
  );
}
