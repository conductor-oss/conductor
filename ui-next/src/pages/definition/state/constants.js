export const themes = [
  { name: "Light", value: "vs-light" },
  { name: "Dark", value: "vs-dark" },
];

// Events
export const UPDATE_ATTRIBS_EVT = "updateAttributes";
export const SAVE_EVT = "save";
export const RESET_EVT = "reset";
export const DELETE_EVT = "delete";
export const CHANGE_VERSION_EVT = "changeVersion";
export const ASSIGN_MESSAGE_EVT = "assignMessage";
export const MESSAGE_RESET_EVT = "messageReset";
export const RESET_CONFIRM_EVT = "resetConfirm";
export const CANCEL_EVENT_EVT = "cancel";
export const DELETE_CONFIRM_EVT = "deleteConfirm";
export const CHANGE_TAB_EVT = "changeTab";
export const CHANGE_TAB_INNER_EVT = "changeTabInner";
export const PERFORM_OPERATION_EVT = "performOperation";
export const REPLACE_TASK_EVT = "replaceTask";
export const DEBOUNCE_REPLACE_TASK_EVT = "debounceReplaceTask";
export const REMOVE_TASK_EVT = "removeTask";
export const ADD_NEW_SWITCH_PATH_EVT = "addNewSwitchPathToTask";
export const UPDATE_WF_METADATA_EVT = "updateWorkflowMetadata";
export const REMOVE_BRANCH_EVT = "removeBranch";

export const FLOW_FINISHED_RENDERING = "FLOW_FINISHED_RENDERING";

// Tabs
export const WORKFLOW_TAB = 0;
export const TASK_TAB = 1;
export const CODE_TAB = 2;
export const RUN_TAB = 3;
export const DEPENDENCIES_TAB = 4;

// ERROR MESSAGES
export const SEVERITY_ERROR = "error";

export const WORKFLOW_DOES_NOT_EXIST_MESSAGE =
  "Workflow definition does not exist, or you don't have permissions to view it.";
