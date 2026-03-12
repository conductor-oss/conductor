import { FunctionComponent } from "react";
import { TaskStatus } from "types/TaskStatus";
import { HumanTaskState as TaskState } from "types/HumanTaskTypes";
import { getChipStatusColor } from "utils/helpers";
import { capitalizeFirstLetter } from "utils/utils";
import TagChip from "./TagChip";

export interface StatusBadgeProps {
  status: TaskStatus | TaskState;
  labelConcat?: string;
}

const StatusBadge: FunctionComponent<StatusBadgeProps> = ({
  status,
  labelConcat = "",
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
      label={`${formattedStatus}${labelConcat}`}
      id={`${formattedStatus}-chip`}
    />
  );
};

export default StatusBadge;
