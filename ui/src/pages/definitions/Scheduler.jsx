import React from "react";
import { NavLink, DataTable, Button } from "../../components";
import { makeStyles } from "@material-ui/styles";
import Header from "./Header";
import sharedStyles from "../styles";
import { Helmet } from "react-helmet";
import { useSchedulers } from "../../data/scheduler";
import AddIcon from "@material-ui/icons/Add";

const useStyles = makeStyles(sharedStyles);

const columns = [
  {
    name: "name",
    renderer: (name) => (
      <NavLink path={`/schedulerDef/${name}`}>{name}</NavLink>
    ),
  },
  {
    name: "cronExpression",
  },
  {
    name: "zoneId",
  },
  {
    name: "workflowName",
    renderer: (val, row) => {
      const req = row.startWorkflowRequest;
      return req ? req.name : "";
    },
    searchable: "calculated",
  },
  {
    name: "paused",
    renderer: (val) => (val ? "Yes" : "No"),
  },
  {
    name: "runCatchupScheduleInstances",
    label: "Catchup",
    renderer: (val) => (val ? "Yes" : "No"),
  },
];

export default function Schedulers() {
  const classes = useStyles();

  const { data: schedulers, isFetching } = useSchedulers();

  return (
    <div className={classes.wrapper}>
      <Header tabIndex={3} loading={isFetching} />
      <Helmet>
        <title>Conductor UI - Scheduler Definitions</title>
      </Helmet>

      <div className={classes.tabContent}>
        <div className={classes.buttonRow}>
          <Button
            component={NavLink}
            path="/schedulerDef"
            startIcon={<AddIcon />}
          >
            New Scheduler Definition
          </Button>
        </div>

        {schedulers && (
          <DataTable
            title={`${schedulers.length} results`}
            localStorageKey="schedulersTable"
            defaultShowColumns={[
              "name",
              "cronExpression",
              "zoneId",
              "workflowName",
              "paused",
            ]}
            keyField="name"
            data={schedulers}
            columns={columns}
          />
        )}
      </div>
    </div>
  );
}
