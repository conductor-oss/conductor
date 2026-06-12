import React from "react";
import { KeyValueTable, LinearProgress } from "../../components";
import { usePollData, useQueueSize } from "../../data/task";
import _ from "lodash";
import { timestampRenderer } from "../../utils/helpers";

export default function TaskPollData({ task }) {
  const { data: pollData, isLoading } = usePollData(task.workflowTask.name);
  const { data: queueSize, isLoadingQueueSize } = useQueueSize(
    task.workflowTask.name,
    task.domain
  );

  if (isLoading || isLoadingQueueSize) {
    return <LinearProgress />;
  }

  const pollDataRow = pollData.find((row) => {
    if (task.domain) {
      return row.domain === task.domain;
    } else {
      return _.isUndefined(row.domain);
    }
  });

  const data = [
    { label: "Task Name", value: task.workflowTask.name },
    { label: "Domain", value: _.defaultTo(task.domain, "(No Domain Set)") },
  ];

  if (pollDataRow) {
    data.push({
      label: "Last Polled By Worker",
      value: pollDataRow.workerId,
    });
    data.push({
      label: "Last Poll Time",
      value: timestampRenderer(pollDataRow.lastPollTime),
    });
  }
  if (queueSize !== undefined) {
    data.push({
      label: "Current Queue Size",
      value: queueSize,
    });
  }

  return <KeyValueTable data={data} />;
}
