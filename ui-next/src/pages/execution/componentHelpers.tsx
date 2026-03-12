import { ExecutionTask } from "types/Execution";
import ClipboardCopy from "components/ClipboardCopy";
import { Link } from "@mui/material";
import _isEmpty from "lodash/isEmpty";

export function taskIdRenderer(handleClick: (row: ExecutionTask) => void) {
  return (taskId: string, row: ExecutionTask) => {
    let defTaskDisplay = taskId;
    if (taskId) {
      defTaskDisplay = `${taskId.substring(0, 4)}..${taskId.substring(
        taskId.length - 4,
      )}`;
    }
    return (
      <ClipboardCopy value={taskId}>
        <Link
          href="#"
          onClick={() => {
            handleClick(row);
          }}
        >
          {defTaskDisplay}
        </Link>
      </ClipboardCopy>
    );
  };
}

export function clickHandler(
  handleSelectedTask: ((task: ExecutionTask) => void) | undefined,
) {
  return function handleClick(row: ExecutionTask) {
    if (!_isEmpty(row)) {
      handleSelectedTask?.(row);
    }
  };
}
