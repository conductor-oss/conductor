import { TaskStatus } from "types/TaskStatus";

export function dropdownIcon(status: string) {
  let icon;
  switch (status) {
    case TaskStatus.COMPLETED:
      icon = "\u2705";
      break; // Green-checkmark
    case TaskStatus.COMPLETED_WITH_ERRORS:
      icon = "\u2757";
      break; // Exclamation
    case TaskStatus.CANCELED:
      icon = "\uD83D\uDED1";
      break; // stopsign
    case TaskStatus.IN_PROGRESS:
    case TaskStatus.SCHEDULED:
      icon = "\u231B";
      break; // hourglass
    case TaskStatus.TIMED_OUT:
      icon = "\u26D4";
      break;
    case TaskStatus.FAILED:
      icon = "\u2757";
      break;
    default:
      icon = "\u274C"; // red-X
  }

  return icon + "\u2003";
}
