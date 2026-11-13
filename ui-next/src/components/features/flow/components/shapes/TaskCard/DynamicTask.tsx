import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { Link as LinkIcon } from "@phosphor-icons/react";
import { Box } from "@mui/material";
import { cyan } from "theme/tokens/colors";
import { DynamicTaskDef } from "types/TaskType";
import { CardLink } from "./CardLink";

export const DynamicTask = ({
  nodeData,
}: {
  nodeData: NodeTaskData<DynamicTaskDef>;
}) => {
  // Only a dynamic task that resolved to SUB_WORKFLOW outputs a subWorkflowId,
  // so its presence is enough — taskToExecute may be an unresolved expression.
  const subWorkflowId = nodeData.outputData?.subWorkflowId;

  return typeof subWorkflowId === "string" && subWorkflowId ? (
    <Box
      sx={{
        mt: 2,
        display: "flex",
        alignItems: "center",
        gap: 2,
      }}
    >
      <LinkIcon />
      <CardLink sx={{ color: cyan }} href={`/execution/${subWorkflowId}`}>
        {subWorkflowId}
      </CardLink>
    </Box>
  ) : null;
};
