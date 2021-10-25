import React, { useState, useEffect } from "react";
import { Tabs, Tab, Paper, ReactJson, Dropdown } from "../../components";

import TaskSummary from "./TaskSummary";
import TaskLogs from "./TaskLogs";

import { makeStyles } from "@material-ui/styles";
import _ from "lodash";

const useStyles = makeStyles((theme) => ({
  json: {
    margin: 15,
  },
  dfSelect: {
    padding: 15,
    backgroundColor: "#efefef",
  },
}));

export default function RightPanel({
  selectedTask,
  dag,
  onTaskChange,
  className,
}) {
  const [tabIndex, setTabIndex] = useState(0);

  const classes = useStyles();

  useEffect(() => {
    setTabIndex(0); // Reset to Status Tab on ref change
  }, [selectedTask]);

  let dfOptions = selectedTask ? dag.dfChildInfo(selectedTask.ref) : null;

  let taskResult,
    retryOptions = null;
  if (!selectedTask) {
    return null;
  } else {
    const { ref, taskId } = selectedTask;
    const node = dag.graph.node(ref);
    if (node.taskResults.length > 1) {
      retryOptions = node.taskResults;
    }

    if (taskId) {
      taskResult = node.taskResults.find((task) => task.taskId === taskId);
    } else {
      taskResult = _.last(node.taskResults);
    }
  }

  return (
    <Paper className={className}>
      {dfOptions && (
        <div className={classes.dfSelect}>
          <Dropdown
            onChange={(e, v) => {
              onTaskChange({ ref: v.ref });
            }}
            options={dfOptions}
            disableClearable
            value={dfOptions.find((opt) => opt.ref === selectedTask.ref)}
            getOptionLabel={(x) => `${dropdownIcon(x.status)} ${x.ref}`}
            style={{ marginBottom: 20, width: 500 }}
          />
        </div>
      )}

      {retryOptions && (
        <div className={classes.dfSelect}>
          <Dropdown
            label="Retried Task - Select an instance"
            disableClearable
            onChange={(e, v) => {
              onTaskChange({
                ref: taskResult.referenceTaskName,
                taskId: v.taskId,
              });
            }}
            options={retryOptions}
            value={retryOptions.find((opt) => opt.taskId === taskResult.taskId)}
            getOptionLabel={(t) =>
              `${dropdownIcon(t.status)} Attempt ${t.retryCount} - ${t.taskId}`
            }
            style={{ marginBottom: 20, width: 500 }}
          />
        </div>
      )}

      <Tabs value={tabIndex} contextual>
        <Tab label="Summary" onClick={() => setTabIndex(0)} />
        <Tab
          label="Input"
          onClick={() => setTabIndex(1)}
          disabled={!taskResult.status}
        />
        <Tab
          label="Output"
          onClick={() => setTabIndex(2)}
          disabled={!taskResult.status}
        />
        <Tab
          label="Logs"
          onClick={() => setTabIndex(3)}
          disabled={!taskResult.status}
        />
        <Tab
          label="JSON"
          onClick={() => setTabIndex(4)}
          disabled={!taskResult.status}
        />
        <Tab label="Definition" onClick={() => setTabIndex(5)} />
      </Tabs>
      <div className={classes.wrapper}>
        {tabIndex === 0 && <TaskSummary taskResult={taskResult} />}
        {tabIndex === 1 && (
          <ReactJson
            className={classes.json}
            src={taskResult.inputData}
            title="Task Input"
          />
        )}
        {tabIndex === 2 && (
          <ReactJson
            className={classes.json}
            src={taskResult.outputData}
            title="Task Output"
          />
        )}
        {tabIndex === 3 && <TaskLogs task={taskResult} />}
        {tabIndex === 4 && (
          <ReactJson
            className={classes.json}
            src={taskResult}
            title="Task Execution JSON"
          />
        )}
        {tabIndex === 5 && (
          <ReactJson
            className={classes.json}
            src={taskResult.workflowTask}
            title="Task Definition/Runtime Config"
          />
        )}
      </div>
    </Paper>
  );
}

function dropdownIcon(status) {
  let icon;
  switch (status) {
    case "COMPLETED":
      icon = "\u2705";
      break; // Green-checkmark
    case "COMPLETED_WITH_ERRORS":
      icon = "\u2757";
      break; // Exclamation
    case "CANCELED":
      icon = "\uD83D\uDED1";
      break; // stopsign
    case "IN_PROGRESS":
    case "SCHUEDULED":
      icon = "\u231B";
      break; // hourglass
    default:
      icon = "\u274C"; // red-X
  }
  return icon + "\u2003";
}
