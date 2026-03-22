import { TaskType } from "types";
import { TaskStatus } from "../../types/TaskStatus";
import { colors } from "theme/tokens/variables";

const DEFAULT_NODE_WIDTH = 350;
const DEFAULT_NODE_HEIGHT = 100;
const DO_WHILE_PADDING = 30;

export const getFlowTheme = (mode = "light") => ({
  nodeTypes: {
    DEFAULT: {
      width: DEFAULT_NODE_WIDTH,
      height: DEFAULT_NODE_HEIGHT,
    },
    [TaskType.DO_WHILE]: {
      padding: DO_WHILE_PADDING,
      width: DEFAULT_NODE_WIDTH + DO_WHILE_PADDING * 2,
      height: 450,
      itemHeight: 150,
    },
    [TaskType.SWITCH]: { width: DEFAULT_NODE_WIDTH + 100, height: 200 },
    [TaskType.DECISION]: { width: DEFAULT_NODE_WIDTH, height: 200 },
    [TaskType.KAFKA_PUBLISH]: { width: DEFAULT_NODE_WIDTH, height: 150 },
    [TaskType.JSON_JQ_TRANSFORM]: {
      width: DEFAULT_NODE_WIDTH,
      height: 140,
    },
    [TaskType.HTTP]: { width: DEFAULT_NODE_WIDTH, height: 130 },
    [TaskType.EVENT]: { width: DEFAULT_NODE_WIDTH, height: 130 },
    [TaskType.WAIT]: {
      width: DEFAULT_NODE_WIDTH,
      height: 110,
    },
    [TaskType.FORK_JOIN]: { width: DEFAULT_NODE_WIDTH, height: 80 },
    [TaskType.FORK_JOIN_DYNAMIC]: {
      width: DEFAULT_NODE_WIDTH,
      height: 120,
    },
    [TaskType.TERMINAL]: { width: 80, height: 80 },
    [TaskType.SWITCH_JOIN]: { width: 350, height: 55 },
    FORK_JOIN_COLLAPSED: { width: DEFAULT_NODE_WIDTH, height: 140 },
    [TaskType.TASK_SUMMARY]: {
      width: DEFAULT_NODE_WIDTH + DO_WHILE_PADDING * 2,
      height: 50,
    },
  },
  taskStatusOutline: {
    [TaskStatus.COMPLETED]: colors.primaryGreen,
    [TaskStatus.COMPLETED_WITH_ERRORS]: "#EEAA00",
    [TaskStatus.CANCELED]: "#fba404",
    [TaskStatus.FAILED]: "#DD2222",
    [TaskStatus.FAILED_WITH_TERMINAL_ERROR]: "#DD2222",
    [TaskStatus.TIMED_OUT]: "#DD2222",
    [TaskStatus.IN_PROGRESS]: "#999999",
    [TaskStatus.SCHEDULED]: "#999999",
    [TaskStatus.SKIPPED]: "#F5BF42",
    [TaskStatus.PENDING]: "transparent",
    [TaskStatus.NULL]: "transparent",
  },
  graph: {
    handleBorderColor: "#585a68",
    handleSize: 8,
    backgroundColor: "#e6e6e6",
  },
  taskCard: {
    selected: {
      outlineColor: "#3388DD",
      boxShadow: "none",
    },
    operators: {
      background: "#205668",
      text: "white",
    },
    systemTasks: {
      background: "white",
      color: "#111111",
    },
    cardLabel: {
      background: "#dddddd",
      color: "black",
    },
    addPathButton: {
      background: "#eeeeee",
      text: "black",
      hoverBackground: "#dddddd",
    },
    deleteButton: {
      iconColor: "#DD2222",
      background: "#f0f0f0",
    },
    switchAdd: {
      iconColor: "black",
      background: "#f9f53d",
    },
  },
  terminalTask: {
    ...(mode === "dark"
      ? {
          color: colors.gray14,
          background: "#3a3929",
          border: "5px solid rgb(67 107 120)",
        }
      : {
          color: colors.gray00,
          background: "#ffffff",
          border: "5px solid rgb(114 164 180)",
        }),
  },
  decisionOperator: {
    caseLabel: {
      defaultCaseBackground: "rgb(225 243 255)",
      background: "rgb(225 243 255)",
    },
  },
  edges: {
    default: {
      stroke: "#757575",
      strokeWidth: 1,
    },
    completed: {
      stroke: colors.primaryGreen,
      strokeWidth: 2,
    },
  },
});

const theme = getFlowTheme();

export default theme;
