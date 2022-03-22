import React, { useState, useEffect } from "react";
import { Tabs, Tab, ReactJson, Dropdown, Banner } from "../../components";

import TaskSummary from "./TaskSummary";
import TaskLogs from "./TaskLogs";

import { makeStyles } from "@material-ui/styles";
import _ from "lodash";

const useStyles = makeStyles({
  margin: {
    margin: 15,
  },
  dfSelect: {
    padding: 15,
    backgroundColor: "#efefef",
  },
  tabContent: {
    flexGrow: 1,
  },
  reactJson: {},
});

export default function RightPanel({ selectedTask, dag, onTaskChange }) {
  const [tabIndex, setTabIndex] = useState(0);

  const classes = useStyles();

  useEffect(() => {
    setTabIndex(0); // Reset to Status Tab on ref change
  }, [selectedTask]);

  if (!selectedTask) {
    return null;
  }

  const dfOptions = selectedTask ? dag.dfChildInfo(selectedTask.ref) : null;
  const { ref, taskId } = selectedTask;

  let taskResult,
    retryOptions = null;
  const node = dag.graph.node(ref);
  if (node.taskResults.length > 1) {
    retryOptions = node.taskResults;
  }

  if (taskId) {
    taskResult = node.taskResults.find((task) => task.taskId === taskId);
  } else {
    taskResult = _.last(node.taskResults);
  }

  return (
    <>
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
      <div className={classes.tabContent}>
        {tabIndex === 0 && <TaskSummary taskResult={taskResult} />}
        {tabIndex === 1 && (
          <ReactJson
            className={classes.reactJson}
            src={taskResult.inputData}
            label="Task Input"
          />
        )}
        {tabIndex === 2 && (
          <>
            {taskResult.externalOutputPayloadStoragePath && (
              <Banner className={classes.margin}>
                This task has externalized output. Please reference{" "}
                <code>externalOutputPayloadStoragePath</code> for the storage
                location.
              </Banner>
            )}
            <ReactJson
              className={classes.reactJson}
              src={taskResult.outputData}
              label="Task Output"
            />
          </>
        )}
        {tabIndex === 3 && <TaskLogs task={taskResult} />}
        {tabIndex === 4 && (
          <ReactJson
            className={classes.reactJson}
            src={taskResult}
            label="Unabridged Task Execution Result"
          />
        )}
        {tabIndex === 5 && (
          <ReactJson
            className={classes.reactJson}
            src={taskResult.workflowTask}
            label="Task Definition at Runtime"
          />
        )}
      </div>
    </>
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
    case "SCHEDULED":
      icon = "\u231B";
      break; // hourglass
    default:
      icon = "\u274C"; // red-X
  }
  return icon + "\u2003";
}
