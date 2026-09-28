import { TaskStatus } from "types/TaskStatus";
import { dropdownIcon } from "./dropdownIcon";

export function IterationStatusIcon({
  status,
  size = 14,
}: {
  status: TaskStatus;
  size?: number;
}) {
  return (
    <span style={{ fontSize: size, lineHeight: 1, userSelect: "none" }}>
      {dropdownIcon(status).trim()}
    </span>
  );
}
