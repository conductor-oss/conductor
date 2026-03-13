import _groupBy from "lodash/groupBy";
import { EventItem, GroupedEventItem } from "./types";
import { colors } from "theme/tokens/variables";

export const groupDataByMessageId = (data: EventItem[]): GroupedEventItem[] => {
  const groupedData = _groupBy(data, "messageId");

  return Object.keys(groupedData).map((messageId) => ({
    ...groupedData[messageId][0], // Take the first item to copy over the base properties
    groupedItems: groupedData[messageId], // Grouped items
  }));
};

export const actions: { name: string; label: string }[] = [
  {
    label: "Complete Task",
    name: "COMPLETE_TASK",
  },
  {
    label: "Terminate Workflow",
    name: "TERMINATE_WORKFLOW",
  },
  {
    label: "Update Variables",
    name: "UPDATE_VARIABLES",
  },
  {
    label: "Fail Task",
    name: "FAIL_TASK",
  },
  {
    label: "Start Workflow",
    name: "START_WORKFLOW",
  },
];

export const status: { name: string; label: string }[] = [
  {
    label: "In Progress",
    name: "IN_PROGRESS",
  },
  {
    label: "Completed",
    name: "COMPLETED",
  },
  {
    label: "Failed",
    name: "FAILED",
  },
  {
    label: "Skipped",
    name: "SKIPPED",
  },
];

export const statusColors: { [key: string]: string } = {
  IN_PROGRESS: colors.progressTag,
  COMPLETED: colors.successTag,
  FAILED: colors.errorTag,
  SKIPPED: colors.warningTag,
};

export const TIME_RANGE_OPTIONS = [
  { label: "5 minutes", value: 5 * 60 * 1000 },
  { label: "15 minutes", value: 15 * 60 * 1000 },
  { label: "30 minutes", value: 30 * 60 * 1000 },
  { label: "All time", value: -1 },
];

export const statusConfig = {
  FAILED: { label: "Failed", color: colors.errorTag },
  SKIPPED: { label: "Skipped", color: colors.greyBorder },
  IN_PROGRESS: { label: "In Progress", color: colors.progressTag },
  COMPLETED: { label: "Completed", color: colors.successTag },
} as const;

export const truncatePayload = (payload: object) => {
  const jsonString = JSON.stringify(payload);
  if (jsonString.length <= 100) {
    return jsonString;
  }
  return jsonString.substring(0, 97) + "...";
};
