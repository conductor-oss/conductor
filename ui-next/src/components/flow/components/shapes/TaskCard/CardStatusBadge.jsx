import { CircularProgress } from "@mui/material";
import {
  Check as CompletedIcon,
  Prohibit as FailedIcon,
  ArrowArcRight as SkippedTaskIcon,
} from "@phosphor-icons/react";
import { colors } from "theme/tokens/variables";

import { TaskStatus } from "types/TaskStatus";

const getBackgroundByStatus = (status) => {
  switch (status) {
    case TaskStatus.COMPLETED:
      return colors.primaryGreen;
    case TaskStatus.COMPLETED_WITH_ERRORS:
      return "#EEAA00";
    case TaskStatus.CANCELED:
      return "#fba404";
    case TaskStatus.FAILED:
    case TaskStatus.FAILED_WITH_TERMINAL_ERROR:
    case TaskStatus.TIMED_OUT:
      return "#DD2222";
    case TaskStatus.IN_PROGRESS:
    case TaskStatus.SCHEDULED:
      return "white";
    case TaskStatus.SKIPPED:
      return "#F5BF42";
    default:
      return null;
  }
};

const CardStatusBadge = ({ status }) => {
  return [
    TaskStatus.IN_PROGRESS,
    TaskStatus.SCHEDULED,
    TaskStatus.COMPLETED,
    TaskStatus.COMPLETED_WITH_ERRORS,
    TaskStatus.FAILED,
    TaskStatus.FAILED_WITH_TERMINAL_ERROR,
    TaskStatus.CANCELED,
    TaskStatus.SKIPPED,
    TaskStatus.TIMED_OUT,
  ].includes(status) ? (
    <div
      style={{
        position: "absolute",
        top: "-15px",
        right: "-15px",
        borderRadius: "30px",
        width: "30px",
        height: "30px",
        background: getBackgroundByStatus(status),
        color: "#aaaaaa",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        boxShadow: "0 0 4px black",
      }}
    >
      {[TaskStatus.IN_PROGRESS, TaskStatus.SCHEDULED].includes(status) ? (
        // disableShrink for lower CPU load
        // see: https://mui.com/components/progress/
        <CircularProgress
          disableShrink
          size={16}
          thickness={6}
          color="primary"
        />
      ) : null}
      {status === TaskStatus.COMPLETED ? (
        <CompletedIcon weight="bold" color="white" />
      ) : null}
      {status === TaskStatus.COMPLETED_WITH_ERRORS ||
      status === TaskStatus.SKIPPED ? (
        <SkippedTaskIcon weight="bold" color="white" />
      ) : null}
      {[
        TaskStatus.CANCELED,
        TaskStatus.FAILED,
        TaskStatus.FAILED_WITH_TERMINAL_ERROR,
        TaskStatus.TIMED_OUT,
      ].includes(status) ? (
        <FailedIcon weight="bold" color="white" />
      ) : null}
    </div>
  ) : null;
};

export default CardStatusBadge;
