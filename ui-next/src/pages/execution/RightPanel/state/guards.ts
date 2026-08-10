import * as TabsList from "pages/execution/state/constants";
import { RightPanelContext } from "./types";
import isNil from "lodash/isNil";

const UPDATABLE_STATUSES = ["IN_PROGRESS", "SCHEDULED"];

export const isSelectedTaskStatusUpdatable = ({
  selectedTask,
  taskDetails,
}: RightPanelContext) => {
  if (selectedTask != null) {
    const { status } = !isNil(selectedTask?.status)
      ? selectedTask
      : taskDetails!;

    return UPDATABLE_STATUSES.includes(status);
  }
};

export const isSummaryTab = ({ currentTab }: RightPanelContext) =>
  currentTab === TabsList.SUMMARY_TAB;

export const isInputTab = ({ currentTab }: RightPanelContext) =>
  currentTab === TabsList.INPUT_TAB;

export const isOutputTab = ({ currentTab }: RightPanelContext) =>
  currentTab === TabsList.OUTPUT_TAB;

export const isLogsTab = ({ currentTab }: RightPanelContext) =>
  currentTab === TabsList.LOGS_TAB;

export const isJsonTab = ({ currentTab }: RightPanelContext) =>
  currentTab === TabsList.JSON_TAB;
