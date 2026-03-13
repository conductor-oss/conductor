import { useRef } from "react";
import { TaskType } from "types";
import { Fade } from "@mui/material";

const OPERATOR_TASK_TYPES = [
  TaskType.FORK_JOIN_DYNAMIC,
  TaskType.JOIN,
  TaskType.FORK_JOIN,
  TaskType.FORK_JOIN_DYNAMIC,
  TaskType.TERMINATE,
  TaskType.SUB_WORKFLOW,
  TaskType.DYNAMIC,
  TaskType.TERMINATE_WORKFLOW,
  TaskType.SET_VARIABLE,
  TaskType.WAIT,
  TaskType.START_WORKFLOW,
];

export const TaskDescription = ({
  description,
  taskType,
}: {
  description: string;
  taskType: TaskType;
}) => {
  const divRef = useRef<HTMLDivElement>(null);

  let borderColor = "rgba(0, 0, 0, 0.1)";
  let borderTopColor = "#cccccc";
  let color = "#555555";
  let textShadow = "none";
  let background = "rgba(255, 255, 255, 0.35)";
  if (OPERATOR_TASK_TYPES.includes(taskType)) {
    borderColor = "rgba(255, 255, 255, 0.2)";
    color = "white";
    textShadow = "0 0 2px rgba(0, 0, 0, 1)";
    borderTopColor = "rgba(255, 255, 255, 0.5)";
    background = "rgba(255, 255, 255, 0.3)";
  }

  return (
    <Fade
      in={true}
      easing={{ enter: "ease-out", exit: "ease-in" }}
      mountOnEnter
    >
      <div
        ref={divRef}
        style={{
          position: "absolute",
          left: "0",
          bottom: "0",
          width: "calc(100% + 6px)",
          background: background,
          backdropFilter: "blur(5px)",
          padding: "8px 10px",
          margin: "-3px",
          borderRadius: "0 0 8px 8px",
          zIndex: 1000,
          boxShadow: "0px -1px 5px 0 rgba(80, 80, 80, 0.15)",
          fontSize: "10pt",
          fontWeight: "300",
          border: `1px solid ${borderColor}`,
          borderTop: `1px solid ${borderTopColor}`,
          color: color,
          textShadow: textShadow,
        }}
      >
        {description}
      </div>
    </Fade>
  );
};
