import React, { useState } from "react";
import _ from "lodash";
import { FormControl, Grid, InputLabel } from "@material-ui/core";
import {
  Paper,
  Heading,
  PrimaryButton,
  Dropdown,
  Input,
  NavLink,
  DataTable,
} from "../../components";
import { useQueryState } from "react-router-use-location-state";
import DateRangePicker from "../../components/DateRangePicker";
import { DEFAULT_ROWS_PER_PAGE } from "../../components/DataTable";
import { useSchedulerExecutionSearch } from "../../data/scheduler";
import SchedulerDisabledBanner, {
  isSchedulerDisabled,
} from "../../components/SchedulerDisabledBanner";

import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import sharedStyles from "../styles";

const useStyles = makeStyles({
  ...sharedStyles,
  resetButton: {
    color: "#d32f2f",
    cursor: "pointer",
    display: "flex",
    alignItems: "center",
    gap: 4,
    border: "none",
    background: "none",
    fontSize: 14,
    padding: "6px 12px",
    "&:hover": {
      textDecoration: "underline",
    },
  },
});

const executionStatuses = ["POLLED", "EXECUTED", "FAILED"];
const MS_IN_DAY = 86400000;

export default function SchedulerExecutions() {
  const classes = useStyles();

  const [scheduleName, setScheduleName] = useQueryState("scheduleName", "");
  const [workflowName, setWorkflowName] = useQueryState("workflowName", "");
  const [executionId, setExecutionId] = useQueryState("executionId", "");
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [lookback, setLookback] = useQueryState("lookback", "");
  const [status, setStatus] = useQueryState("status", []);
  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE
  );
  const [queryObj, setQueryObj] = useState(buildQuery);

  const {
    data: resultObj,
    error,
    refetch,
  } = useSchedulerExecutionSearch({
    page,
    rowsPerPage,
    sort: "scheduledTime:DESC",
    query: queryObj.query,
    freeText: queryObj.freeText,
  });

  function buildQuery() {
    const clauses = [];
    if (!_.isEmpty(scheduleName)) {
      clauses.push(`scheduleName='${scheduleName}'`);
    }
    if (!_.isEmpty(workflowName)) {
      clauses.push(`workflowName='${workflowName}'`);
    }
    if (!_.isEmpty(executionId)) {
      clauses.push(`executionId='${executionId}'`);
    }
    if (!_.isEmpty(status)) {
      clauses.push(`state IN (${status.join(",")})`);
    }
    if (!_.isEmpty(lookback)) {
      clauses.push(`scheduledTime>${new Date().getTime() - lookback * MS_IN_DAY}`);
    }
    if (!_.isEmpty(startFrom)) {
      clauses.push(`scheduledTime>${new Date(startFrom).getTime()}`);
    }
    if (!_.isEmpty(startTo)) {
      clauses.push(`scheduledTime<${new Date(startTo).getTime()}`);
    }
    return {
      query: clauses.join(" AND "),
      freeText: "*",
    };
  }

  function doSearch() {
    setPage(1);
    const oldQuery = queryObj;
    const newQuery = buildQuery();
    setQueryObj(newQuery);
    if (_.isEqual(oldQuery, newQuery)) {
      refetch();
    }
  }

  function doReset() {
    setScheduleName("");
    setWorkflowName("");
    setExecutionId("");
    setStartFrom("");
    setStartTo("");
    setLookback("");
    setStatus([]);
    setPage(1);
    setQueryObj({ query: "", freeText: "*" });
  }

  const handlePage = (page) => setPage(page);
  const handleRowsPerPage = (rowsPerPage) => {
    setPage(1);
    setRowsPerPage(rowsPerPage);
  };

  const handleLookback = (val) => {
    setStartFrom("");
    setStartTo("");
    setLookback(val);
  };

  const handleStartFrom = (val) => {
    setLookback("");
    setStartFrom(val);
  };

  const handleStartTo = (val) => {
    setLookback("");
    setStartTo(val);
  };

  const results = _.get(resultObj, "results", []);
  const totalHits = _.get(resultObj, "totalHits", 0);

  const columns = [
    {
      name: "scheduleName",
      renderer: (val) => (
        <NavLink path={`/schedulerDef/${val}`}>{val}</NavLink>
      ),
      label: "Schedule Name",
      grow: 2,
    },
    {
      name: "executionId",
      label: "Execution ID",
    },
    {
      name: "scheduledTime",
      renderer: (val) => (val ? new Date(val).toLocaleString() : ""),
      label: "Scheduled Time",
    },
    {
      name: "executionTime",
      renderer: (val) => (val ? new Date(val).toLocaleString() : ""),
      label: "Execution Time",
    },
    {
      name: "state",
      label: "State",
    },
    {
      name: "workflowId",
      renderer: (val) =>
        val ? <NavLink path={`/execution/${val}`}>{val}</NavLink> : "",
      label: "Workflow ID",
      grow: 2,
    },
    {
      name: "reason",
      label: "Reason",
    },
  ];

  if (isSchedulerDisabled(error)) {
    return (
      <div className={clsx([classes.wrapper, classes.padded])}>
        <Heading level={3} gutterBottom>
          Scheduler Executions
        </Heading>
        <SchedulerDisabledBanner />
      </div>
    );
  }

  return (
    <div className={clsx([classes.wrapper, classes.padded])}>
      <Heading level={3} gutterBottom>
        Scheduler Executions
      </Heading>
      <Paper className={classes.paper}>
        <Grid container spacing={3} style={{ padding: 15 }}>
          <Grid item xs={5}>
            <Input
              fullWidth
              label="Schedule name"
              defaultValue={scheduleName}
              onBlur={setScheduleName}
              clearable
            />
          </Grid>
          <Grid item xs={4}>
            <Input
              fullWidth
              label="Workflow name"
              defaultValue={workflowName}
              onBlur={setWorkflowName}
              clearable
            />
          </Grid>
          <Grid item xs={3}>
            <Input
              fullWidth
              label="Scheduler execution id"
              defaultValue={executionId}
              onBlur={setExecutionId}
              clearable
            />
          </Grid>

          <Grid item xs={4}>
            <DateRangePicker
              disabled={!_.isEmpty(lookback)}
              label="Start time"
              from={startFrom}
              to={startTo}
              onFromChange={handleStartFrom}
              onToChange={handleStartTo}
            />
          </Grid>
          <Grid item xs={2}>
            <Input
              fullWidth
              label="Lookback (days)"
              defaultValue={lookback}
              onBlur={handleLookback}
              type="number"
              clearable
              disabled={!_.isEmpty(startFrom) || !_.isEmpty(startTo)}
            />
          </Grid>
          <Grid item xs={2}>
            <Dropdown
              label="Status"
              fullWidth
              options={executionStatuses}
              multiple
              onChange={(evt, val) => setStatus(val)}
              value={status}
            />
          </Grid>
          <Grid
            item
            xs={4}
            style={{
              display: "flex",
              alignItems: "flex-end",
              justifyContent: "flex-end",
              gap: 8,
            }}
          >
            <button className={classes.resetButton} onClick={doReset}>
              Reset
            </button>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <PrimaryButton onClick={doSearch}>Search</PrimaryButton>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>

      {resultObj && (
        <DataTable
          title={
            totalHits > 0 &&
            `Page ${page} of ${Math.ceil(totalHits / rowsPerPage)}`
          }
          localStorageKey="schedulerExecutionsTable"
          defaultShowColumns={[
            "scheduleName",
            "executionId",
            "scheduledTime",
            "executionTime",
            "state",
            "workflowId",
            "reason",
          ]}
          keyField="executionId"
          data={results}
          columns={columns}
          paginationServer
          paginationTotalRows={totalHits}
          paginationDefaultPage={page}
          paginationPerPage={rowsPerPage}
          onChangePage={handlePage}
          onChangeRowsPerPage={handleRowsPerPage}
        />
      )}
    </div>
  );
}
