import React from "react";
import { Paper, NavLink, KeyValueTable } from "../../components";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  paper: {
    minHeight: 300,
  },
});

export default function ExecutionSummary({ execution }) {
  const classes = useStyles();

  // To accommodate unexecuted tasks, read type & name out of workflowTask
  const data = [
    { label: "Workflow ID", value: execution.workflowId },
    { label: "Status", value: execution.status },
    { label: "Version", value: execution.workflowVersion },
    { label: "Start Time", value: execution.startTime, type: "date" },
    { label: "End Time", value: execution.endTime, type: "date" },
    {
      label: "Duration",
      value: execution.endTime - execution.startTime,
      type: "duration",
    },
  ];

  if (execution.parentWorkflowId) {
    data.push({
      label: "Parent Workflow ID",
      value: (
        <NavLink newTab path={`/execution/${execution.parentWorkflowId}`}>
          {execution.parentWorkflowId}
        </NavLink>
      ),
    });
  }

  if (execution.parentWorkflowTaskId) {
    data.push({
      label: "Parent Task ID",
      value: execution.parentWorkflowTaskId,
    });
  }

  if (execution.reasonForIncompletion) {
    data.push({
      label: "Reason for Incompletion",
      value: execution.reasonForIncompletion,
    });
  }

  return (
    <Paper className={classes.paper}>
      <KeyValueTable data={data} />
    </Paper>
  );
}
