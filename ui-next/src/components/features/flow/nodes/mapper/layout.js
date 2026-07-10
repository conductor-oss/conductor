import theme from "../../theme";
import { TaskType } from "types";
import _isNil from "lodash/isNil";

export const BOTTOM_PORT_MARGIN = 10;
const SWITCH_SIZE_INCREMENTER = 60;
const MIN_AMOUNT_OF_SWITCH_PORTS = 4;
const ADD_FORK_ADDITINAL_HEIGHT = 40;

const computeAdditionalWidth = (portsAmount) =>
  portsAmount > MIN_AMOUNT_OF_SWITCH_PORTS
    ? (portsAmount - MIN_AMOUNT_OF_SWITCH_PORTS) * SWITCH_SIZE_INCREMENTER
    : 0;

export const taskToSize = (task) => {
  const { type, executionData = null } = task;
  switch (type) {
    case TaskType.START:
    case TaskType.TERMINAL:
      return {
        width: theme.nodeTypes.TERMINAL.width,
        height: theme.nodeTypes.TERMINAL.height,
      };
    case TaskType.SWITCH_JOIN:
      return {
        width: theme.nodeTypes.SWITCH_JOIN.width,
        height: theme.nodeTypes.SWITCH_JOIN.height,
      };
    case TaskType.JOIN:
    case TaskType.FORK_JOIN: {
      const { forkTasks = [] } = task;
      return {
        width:
          theme.nodeTypes.FORK_JOIN.width +
          computeAdditionalWidth(forkTasks.length),
        height: _isNil(executionData)
          ? theme.nodeTypes.FORK_JOIN.height + ADD_FORK_ADDITINAL_HEIGHT
          : theme.nodeTypes.FORK_JOIN.height,
      };
    }
    case TaskType.DYNAMIC_JOIN:
    case TaskType.TERMINATE:
      return {
        width: theme.nodeTypes.FORK_JOIN.width,
        height: theme.nodeTypes.FORK_JOIN.height,
      };
    case TaskType.HTTP:
    case TaskType.HTTP_POLL:
    case TaskType.START_WORKFLOW:
      return {
        width: theme.nodeTypes.HTTP.width,
        height: theme.nodeTypes.HTTP.height,
      };
    case TaskType.EVENT:
      return {
        width: theme.nodeTypes.EVENT.width,
        height: theme.nodeTypes.EVENT.height,
      };
    case TaskType.WAIT:
      return {
        width: theme.nodeTypes.WAIT.width,
        height: task?.executionData?.status ? 100 : theme.nodeTypes.WAIT.height,
      };
    case TaskType.INLINE:
    case TaskType.JSON_JQ_TRANSFORM:
      return {
        width: theme.nodeTypes.JSON_JQ_TRANSFORM.width,
        height: theme.nodeTypes.JSON_JQ_TRANSFORM.height,
      };
    case TaskType.DO_WHILE:
      return {
        width: theme.nodeTypes.DO_WHILE.width,
        height: theme.nodeTypes.DO_WHILE.height,
      };
    case TaskType.KAFKA_PUBLISH:
      return {
        width: theme.nodeTypes.KAFKA_PUBLISH.width,
        height: theme.nodeTypes.KAFKA_PUBLISH.height,
      };
    case TaskType.DECISION:
    case TaskType.SWITCH: {
      const { decisionCases = {} } = task;
      return {
        width:
          theme.nodeTypes.SWITCH.width +
          computeAdditionalWidth(Object.keys(decisionCases).length + 1),
        height: theme.nodeTypes.SWITCH.height,
      };
    }
    case TaskType.FORK_JOIN_DYNAMIC:
      return {
        width: theme.nodeTypes.FORK_JOIN_DYNAMIC.width,
        height: theme.nodeTypes.FORK_JOIN_DYNAMIC.height,
      };
    case TaskType.TASK_SUMMARY: {
      const summaryValues = Object.keys(
        task?.executionData?.summary?.taskCountByStatus || {},
      ).length;

      const newHeight =
        summaryValues === 1 ? summaryValues * 68 : summaryValues * 48;

      return {
        width: theme.nodeTypes.TASK_SUMMARY.width,
        height: theme.nodeTypes.TASK_SUMMARY.height + newHeight,
      };
    }
    case "FORK_JOIN_COLLAPSED":
      return {
        width: theme.nodeTypes.FORK_JOIN_COLLAPSED.width,
        height: theme.nodeTypes.FORK_JOIN_COLLAPSED.height,
      };
    default:
      return {
        width: theme.nodeTypes.DEFAULT.width,
        height: theme.nodeTypes.DEFAULT.height,
      };
  }
};
