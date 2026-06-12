import React, { useState } from "react";
import _ from "lodash";
import { FormControl, Grid, InputLabel } from "@material-ui/core";
import {
  Paper,
  Heading,
  PrimaryButton,
  Dropdown,
  Input,
  TaskNameInput,
  WorkflowNameInput,
} from "../../components";

import { TASK_STATUSES, SEARCH_TASK_TYPES_SET } from "../../utils/constants";
import { useQueryState } from "react-router-use-location-state";
import SearchTabs from "./SearchTabs";
import TaskResultsTable from "./TaskResultsTable";
import DateRangePicker from "../../components/DateRangePicker";
import { DEFAULT_ROWS_PER_PAGE } from "../../components/DataTable";
import { useTaskSearch } from "../../data/task";

import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import executionsStyles from "./executionsStyles";
import sharedStyles from "../styles";

const useStyles = makeStyles({
  ...executionsStyles,
  ...sharedStyles,
});

const DEFAULT_SORT = "startTime:DESC";
const MS_IN_DAY = 86400000;
const taskTypeOptions = Array.from(SEARCH_TASK_TYPES_SET.values());

export default function WorkflowPanel() {
  const classes = useStyles();

  const [freeText, setFreeText] = useQueryState("freeText", "");
  const [status, setStatus] = useQueryState("status", []);
  const [taskName, setTaskName] = useQueryState("taskName", []);
  const [taskId, setTaskId] = useQueryState("taskId", "");
  const [taskType, setTaskType] = useQueryState("taskType", []);
  const [startFrom, setStartFrom] = useQueryState("startFrom", "");
  const [startTo, setStartTo] = useQueryState("startTo", "");
  const [lookback, setLookback] = useQueryState("lookback", "");
  const [workflowType, setWorkflowType] = useQueryState("workflowType", []);

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
  } = useTaskSearch({
    page,
    rowsPerPage,
    sort,
    query: queryFT.query,
    freeText: queryFT.freeText,
  });

  function buildQuery() {
    const clauses = [];
    if (!_.isEmpty(taskName)) {
      clauses.push(`taskDefName IN (${taskName.join(",")})`);
    }
    if (!_.isEmpty(taskType)) {
      clauses.push(`taskType IN (${taskType.join(",")})`);
    }
    if (!_.isEmpty(taskId)) {
      clauses.push(`taskId="${taskId}"`);
    }
    if (!_.isEmpty(status)) {
      clauses.push(`status IN (${status.join(",")})`);
    }
    if (!_.isEmpty(lookback)) {
      clauses.push(`updateTime>${new Date().getTime() - lookback * MS_IN_DAY}`);
    }
    if (!_.isEmpty(startFrom)) {
      clauses.push(`updateTime>${new Date(startFrom).getTime()}`);
    }
    if (!_.isEmpty(startTo)) {
      clauses.push(`updateTime<${new Date(startTo).getTime()}`);
    }
    if (!_.isEmpty(workflowType)) {
      clauses.push(`workflowType IN (${workflowType.join(",")})`);
    }
    return {
      query: clauses.join(" AND "),
      freeText: _.isEmpty(freeText) ? "*" : freeText,
    };
  }

  function doSearch() {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    // Only force refetch if query didn't change. Else let react-query detect difference and refetch automatically
    if (_.isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
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
        <SearchTabs tabIndex={1} />
        <Grid container spacing={3} className={classes.controls}>
          <Grid item xs={3}>
            <TaskNameInput
              fullWidth
              onChange={(evt, val) => setTaskName(val)}
              value={taskName}
            />
          </Grid>
          <Grid item xs={3}>
            <Input
              fullWidth
              label="Task ID"
              defaultValue={taskId}
              onBlur={setTaskId}
              clearable
            />
          </Grid>
          <Grid item xs={3}>
            <Dropdown
              label="Task Status"
              fullWidth
              options={TASK_STATUSES}
              multiple
              onChange={(evt, val) => setStatus(val)}
              value={status}
            />
          </Grid>
          <Grid item xs={3}>
            <Dropdown
              label="Task Type (Leave blank to include SIMPLE Tasks)"
              fullWidth
              options={taskTypeOptions}
              multiple
              onChange={(evt, val) => setTaskType(val)}
              value={taskType}
            />
          </Grid>
          <Grid item xs={3}>
            <WorkflowNameInput
              fullWidth
              onChange={(evt, val) => setWorkflowType(val)}
              value={workflowType}
            />
          </Grid>
          <Grid item xs={4}>
            <DateRangePicker
              disabled={!_.isEmpty(lookback)}
              label="Update Time"
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

          <Grid item xs={3}>
            <Input
              fullWidth
              label="Lucene-syntax Query (Double-quote strings for Free Text)"
              defaultValue={freeText}
              onBlur={setFreeText}
              clearable
            />
          </Grid>
          <Grid item xs={1}>
            <FormControl>
              <InputLabel>&nbsp;</InputLabel>
              <PrimaryButton onClick={doSearch}>Search</PrimaryButton>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>
      <TaskResultsTable
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
