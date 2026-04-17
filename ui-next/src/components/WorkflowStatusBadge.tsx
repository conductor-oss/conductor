import { FunctionComponent } from "react";
import { getChipStatusColor } from "utils/helpers";
import { capitalizeFirstLetter } from "utils/utils";
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
  let formattedStatus = status ? status.toLowerCase() : "";
  formattedStatus =
    formattedStatus && formattedStatus.length > 0
      ? capitalizeFirstLetter(formattedStatus)
      : "";
  return (
    <TagChip
      style={chipStyles}
      label={formattedStatus}
      id={`${formattedStatus}-chip`}
    />
  );
};

export default WorkflowStatusBadge;
