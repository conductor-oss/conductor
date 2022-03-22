import React from "react";
import { NavLink, DataTable, Button, Heading } from "../../components";
import { LinearProgress, Tooltip } from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import { useSchedules } from "./schedulerHooks";
import sharedStyles from "../../pages/styles";
import { Helmet } from "react-helmet";
import AddIcon from "@material-ui/icons/Add";
import CheckIcon from "@material-ui/icons/Check";
import cronstrue from "cronstrue";
import { useEnv } from "../../plugins/env";

const useStyles = makeStyles({
  ...sharedStyles,
  submitButton: {
    float: "right",
  },
  header: {
    ...sharedStyles.header,
    paddingBottom: 30,
  },
});

export default function Scheduler() {
  const classes = useStyles();
  const { env, stack } = useEnv();
  const { data: schedules, isFetching } = useSchedules();

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Workflow Scheduler</title>
      </Helmet>
      {isFetching && <LinearProgress />}
      <div className={classes.header}>
        <Button
          className={classes.submitButton}
          component={NavLink}
          path="/scheduler/schedule"
          startIcon={<AddIcon />}
        >
          New Schedule
        </Button>
        <Heading level={3} gutterBottom>
          Workflow Scheduler
        </Heading>
      </div>
      <div className={classes.tabContent}>
        <DataTable
          data={schedules}
          columns={[
            {
              name: "name",
              label: "Trigger Name",
              renderer: (name) => (
                <NavLink path={`/scheduler/schedule/${name}`}>{name}</NavLink>
              ),
            },
            {
              name: "cronExpression",
              label: "Cron Expression",
              renderer: (cron) => (
                <Tooltip title={cron}>
                  <span>{cronstrue.toString(cron)}</span>
                </Tooltip>
              ),
            },
            {
              name: "enabled",
              label: "Enabled",
              renderer: (enabled) => enabled && <CheckIcon />,
            },
            {
              id: "workflowName",
              name: "triggerPayload.name",
              label: "Workflow Name",
            },
            {
              id: "workflowVersion",
              name: "triggerPayload.version",
              label: "Workflow Version",
            },
            {
              id: "executions",
              name: "triggerPayload.name",
              label: "Executions",
              renderer: (name) => (
                <NavLink path={`/?workflowType=${name.trim()}`} newTab>
                  Query
                </NavLink>
              ),
            },
            {
              id: "history",
              name: "name",
              label: "History",
              renderer: (name) => (
                <NavLink newTab path={spinnakerLink(name, env, stack)}>
                  Open in Spinnaker
                </NavLink>
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}

function spinnakerLink(name, env, stack) {
  return `https://www.spinnaker.mgmt.netflix.net/#/applications/conductorscheduler/executions?pipeline=${name}_${env}_${stack}`;
}
