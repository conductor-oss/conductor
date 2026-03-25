import _first from "lodash/first";
import _flow from "lodash/flow";
import _identity from "lodash/identity";
import _last from "lodash/last";
import { durationRenderer, juxt, timestampRenderer } from "utils";
import { ExecutionTask } from "types/Execution";

// Define types for the timeline data structures
interface TimelineGroup {
  id: string;
  content: string;
  treeLevel?: number;
  nestedGroups?: string[];
}

interface TimelineItem {
  id: string;
  group: string;
  content: string;
  start: Date;
  end: Date;
  title: string;
  className: string;
  style?: string;
}

type ExecutionStatusMap = Record<string, { related?: unknown }>;

const extractGroupsAndItems = juxt(
  _flow([
    _first,
    (gMap: Map<string, TimelineGroup>) => Array.from(gMap.values()),
  ]), // groups to array of values
  _flow([_last, _identity]), // don't modify items
);

function truncate(val: string | undefined): string {
  const maxLabelLength = 20;
  if (val?.length && val.length > maxLabelLength + 3) {
    return val.substring(0, maxLabelLength) + "...";
  }
  return val || "";
}

// Extract the core logic for testing
export const processTasksToGroupsAndItems = (
  tasks: ExecutionTask[],
  executionStatusMap: ExecutionStatusMap,
): [TimelineGroup[], TimelineItem[]] => {
  const [groupMap, itemsList] = tasks.reduce(
    (
      acc: [Map<string, TimelineGroup>, TimelineItem[]],
      t: ExecutionTask,
    ): [Map<string, TimelineGroup>, TimelineItem[]] => {
      const [gc, ic] = acc;
      const group: TimelineGroup = {
        id: t.referenceTaskName,
        content: `${truncate(t.referenceTaskName)} (${truncate(
          t.workflowTask.name,
        )})`,
        ...(executionStatusMap[t.workflowTask.taskReferenceName]?.related ==
        null
          ? {}
          : { treeLevel: 2 }),
      };
      let item: TimelineItem | TimelineItem[] = [];
      if ((t.startTime && t.startTime > 0) || (t.endTime && t.endTime > 0)) {
        const startTime =
          t.startTime && t.startTime > 0
            ? new Date(t.startTime)
            : new Date(t.endTime!);

        const endTime =
          t.endTime && t.endTime > 0
            ? new Date(t.endTime)
            : new Date(t.startTime!);

        const scheduledTime = t.scheduledTime
          ? new Date(t.scheduledTime)
          : null;
        const duration = durationRenderer(
          endTime.getTime() - startTime.getTime(),
        );

        item = {
          id: t.taskId!,
          group: t.referenceTaskName,
          content: `${duration}`,
          start: startTime,
          end: endTime,
          title: `${t.referenceTaskName} (${
            t.status
          })<br/>${timestampRenderer(startTime.getTime())} - ${timestampRenderer(
            endTime.getTime(),
          )}`,
          className: `status_${t.status}`,
        };

        // Add scheduled time range as a separate item if scheduledTime is available
        if (scheduledTime && scheduledTime < startTime) {
          const scheduledDuration = durationRenderer(
            startTime.getTime() - scheduledTime.getTime(),
          );
          const scheduledItem: TimelineItem = {
            id: `${t.taskId}_scheduled`,
            group: t.referenceTaskName,
            content: scheduledDuration,
            start: scheduledTime,
            end: startTime,
            title: `Queue Wait Time: ${scheduledDuration} <br/> ${timestampRenderer(scheduledTime.getTime())} - ${timestampRenderer(startTime.getTime())}`,
            className: "status_SCHEDULED",
            style: "background-color: #ffb74d; opacity: 0.7;",
          };
          ic.push(scheduledItem);
        }
      }
      gc.set(t.referenceTaskName, group);
      return [gc, ic.concat(Array.isArray(item) ? item : [item])];
    },
    [new Map<string, TimelineGroup>(), [] as TimelineItem[]],
  );

  // Now process FORK_JOIN_DYNAMIC groups to set up nested groups correctly
  const groupsArray = Array.from(groupMap.values());
  groupsArray.forEach((group: TimelineGroup) => {
    const task = tasks.find(
      (t: ExecutionTask) => t.referenceTaskName === group.id,
    );
    if (
      task?.workflowTask.type === "FORK_JOIN_DYNAMIC" &&
      task.inputData?.forkedTasks
    ) {
      group.nestedGroups = task.inputData.forkedTasks.map((taskId: string) => {
        // Check if the group exists as-is first
        if (groupMap.has(taskId)) {
          return taskId;
        }
        // If not, try with __iteration suffix
        const suffixedId = `${taskId}__${task?.iteration}`;
        if (groupMap.has(suffixedId)) {
          return suffixedId;
        }
        // If neither exists, return the original (will cause error but preserves original behavior)
        return taskId;
      });
    }
  });

  return extractGroupsAndItems([groupMap, itemsList]) as [
    TimelineGroup[],
    TimelineItem[],
  ];
};
