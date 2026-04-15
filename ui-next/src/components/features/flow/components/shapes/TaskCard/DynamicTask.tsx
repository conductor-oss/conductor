import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { Link as LinkIcon } from "@phosphor-icons/react";
import { Box, Link } from "@mui/material";
import { cyan } from "theme/tokens/colors";
import { DynamicTaskDef } from "types/TaskType";

export const DynamicTask = ({
  nodeData,
}: {
  nodeData: NodeTaskData<DynamicTaskDef>;
}) => {
  const isDynamicSubWorkflow =
    nodeData.task.inputParameters.taskToExecute === "SUB_WORKFLOW";
  const subWorkflowId = nodeData.outputData?.subWorkflowId as string;

  return isDynamicSubWorkflow && subWorkflowId ? (
    <Box
      sx={{
        mt: 2,
        display: "flex",
        alignItems: "center",
        gap: 2,
      }}
    >
      <LinkIcon />
      <Link
        sx={{ color: cyan }}
        href={`/execution/${subWorkflowId}`}
        target="_blank"
        rel="noreferrer"
      >
        {subWorkflowId}
      </Link>
    </Box>
  ) : null;
};
