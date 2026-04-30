import React, { useState } from "react";
import _ from "lodash";
import { FormControl, Grid, InputLabel } from "@material-ui/core";
import {
  Paper,
  Heading,
  PrimaryButton,
  SecondaryButton,
  Dropdown,
  Input,
} from "../../components";

import { useQueryState } from "react-router-use-location-state";
import SearchTabs from "./SearchTabs";
import SchedulerExecutionResultsTable from "./SchedulerExecutionResultsTable";
import DateRangePicker from "../../components/DateRangePicker";
import { DEFAULT_ROWS_PER_PAGE } from "../../components/DataTable";
import { useSchedulerExecutionSearch, useSchedulerNames } from "../../data/scheduler";

import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import executionsStyles from "./executionsStyles";
import sharedStyles from "../styles";

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const SCHEDULER_STATES = ["POLLED", "EXECUTED", "FAILED"];
const DEFAULT_SORT = "scheduledTime:DESC";
const MS_IN_DAY = 86400000;

export default function SchedulerSearch() {
  const classes = useStyles();

  const schedulerNames = useSchedulerNames();

  const [state, setState] = useQueryState("state", []);
  const [scheduleName, setScheduleName] = useQueryState("scheduleName", []);
  const [workflowName, setWorkflowName] = useQueryState("workflowName", "");
  const [executionId, setExecutionId] = useQueryState("executionId", "");
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [lookback, setLookback] = useQueryState("lookback", "");

  const [page, setPage] = useQueryState("page", 1);
  const [rowsPerPage, setRowsPerPage] = useQueryState(
    "rowsPerPage",
    DEFAULT_ROWS_PER_PAGE
  );
  const [sort, setSort] = useQueryState("sort", DEFAULT_SORT);
  const [queryFT, setQueryFT] = useState(buildQuery);

  const {
    data: resultObj,
    error,
    isFetching,
    refetch,
  } = useSchedulerExecutionSearch({
    page,
    rowsPerPage,
    sort,
    query: queryFT.query,
    freeText: "*",
  });

  function buildQuery() {
    const clauses = [];
    if (!_.isEmpty(scheduleName)) {
      clauses.push(`scheduleName IN (${scheduleName.join(",")})`);
    }
    if (!_.isEmpty(state)) {
      clauses.push(`state IN (${state.join(",")})`);
    }
    if (!_.isEmpty(workflowName)) {
      clauses.push(`workflowName=${workflowName}`);
    }
    if (!_.isEmpty(executionId)) {
      clauses.push(`executionId=${executionId}`);
    }
    if (!_.isEmpty(lookback)) {
      clauses.push(
        `scheduledTime>${new Date().getTime() - lookback * MS_IN_DAY}`
      );
    }
    if (!_.isEmpty(startFrom)) {
      clauses.push(`scheduledTime>${new Date(startFrom).getTime()}`);
    }
    if (!_.isEmpty(startTo)) {
      clauses.push(`scheduledTime<${new Date(startTo).getTime()}`);
    }

    return {
      query: clauses.join(" AND "),
    };
  }

  function doSearch() {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    if (_.isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
  }

  function doReset() {
    setScheduleName([]);
    setWorkflowName("");
    setExecutionId("");
    setState([]);
    setStartFrom("");
    setStartTo("");
    setLookback("");
    setPage(1);
    setSort(DEFAULT_SORT);
    setQueryFT({ query: "" });
  }

  const handlePage = (page) => {
    setPage(page);
  };

  const handleSort = (changedColumn, direction) => {
    const sort = `${changedColumn}:${direction.toUpperCase()}`;
    setPage(1);
    setSort(sort);
  };

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

  return (
    <div className={clsx([classes.wrapper, classes.padded])}>
      <Heading level={3} className={classes.heading}>
        Search Executions
      </Heading>
      <Paper className={classes.paper}>
        <SearchTabs tabIndex={2} />
        <Grid container spacing={3} className={classes.controls}>
          <Grid item xs={5}>
            <Dropdown
              label="Schedule Name"
              fullWidth
              options={schedulerNames}
              multiple
              onChange={(evt, val) => setScheduleName(val)}
              value={scheduleName}
            />
          </Grid>
          <Grid item xs={4}>
            <Input
              fullWidth
              label="Workflow Name"
              defaultValue={workflowName}
              onBlur={setWorkflowName}
              clearable
            />
          </Grid>
          <Grid item xs={3}>
            <Input
              fullWidth
              label="Scheduler Execution ID"
              defaultValue={executionId}
              onBlur={setExecutionId}
              clearable
            />
          </Grid>

          <Grid item xs={4}>
            <DateRangePicker
              disabled={!_.isEmpty(lookback)}
              label="Start Time"
              from={startFrom}
              to={startTo}
              onFromChange={handleStartFrom}
              onToChange={handleStartTo}
            />
          </Grid>
          <Grid item xs={1}>
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
              options={SCHEDULER_STATES}
              multiple
              onChange={(evt, val) => setState(val)}
              value={state}
            />
          </Grid>
          <Grid item xs={3} />
          <Grid item xs={1}>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <SecondaryButton onClick={doReset}>Reset</SecondaryButton>
            </FormControl>
          </Grid>
          <Grid item xs={1}>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <PrimaryButton onClick={doSearch}>Search</PrimaryButton>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>
      <SchedulerExecutionResultsTable
        resultObj={resultObj}
        error={error}
        busy={isFetching}
        page={page}
        rowsPerPage={rowsPerPage}
        sort={sort}
        setPage={handlePage}
        setRowsPerPage={handleRowsPerPage}
        setSort={handleSort}
      />
    </div>
  );
}
