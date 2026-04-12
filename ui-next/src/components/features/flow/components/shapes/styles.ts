import theme from "components/features/flow/theme";
import { TaskStatus, TaskType } from "types";

export const getCardVariant = (
  type: TaskType,
  status?: TaskStatus,
  selected?: boolean,
) => {
  const outlineColor = selected
    ? theme.taskCard.selected.outlineColor
    : theme.taskStatusOutline[status ?? TaskStatus.NULL];

  const isOperator = [
    TaskType.FORK_JOIN_DYNAMIC,
    TaskType.JOIN,
    TaskType.FORK_JOIN,
    TaskType.FORK_JOIN_DYNAMIC,
    TaskType.TERMINATE,
    TaskType.SUB_WORKFLOW,
    TaskType.DYNAMIC,
    TaskType.SET_VARIABLE,
    TaskType.START_WORKFLOW,
  ].includes(type);

  let cardStyles = {};

  const operatorStyles = {
    backgroundColor: theme.taskCard.operators.background,
    border: outlineColor
      ? `3px solid ${outlineColor}`
      : `3px solid transparent`,
    color: theme.taskCard.operators.text,
    borderRadius: "10px",
  };

  const tasksStyles = {
    backgroundColor: theme.taskCard.systemTasks.background,
    color: theme.taskCard.systemTasks.color,
    border: outlineColor
      ? `3px solid ${outlineColor}`
      : `3px solid transparent`,
    borderRadius: "10px",
  };

  cardStyles = isOperator ? operatorStyles : tasksStyles;

  const errorStripesColor = () => {
    if (isOperator) {
      if (status === TaskStatus.CANCELED) {
        return "rgba(251, 164, 4, .25)";
      }
      return "rgba(90, 0, 0, .25)";
    } else {
      if (status === TaskStatus.CANCELED) {
        return "rgba(251, 164, 4, .15)";
      }
      return "rgba(220, 110, 110, .15)";
    }
  };

  switch (status) {
    case TaskStatus.FAILED:
    case TaskStatus.SKIPPED:
    case TaskStatus.CANCELED:
    case TaskStatus.TIMED_OUT:
      cardStyles = {
        ...cardStyles,
        backgroundImage: `linear-gradient( 135deg, rgba(0,0,0,0) 25%, ${errorStripesColor()} 25%, ${errorStripesColor()} 50%, rgba(0,0,0,0) 50%, rgba(0,0,0,0) 75%, ${errorStripesColor()} 75%, ${errorStripesColor()} 100% )`,
        // These are not magic numbers!, see: https://css-tricks.com/no-jank-css-stripes/
        backgroundSize: "56.57px 56.57px",
      };
      break;

    default:
      break;
  }

  const boxShadows = [TaskType.SWITCH, TaskType.DECISION].includes(type)
    ? []
    : ["0 2px 20px rgba(0,0,0,.4)"];
  if (selected) {
    boxShadows.push(theme.taskCard.selected.boxShadow);
  }

  cardStyles = {
    ...cardStyles,
    boxShadow: boxShadows.join(", "),
  };

  return cardStyles;
};
