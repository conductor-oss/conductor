import { FunctionComponent } from "react";
import { getChipStatusColor } from "utils/helpers";
import { humanizeStatus } from "utils/utils";
import TagChip from "./ui/TagChip";
import { WorkflowExecutionStatus } from "types/Execution";

export interface WorkflowStatusBadgeProps {
  status: WorkflowExecutionStatus;
}

const WorkflowStatusBadge: FunctionComponent<WorkflowStatusBadgeProps> = ({
  status,
}) => {
  const color = getChipStatusColor(status);
  const chipStyles =
    color == null
      ? {}
      : {
          backgroundColor: color,
        };
  const formattedStatus = humanizeStatus(status);
  return (
    <TagChip
      style={chipStyles}
      label={formattedStatus}
      id={`${formattedStatus}-chip`}
    />
  );
};

export default WorkflowStatusBadge;
